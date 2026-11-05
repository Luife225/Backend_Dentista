"""Fase G: Inferencia local independiente y medición de latencia sobre radiografías panorámicas.

Implementa:
- Carga única del modelo mediante caché de instancia para evitar recargas por imagen.
- Validación de archivo de entrada (PNG/JPEG), dimensiones y decodificación íntegra.
- Respuesta estructurada con esquema versionado (coronyx.ml.panoramic_caries.v1).
- Diferenciación explícita entre:
  * analysis_completed_with_findings
  * analysis_completed_no_findings
  * invalid_image_error
  * model_unavailable_error
  * inference_runtime_error
- Protocolo de latencia (Sección 23): arranque en frío por separado, 5 calentamientos y 30 imágenes de validación.
"""

import argparse
from datetime import datetime, timezone
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import statistics
import time
import uuid

from PIL import Image, ImageDraw

from .adquirir_dentex import LAB
from .evaluar_modelo import CLASSES, FROZEN_MODEL_JSON, default_best_weights
from .preparar_dataset import OUTPUT as DATASET_DIR

SCHEMA_VERSION = "coronyx.ml.panoramic_caries.v1"
ALLOWED_FORMATS = {"PNG", "JPEG"}
MAX_FILE_BYTES = 25 * 1024 * 1024
MIN_DIMENSION = 256
MAX_DIMENSION = 8192
LATENCY_REPORT_PATH = LAB / "auditoria" / "LATENCIA_INFERENCIA_V1.json"


