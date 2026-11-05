"""Motor de inferencia de radiografías panorámicas para CORONYX (Despliegue FastAPI / Railway).

Carga una sola vez el modelo entrenado (`model/best.pt`) y ejecuta detección de:
- 0: caries
- 1: caries_profunda

Funciona automáticamente con GPU (CUDA) si está disponible (ej. laptop local RTX 5060)
o con CPU en contenedores de nube (ej. Railway).
"""

from datetime import datetime, timezone
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import time
import uuid

from PIL import Image, ImageDraw

BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = BASE_DIR / "model" / "model_config.json"
DEFAULT_WEIGHTS_PATH = BASE_DIR / "model" / "best.pt"

SCHEMA_VERSION = "coronyx.ml.panoramic_caries.v1"
CLASSES = {0: "caries", 1: "caries_profunda"}
ALLOWED_FORMATS = {"PNG", "JPEG"}
MAX_FILE_BYTES = 25 * 1024 * 1024
MIN_DIMENSION = 256
MAX_DIMENSION = 8192


class PanoramicDetectorSession:
    """Mantiene en memoria el modelo YOLOv8n entrenado para responder sin recargar pesos en cada petición."""

    def __init__(self, weights_path=None, threshold=None, imgsz=640, nms_iou=0.70):
        config = {}
        if CONFIG_PATH.is_file():
            config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

        if weights_path:
            resolved_weights = Path(weights_path)
        else:
            rel_path = config.get("weights_path", "model/best.pt")
            candidate = Path(rel_path)
            resolved_weights = candidate if candidate.is_absolute() else (BASE_DIR / candidate)
            if not resolved_weights.is_file():
                resolved_weights = DEFAULT_WEIGHTS_PATH

        self.weights_path = resolved_weights.resolve()
        self.model_name = config.get("model_name", "coronyx-dentex-yolov8n")
        self.model_version = config.get("model_version", "1.0.0-mvp")
        self.operating_threshold = float(
            threshold
            if threshold is not None
            else config.get("operating_confidence_threshold", 0.25)
        )
        self.imgsz = int(imgsz or config.get("preprocessing", {}).get("imgsz", 640))
        self.nms_iou = float(nms_iou or config.get("nms_parameters", {}).get("iou_threshold", 0.70))
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
        from ultralytics import YOLO  # noqa: PLC0415  # pyrefly: ignore[missing-import]

        self.model = YOLO(str(self.weights_path))
        self.cold_start_ms = round((time.perf_counter() - t0) * 1000.0, 2)

    def analyze_bytes(self, raw_bytes: bytes, file_name: str = "radiografia.png", custom_threshold: float | None = None):
        request_id = str(uuid.uuid4())
        analyzed_at = datetime.now(timezone.utc).isoformat()
        threshold = float(custom_threshold if custom_threshold is not None else self.operating_threshold)

        # 1. Verificar disponibilidad del modelo
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

        # 2. Validar tamaño y formato de imagen PNG/JPEG
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

        # 3. Ejecutar inferencia (GPU si está disponible, CPU en Railway)
        try:
            import torch  # noqa: PLC0415  # pyrefly: ignore[missing-import]

            use_cuda = torch.cuda.is_available()
            if use_cuda:
                torch.cuda.synchronize()
            results = self.model.predict(
                source=rgb_image,
                imgsz=self.imgsz,
                conf=threshold,
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
                "operating_threshold": threshold,
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


def render_annotated_png_bytes(raw_bytes: bytes, response_payload: dict) -> bytes:
    """Dibuja las cajas predichas en memoria y devuelve los bytes PNG."""
    with Image.open(BytesIO(raw_bytes)) as img:
        canvas = img.convert("RGB")
    draw = ImageDraw.Draw(canvas)
    colors = {0: (255, 190, 0), 1: (239, 68, 68)}
    for item in response_payload.get("findings", []):
        b = item["bbox_pixels"]
        color = colors.get(item["class_id"], (0, 200, 255))
        coords = [b["xMin"], b["yMin"], b["xMax"], b["yMax"]]
        draw.rectangle(coords, outline=color, width=5)
        label = f"{item['class_name']} ({item['score']:.2f})"
        text_y = max(0.0, b["yMin"] - 20)
        draw.rectangle(
            [b["xMin"], text_y, b["xMin"] + len(label) * 7 + 8, text_y + 18],
            fill=(20, 20, 20),
        )
        draw.text((b["xMin"] + 4, text_y + 2), label, fill=color)
    out = BytesIO()
    canvas.save(out, format="PNG")
    return out.getvalue()