class PanoramicDetectorSession:
    """Mantiene cargado el modelo entrenado y sus metadatos congelados."""

    def __init__(self, weights_path=None, threshold=None, imgsz=640, nms_iou=0.70):
        frozen = {}
        if FROZEN_MODEL_JSON.is_file():
            frozen = json.loads(FROZEN_MODEL_JSON.read_text(encoding="utf-8"))

        if weights_path:
            resolved_weights = Path(weights_path)
        else:
            raw_cfg_path = frozen.get("weights_path")
            if raw_cfg_path:
                candidate = Path(raw_cfg_path)
                if not candidate.is_absolute():
                    candidate = (LAB / candidate).resolve()
                resolved_weights = candidate if candidate.is_file() else default_best_weights()
            else:
                resolved_weights = default_best_weights()
        self.weights_path = resolved_weights.resolve()
        self.model_name = frozen.get("model_name", "coronyx-dentex-yolov8n")
        self.model_version = frozen.get("model_version", "1.0.0-mvp")
        self.operating_threshold = float(
            threshold
            if threshold is not None
            else frozen.get("operating_confidence_threshold", 0.25)
        )
        self.imgsz = int(imgsz or frozen.get("preprocessing", {}).get("imgsz", 640))
        self.nms_iou = float(nms_iou or frozen.get("nms_parameters", {}).get("iou_threshold", 0.70))
        self.weights_sha256 = None
        self.model = None
        self.cold_start_ms = None

    def load(self):
        if self.model is not None:
            return
        if not self.weights_path.is_file():
            raise FileNotFoundError(
                f"No se encontraron los pesos del modelo en: {self.weights_path}"
            )
        t0 = time.perf_counter()
        self.weights_sha256 = sha256(self.weights_path.read_bytes()).hexdigest()
        from ultralytics import YOLO  # noqa: PLC0415

        self.model = YOLO(str(self.weights_path))
        self.cold_start_ms = round((time.perf_counter() - t0) * 1000.0, 2)

    def analyze_bytes(self, raw_bytes, file_name="radiografia.png"):
        request_id = str(uuid.uuid4())
        analyzed_at = datetime.now(timezone.utc).isoformat()

        # 1. Comprobar que el modelo esté disponible
        try:
            self.load()
        except Exception as err:
            return {
                "schema_version": SCHEMA_VERSION,
                "request_id": request_id,
                "analyzed_at_utc": analyzed_at,
                "status": "model_unavailable_error",
                "error": {
                    "code": "MODEL_WEIGHTS_MISSING_OR_INVALID",
                    "message": str(err),
                },
                "model": {
                    "name": self.model_name,
                    "version": self.model_version,
                    "weights_sha256": None,
                },
                "image": None,
                "findings": [],
            }

        # 2. Validar tamaño y decodificación de imagen PNG/JPEG
        if not raw_bytes or len(raw_bytes) > MAX_FILE_BYTES:
            return {
                "schema_version": SCHEMA_VERSION,
                "request_id": request_id,
                "analyzed_at_utc": analyzed_at,
                "status": "invalid_image_error",
                "error": {
                    "code": "INVALID_FILE_SIZE",
                    "message": f"El archivo está vacío o supera el límite de {MAX_FILE_BYTES} bytes.",
                },
                "model": {
                    "name": self.model_name,
                    "version": self.model_version,
                    "weights_sha256": self.weights_sha256,
                },
                "image": None,
                "findings": [],
            }

        t_total_start = time.perf_counter()
        try:
            with Image.open(BytesIO(raw_bytes)) as img_check:
                fmt = img_check.format
                width, height = img_check.size
                img_check.verify()
            if fmt not in ALLOWED_FORMATS:
                raise ValueError(f"Formato {fmt} no permitido; se requiere PNG o JPEG.")
            if not (MIN_DIMENSION <= width <= MAX_DIMENSION and MIN_DIMENSION <= height <= MAX_DIMENSION):
                raise ValueError(
                    f"Dimensiones ({width}x{height}) fuera del rango admitido ({MIN_DIMENSION}-{MAX_DIMENSION})."
                )
            with Image.open(BytesIO(raw_bytes)) as img:
                rgb_image = img.convert("RGB")
        except Exception as err:
            return {
                "schema_version": SCHEMA_VERSION,
                "request_id": request_id,
                "analyzed_at_utc": analyzed_at,
                "status": "invalid_image_error",
                "error": {
                    "code": "IMAGE_DECODE_OR_FORMAT_ERROR",
                    "message": str(err),
                },
                "model": {
                    "name": self.model_name,
                    "version": self.model_version,
                    "weights_sha256": self.weights_sha256,
                },
                "image": None,
                "findings": [],
            }

        image_sha256 = sha256(raw_bytes).hexdigest()

        # 3. Ejecutar inferencia
        try:
            import torch  # noqa: PLC0415

            use_cuda = torch.cuda.is_available()
            if use_cuda:
                torch.cuda.synchronize()
            results = self.model.predict(
                source=rgb_image,
                imgsz=self.imgsz,
                conf=self.operating_threshold,
                iou=self.nms_iou,
                agnostic_nms=False,
                max_det=300,
                device=0 if use_cuda else "cpu",
                verbose=False,
            )
            if use_cuda:
                torch.cuda.synchronize()
        except Exception as err:
            return {
                "schema_version": SCHEMA_VERSION,
                "request_id": request_id,
                "analyzed_at_utc": analyzed_at,
                "status": "inference_runtime_error",
                "error": {
                    "code": "INFERENCE_EXECUTION_FAILED",
                    "message": str(err),
                },
                "model": {
                    "name": self.model_name,
                    "version": self.model_version,
                    "weights_sha256": self.weights_sha256,
                },
                "image": {
                    "file_name": file_name,
                    "sha256": image_sha256,
                    "width": width,
                    "height": height,
                },
                "findings": [],
            }

        result = results[0]
        speed = result.speed or {}
        findings = []
        boxes = result.boxes
        if boxes is not None and len(boxes) > 0:
            xyxy_list = boxes.xyxy.cpu().tolist()
            conf_list = boxes.conf.cpu().tolist()
            cls_list = [int(c) for c in boxes.cls.cpu().tolist()]
            # Ordenar de mayor a menor confianza
            ordered = sorted(
                zip(xyxy_list, conf_list, cls_list), key=lambda item: -float(item[1])
            )
            for idx, (bbox, score, cls_id) in enumerate(ordered, 1):
                x_min = max(0.0, min(float(width), float(bbox[0])))
                y_min = max(0.0, min(float(height), float(bbox[1])))
                x_max = max(0.0, min(float(width), float(bbox[2])))
                y_max = max(0.0, min(float(height), float(bbox[3])))
                findings.append(
                    {
                        "finding_id": f"hallazgo-{idx}",
                        "class_id": cls_id,
                        "class_name": CLASSES.get(cls_id, str(cls_id)),
                        "score": round(float(score), 4),
                        "bbox_pixels": {
                            "xMin": round(x_min, 2),
                            "yMin": round(y_min, 2),
                            "xMax": round(x_max, 2),
                            "yMax": round(y_max, 2),
                        },
                        "review_status": "PENDIENTE",
                    }
                )

        total_ms = round((time.perf_counter() - t_total_start) * 1000.0, 2)
        status = (
            "analysis_completed_with_findings"
            if findings
            else "analysis_completed_no_findings"
        )
        return {
            "schema_version": SCHEMA_VERSION,
            "request_id": request_id,
            "analyzed_at_utc": analyzed_at,
            "status": status,
            "error": None,
            "model": {
                "name": self.model_name,
                "version": self.model_version,
                "weights_sha256": self.weights_sha256,
                "operating_threshold": self.operating_threshold,
                "nms_iou": self.nms_iou,
                "input_imgsz": self.imgsz,
            },
            "image": {
                "file_name": file_name,
                "sha256": image_sha256,
                "width": width,
                "height": height,
                "format": fmt,
            },
            "findings_count": len(findings),
            "findings": findings,
            "timing_ms": {
                "preprocess_ms": round(float(speed.get("preprocess", 0.0)), 2),
                "inference_ms": round(float(speed.get("inference", 0.0)), 2),
                "postprocess_ms": round(float(speed.get("postprocess", 0.0)), 2),
                "total_wall_ms": total_ms,
            },
            "clinical_disclaimer": (
                "Sugerencias automatizadas pendientes de revisión odontológica. "
                "Las cajas delimitan dientes con posible condición; no representan segmentación "
                "de lesiones ni numeración dental, y la ausencia de hallazgos no equivale a paciente sano."
            ),
        }


def save_annotated_preview(image_path, response_payload, output_path):
    """Dibuja las cajas predichas respetando exactamente el tamaño y relación de aspecto originales."""
    with Image.open(image_path) as img:
        canvas = img.convert("RGB")
    draw = ImageDraw.Draw(canvas)
    colors = {0: (255, 180, 0), 1: (239, 68, 68)}
    for item in response_payload.get("findings", []):
        b = item["bbox_pixels"]
        color = colors.get(item["class_id"], (0, 200, 255))
        coords = [b["xMin"], b["yMin"], b["xMax"], b["yMax"]]
        draw.rectangle(coords, outline=color, width=4)
        label = f"{item['class_name']} ({item['score']:.2f})"
        draw.text((b["xMin"] + 4, max(0.0, b["yMin"] - 18)), label, fill=color)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output_path, format="PNG")
    return output_path


def measure_latency_protocol(session):
    """Protocolo de latencia (Sección 23):
    - Carga inicial medida por separado.
    - 5 calentamientos.
    - 30 imágenes distintas de validación (1 por solicitud).
    """
    session.load()
    val_images = sorted((DATASET_DIR / "images" / "validation").glob("*.png"))
    if not val_images:
        raise RuntimeError("No hay imágenes de validación para medir latencia")

    warmups = val_images[:5]
    for img_path in warmups:
        session.analyze_bytes(img_path.read_bytes(), file_name=img_path.name)

    target_images = val_images[:30]
    records = []
    for img_path in target_images:
        res = session.analyze_bytes(img_path.read_bytes(), file_name=img_path.name)
        records.append(res["timing_ms"])

    def stats_for(key):
        values = sorted(r[key] for r in records)
        p50 = statistics.median(values)
        p95_idx = min(len(values) - 1, max(0, int(round(0.95 * (len(values) - 1)))))
        return {
            "p50_ms": round(p50, 2),
            "p95_ms": round(values[p95_idx], 2),
            "min_ms": round(values[0], 2),
            "max_ms": round(values[-1], 2),
        }

    import torch  # noqa: PLC0415

    report = {
        "measured_at_utc": datetime.now(timezone.utc).isoformat(),
        "source_partition": "validation",
        "warmups_count": len(warmups),
        "images_measured": len(target_images),
        "batch_size_per_request": 1,
        "cold_start_load_ms": session.cold_start_ms,
        "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
        "input_imgsz": session.imgsz,
        "stages": {
            "preprocess_ms": stats_for("preprocess_ms"),
            "inference_ms": stats_for("inference_ms"),
            "postprocess_ms": stats_for("postprocess_ms"),
            "total_wall_ms": stats_for("total_wall_ms"),
        },
    }
    LATENCY_REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    LATENCY_REPORT_PATH.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--imagen", type=str, default=None, help="Ruta a una radiografía PNG o JPEG.")
    parser.add_argument("--pesos", type=str, default=None, help="Ruta opcional a best.pt.")
    parser.add_argument("--umbral", type=float, default=None, help="Umbral opcional de confianza.")
    parser.add_argument(
        "--guardar-vista",
        type=str,
        default=None,
        help="Ruta opcional donde guardar una imagen PNG con las cajas dibujadas.",
    )
    parser.add_argument(
        "--medir-latencia",
        action="store_true",
        help="Ejecuta el protocolo de latencia (5 calentamientos + 30 imágenes de validación).",
    )
    args = parser.parse_args()

    session = PanoramicDetectorSession(weights_path=args.pesos, threshold=args.umbral)

    if args.medir_latencia:
        report = measure_latency_protocol(session)
        print(json.dumps(report, ensure_ascii=True, indent=2))
        return

    if args.imagen:
        img_path = Path(args.imagen)
        raw = img_path.read_bytes() if img_path.is_file() else b""
        payload = session.analyze_bytes(raw, file_name=img_path.name)
        if args.guardar_vista and payload["status"].startswith("analysis_completed"):
            out = save_annotated_preview(img_path, payload, args.guardar_vista)
            payload["annotated_preview_saved_to"] = str(out)
        print(json.dumps(payload, ensure_ascii=True, indent=2))
        return

    parser.error("Indique --imagen <ruta> o --medir-latencia")


if __name__ == "__main__":
    main()
