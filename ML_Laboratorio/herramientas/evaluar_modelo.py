"""Fase E y Fase F: Evaluación reproducible en validación, selección de umbral y prueba interna final.

Implementa:
- Emparejamiento uno a uno por imagen y categoría a IoU >= umbral.
- Cálculo de TP, FP, FN, Precisión, Recall, F1, AP50 y AP50-95 por clase y media no ponderada (macro).
- Selección determinista del umbral operativo en validación (máximo macro F1 a IoU 0.50;
  desempate por mayor macro recall y luego menor umbral).
- Congelación del modelo y evaluación única sobre la prueba interna de 127 imágenes.
"""

import argparse
from collections import defaultdict
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path

from .adquirir_dentex import LAB, IntegrityError
from .entrenar_referencia import CONFIG_PATH, RUNS_DIR, current_commit, file_sha256
from .preparar_dataset import OUTPUT as DATASET_DIR, PARTITIONS_PATH, POLICY_PATH
from .verificar_entorno_gpu import verify_gpu_environment

AUDITORIA_DIR = LAB / "auditoria"
VALIDATION_REPORT_JSON = AUDITORIA_DIR / "VALIDACION_SELECCION_V1.json"
FROZEN_MODEL_JSON = AUDITORIA_DIR / "MODELO_CONGELADO_V1.json"
FINAL_TEST_REPORT_JSON = AUDITORIA_DIR / "EVALUACION_INTERNA_FINAL_V1.json"
FINAL_REPORT_MD = AUDITORIA_DIR / "INFORME_EVALUACION_V1.md"

CLASSES = {0: "caries", 1: "caries_profunda"}
IOU_THRESHOLDS = [round(0.50 + 0.05 * i, 2) for i in range(10)]


def box_iou_xyxy(box_a, box_b):
    """Calcula IoU entre dos cajas [xMin, yMin, xMax, yMax] en píxeles originales."""
    x_left = max(box_a[0], box_b[0])
    y_top = max(box_a[1], box_b[1])
    x_right = min(box_a[2], box_b[2])
    y_bottom = min(box_a[3], box_b[3])
    inter_w = max(0.0, x_right - x_left)
    inter_h = max(0.0, y_bottom - y_top)
    inter_area = inter_w * inter_h
    if inter_area <= 0.0:
        return 0.0
    area_a = max(0.0, box_a[2] - box_a[0]) * max(0.0, box_a[3] - box_a[1])
    area_b = max(0.0, box_b[2] - box_b[0]) * max(0.0, box_b[3] - box_b[1])
    union = area_a + area_b - inter_area
    return inter_area / union if union > 0.0 else 0.0


def match_predictions_one_to_one(gt_boxes, pred_boxes, iou_threshold=0.50):
    """Empareja predicciones (ordenadas por score descendente) uno a uno con referencias de la misma clase.

    Cada elemento de gt_boxes: {"id": int, "bbox_xyxy": [x1, y1, x2, y2]}
    Cada elemento de pred_boxes: {"score": float, "bbox_xyxy": [x1, y1, x2, y2], ...}
    Devuelve:
      tp_flags: lista de bool (misma longitud que pred_boxes ordenadas)
      matched_gt_ids: conjunto de ids de gt_boxes emparejados
      matches: lista de dicts con detalle de emparejamiento
    """
    sorted_preds = sorted(
        enumerate(pred_boxes),
        key=lambda item: (-float(item[1]["score"]), item[0]),
    )
    matched_gt_indices = set()
    tp_flags = []
    matches = []

    for _, pred in sorted_preds:
        best_iou = 0.0
        best_gt_idx = None
        for gt_idx, gt in enumerate(gt_boxes):
            if gt_idx in matched_gt_indices:
                continue
            iou = box_iou_xyxy(pred["bbox_xyxy"], gt["bbox_xyxy"])
            if iou >= iou_threshold and iou > best_iou:
                best_iou = iou
                best_gt_idx = gt_idx
        if best_gt_idx is not None:
            matched_gt_indices.add(best_gt_idx)
            tp_flags.append(True)
            matches.append(
                {
                    "type": "TP",
                    "score": float(pred["score"]),
                    "iou": round(best_iou, 6),
                    "gt_id": gt_boxes[best_gt_idx].get("id"),
                    "pred_bbox": pred["bbox_xyxy"],
                    "gt_bbox": gt_boxes[best_gt_idx]["bbox_xyxy"],
                }
            )
        else:
            tp_flags.append(False)
            matches.append(
                {
                    "type": "FP",
                    "score": float(pred["score"]),
                    "iou": round(best_iou, 6),
                    "gt_id": None,
                    "pred_bbox": pred["bbox_xyxy"],
                    "gt_bbox": None,
                }
            )

    matched_gt_ids = {gt_boxes[idx].get("id", idx) for idx in matched_gt_indices}
    return tp_flags, matched_gt_ids, matches


def compute_ap_from_pr(precisions, recalls):
    """Calcula Average Precision (interpolación de 101 puntos estilo COCO/Ultralytics)."""
    if not precisions or not recalls:
        return 0.0
    mrec = [0.0] + list(recalls) + [1.0]
    mpre = [1.0] + list(precisions) + [0.0]
    for i in range(len(mpre) - 2, -1, -1):
        if mpre[i] < mpre[i + 1]:
            mpre[i] = mpre[i + 1]
    ap = 0.0
    for r_thresh in [i / 100.0 for i in range(101)]:
        # Mayor precisión donde recall >= r_thresh
        p_vals = [mpre[j] for j in range(len(mrec)) if mrec[j] >= r_thresh]
        ap += max(p_vals) if p_vals else 0.0
    return ap / 101.0


def compute_class_ap_at_iou(gt_by_image, preds_by_image, class_id, iou_threshold):
    """Calcula AP y curva PR para una clase en todo el conjunto a un umbral IoU dado."""
    total_gt = sum(len(gt_by_image[img].get(class_id, [])) for img in gt_by_image)
    if total_gt == 0:
        return None, []

    all_records = []
    for image_name in sorted(gt_by_image):
        gts = gt_by_image[image_name].get(class_id, [])
        preds = preds_by_image.get(image_name, {}).get(class_id, [])
        _, _, matches = match_predictions_one_to_one(gts, preds, iou_threshold=iou_threshold)
        for m in matches:
            all_records.append((m["score"], 1 if m["type"] == "TP" else 0, image_name))

    all_records.sort(key=lambda row: (-row[0], row[2]))
    tp_cum = 0
    fp_cum = 0
    precisions = []
    recalls = []
    pr_curve_sample = []

    for idx, (score, is_tp, _) in enumerate(all_records, 1):
        if is_tp:
            tp_cum += 1
        else:
            fp_cum += 1
        prec = tp_cum / (tp_cum + fp_cum)
        rec = tp_cum / total_gt
        precisions.append(prec)
        recalls.append(rec)
        if idx == 1 or idx == len(all_records) or idx % max(1, len(all_records) // 25) == 0:
            pr_curve_sample.append(
                {
                    "score": round(score, 4),
                    "precision": round(prec, 6),
                    "recall": round(rec, 6),
                }
            )

    return round(compute_ap_from_pr(precisions, recalls), 6), pr_curve_sample


def evaluate_at_threshold(gt_by_image, preds_by_image, conf_threshold, iou_threshold=0.50):
    """Evalúa TP, FP, FN, Precisión, Recall y F1 por clase y macro en un umbral de confianza."""
    per_class = {}
    for class_id, class_name in CLASSES.items():
        total_gt = 0
        images_with_gt = 0
        tp = 0
        fp = 0
        fn = 0
        fp_examples = []
        fn_examples = []
        cross_class_confusions = []

        other_class = 1 - class_id
        for image_name in sorted(gt_by_image):
            gts = gt_by_image[image_name].get(class_id, [])
            other_gts = gt_by_image[image_name].get(other_class, [])
            if gts:
                images_with_gt += 1
                total_gt += len(gts)

            filtered_preds = [
                p
                for p in preds_by_image.get(image_name, {}).get(class_id, [])
                if float(p["score"]) >= conf_threshold
            ]
            tp_flags, matched_gt_ids, matches = match_predictions_one_to_one(
                gts, filtered_preds, iou_threshold=iou_threshold
            )
            img_tp = sum(1 for flag in tp_flags if flag)
            img_fp = sum(1 for flag in tp_flags if not flag)
            img_fn = len(gts) - img_tp
            tp += img_tp
            fp += img_fp
            fn += img_fn

            for m in matches:
                if m["type"] == "FP":
                    # Comprobar si coincide espacialmente con una referencia de la otra categoría
                    confused_with_other = any(
                        box_iou_xyxy(m["pred_bbox"], ogt["bbox_xyxy"]) >= iou_threshold
                        for ogt in other_gts
                    )
                    entry = {
                        "image": image_name,
                        "class_predicted": class_name,
                        "score": round(m["score"], 4),
                        "bbox_xyxy": [round(v, 2) for v in m["pred_bbox"]],
                        "confused_with_other_target_class": confused_with_other,
                    }
                    fp_examples.append(entry)
                    if confused_with_other:
                        cross_class_confusions.append(entry)

            for gt in gts:
                if gt["id"] not in matched_gt_ids:
                    fn_examples.append(
                        {
                            "image": image_name,
                            "class_Missed": class_name,
                            "annotation_id": gt["id"],
                            "bbox_xyxy": [round(v, 2) for v in gt["bbox_xyxy"]],
                        }
                    )

        if total_gt == 0:
            per_class[class_name] = {
                "class_id": class_id,
                "evaluable": False,
                "images_with_reference": 0,
                "reference_annotations": 0,
                "TP": 0,
                "FP": fp,
                "FN": 0,
                "precision": None,
                "recall": None,
                "F1": None,
            }
            continue

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) > 0 else 0.0
        fp_examples.sort(key=lambda item: -item["score"])

        per_class[class_name] = {
            "class_id": class_id,
            "evaluable": True,
            "images_with_reference": images_with_gt,
            "reference_annotations": total_gt,
            "TP": tp,
            "FP": fp,
            "FN": fn,
            "precision": round(precision, 6),
            "recall": round(recall, 6),
            "F1": round(f1, 6),
            "top_false_positives": fp_examples[:10],
            "sample_false_negatives": fn_examples[:10],
            "cross_class_confusions": cross_class_confusions[:10],
        }

    evaluable_classes = [c for c in per_class.values() if c["evaluable"]]
    macro_precision = (
        round(sum(c["precision"] for c in evaluable_classes) / len(evaluable_classes), 6)
        if len(evaluable_classes) == len(CLASSES)
        else None
    )
    macro_recall = (
        round(sum(c["recall"] for c in evaluable_classes) / len(evaluable_classes), 6)
        if len(evaluable_classes) == len(CLASSES)
        else None
    )
    macro_f1 = (
        round(sum(c["F1"] for c in evaluable_classes) / len(evaluable_classes), 6)
        if len(evaluable_classes) == len(CLASSES)
        else None
    )

    return {
        "confidence_threshold": round(conf_threshold, 4),
        "iou_threshold": iou_threshold,
        "macro_precision": macro_precision,
        "macro_recall": macro_recall,
        "macro_F1": macro_f1,
        "per_class": per_class,
    }


def select_operating_threshold(gt_by_image, preds_by_image):
    """Elige el umbral global en validación según PLAN_EVALUACION_2026-10-25.md:
    Mayor macro F1 a IoU 0.50; en empate, mayor macro recall y luego menor umbral.
    """
    candidates = [round(0.05 + 0.01 * i, 2) for i in range(91)]
    best_eval = None
    best_key = None
    grid_summary = []

    for threshold in candidates:
        res = evaluate_at_threshold(gt_by_image, preds_by_image, threshold, iou_threshold=0.50)
        if res["macro_F1"] is None:
            continue
        key = (res["macro_F1"], res["macro_recall"], -threshold)
        grid_summary.append(
            {
                "threshold": threshold,
                "macro_F1": res["macro_F1"],
                "macro_recall": res["macro_recall"],
                "macro_precision": res["macro_precision"],
            }
        )
        if best_key is None or key > best_key:
            best_key = key
            best_eval = res

    if best_eval is None:
        raise IntegrityError("No fue posible seleccionar un umbral equilibrado en validación")
    return best_eval, grid_summary


def load_partition_ground_truth(partition):
    coco_path = DATASET_DIR / "annotations" / f"{partition}.json"
    if not coco_path.is_file():
        raise IntegrityError(f"No existe el archivo de anotaciones de {partition}: {coco_path}")
    coco = json.loads(coco_path.read_text(encoding="utf-8"))
    id_to_name = {img["id"]: img["file_name"] for img in coco["images"]}
    gt_by_image = {img["file_name"]: {0: [], 1: []} for img in coco["images"]}
    for ann in coco["annotations"]:
        image_name = id_to_name[ann["image_id"]]
        x, y, w, h = ann["bbox"]
        gt_by_image[image_name][ann["category_id"]].append(
            {
                "id": ann["id"],
                "bbox_xyxy": [float(x), float(y), float(x + w), float(y + h)],
            }
        )
    return gt_by_image, len(coco["images"])


def generate_raw_predictions(weights_path, partition, imgsz=640, nms_iou=0.70, min_conf=0.001):
    from ultralytics import YOLO  # noqa: PLC0415

    model = YOLO(str(weights_path))
    images_dir = DATASET_DIR / "images" / partition
    image_paths = sorted(images_dir.glob("*.png"))
    if not image_paths:
        raise IntegrityError(f"No hay imágenes preparadas en {images_dir}")

    preds_by_image = {}
    serialized_predictions = []

    for img_path in image_paths:
        results = model.predict(
            source=str(img_path),
            imgsz=imgsz,
            conf=min_conf,
            iou=nms_iou,
            agnostic_nms=False,
            max_det=300,
            device=0,
            verbose=False,
        )
        result = results[0]
        by_class = {0: [], 1: []}
        boxes = result.boxes
        if boxes is not None and len(boxes) > 0:
            xyxy_list = boxes.xyxy.cpu().tolist()
            conf_list = boxes.conf.cpu().tolist()
            cls_list = [int(c) for c in boxes.cls.cpu().tolist()]
            for bbox, score, cls_id in zip(xyxy_list, conf_list, cls_list):
                item = {
                    "score": float(score),
                    "bbox_xyxy": [float(v) for v in bbox],
                }
                if cls_id in by_class:
                    by_class[cls_id].append(item)
                serialized_predictions.append(
                    {
                        "image": img_path.name,
                        "class_id": cls_id,
                        "class_name": CLASSES.get(cls_id, str(cls_id)),
                        "score": round(float(score), 6),
                        "bbox_xyxy": [round(float(v), 2) for v in bbox],
                    }
                )
        preds_by_image[img_path.name] = by_class

    return preds_by_image, serialized_predictions


def full_partition_metrics(gt_by_image, preds_by_image, operating_threshold):
    operating = evaluate_at_threshold(
        gt_by_image, preds_by_image, operating_threshold, iou_threshold=0.50
    )
    ap_per_class = {}
    for class_id, class_name in CLASSES.items():
        ap50, pr_curve = compute_class_ap_at_iou(
            gt_by_image, preds_by_image, class_id, iou_threshold=0.50
        )
        ap_ious = [
            compute_class_ap_at_iou(gt_by_image, preds_by_image, class_id, iou_threshold=t)[0]
            for t in IOU_THRESHOLDS
        ]
        valid_aps = [v for v in ap_ious if v is not None]
        ap50_95 = round(sum(valid_aps) / len(valid_aps), 6) if valid_aps else None
        ap_per_class[class_name] = {
            "AP50": ap50,
            "AP50-95": ap50_95,
            "pr_curve_iou50": pr_curve,
        }
        operating["per_class"][class_name]["AP50"] = ap50
        operating["per_class"][class_name]["AP50-95"] = ap50_95
        operating["per_class"][class_name]["pr_curve_iou50"] = pr_curve

    ap50_vals = [v["AP50"] for v in ap_per_class.values() if v["AP50"] is not None]
    ap50_95_vals = [v["AP50-95"] for v in ap_per_class.values() if v["AP50-95"] is not None]

    operating["mean_AP50_unweighted"] = (
        round(sum(ap50_vals) / len(ap50_vals), 6) if len(ap50_vals) == len(CLASSES) else None
    )
    operating["mean_AP50_95_unweighted"] = (
        round(sum(ap50_95_vals) / len(ap50_95_vals), 6)
        if len(ap50_95_vals) == len(CLASSES)
        else None
    )
    operating["trivial_zero_detector_reference"] = {
        "precision": "undefined",
        "recall": 0.0,
        "AP50": 0.0,
        "AP50-95": 0.0,
    }
    return operating


def default_best_weights():
    d2 = RUNS_DIR / "detect" / "d2_referencia_20261025" / "weights" / "best.pt"
    if d2.is_file():
        return d2
    d1 = RUNS_DIR / "detect" / "d1_humo_20261025" / "weights" / "best.pt"
    return d1


def run_validation_and_freeze(weights_path):
    weights_path = Path(weights_path).resolve()
    if not weights_path.is_file():
        raise IntegrityError(f"No existen los pesos a validar: {weights_path}")

    gpu_info = verify_gpu_environment()
    gt_val, num_val_images = load_partition_ground_truth("validation")
    preds_val, raw_val_preds = generate_raw_predictions(weights_path, "validation")
    selected_eval, grid = select_operating_threshold(gt_val, preds_val)
    selected_threshold = selected_eval["confidence_threshold"]
    val_metrics = full_partition_metrics(gt_val, preds_val, selected_threshold)

    AUDITORIA_DIR.mkdir(parents=True, exist_ok=True)
    val_report = {
        "phase": "E_validation_and_threshold_selection",
        "evaluated_at_utc": datetime.now(timezone.utc).isoformat(),
        "git_commit": current_commit(),
        "partition": "validation",
        "images_evaluated": num_val_images,
        "weights_path": str(weights_path),
        "weights_sha256": file_sha256(weights_path),
        "selection_rule": "max_validation_macro_F1_at_IoU_0.50_tie_break_macro_recall_then_lower_threshold",
        "selected_operating_threshold": selected_threshold,
        "metrics_at_selected_threshold": val_metrics,
        "threshold_search_grid_summary": grid,
        "raw_predictions_count": len(raw_val_preds),
        "cohort_limitation": "Solo imagenes con anotaciones objetivo; no establece rendimiento en radiografias sanas o no seleccionadas.",
        "patient_independence_verified": False,
    }
    VALIDATION_REPORT_JSON.write_text(
        json.dumps(val_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    frozen = {
        "version": 1,
        "frozen_at_utc": datetime.now(timezone.utc).isoformat(),
        "git_commit": current_commit(),
        "model_name": "coronyx-dentex-yolov8n",
        "model_version": "1.0.0-mvp",
        "weights_path": str(weights_path),
        "weights_sha256": file_sha256(weights_path),
        "classes": CLASSES,
        "preprocessing": {
            "imgsz": 640,
            "letterbox_aspect_ratio_preserved": True,
        },
        "nms_parameters": {
            "iou_threshold": 0.70,
            "agnostic_nms": False,
            "max_det": 300,
        },
        "operating_confidence_threshold": selected_threshold,
        "validation_summary": {
            "mean_AP50_unweighted": val_metrics["mean_AP50_unweighted"],
            "mean_AP50_95_unweighted": val_metrics["mean_AP50_95_unweighted"],
            "macro_F1": val_metrics["macro_F1"],
            "macro_precision": val_metrics["macro_precision"],
            "macro_recall": val_metrics["macro_recall"],
        },
        "environment": gpu_info["packages"],
        "policy_sha256": file_sha256(POLICY_PATH),
        "partitions_sha256": file_sha256(PARTITIONS_PATH),
        "training_config_sha256": file_sha256(CONFIG_PATH),
    }
    FROZEN_MODEL_JSON.write_text(
        json.dumps(frozen, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return val_report, frozen


def run_internal_test():
    if not FROZEN_MODEL_JSON.is_file():
        raise IntegrityError(
            "Debe ejecutarse primero la Fase E (--particion validation) para congelar el modelo antes de usar test."
        )
    frozen = json.loads(FROZEN_MODEL_JSON.read_text(encoding="utf-8"))
    weights_path = Path(frozen["weights_path"])
    if not weights_path.is_file() or file_sha256(weights_path) != frozen["weights_sha256"]:
        raise IntegrityError("Los pesos congelados no existen o cambiaron su hash SHA-256")

    threshold = float(frozen["operating_confidence_threshold"])
    imgsz = int(frozen["preprocessing"]["imgsz"])
    nms_iou = float(frozen["nms_parameters"]["iou_threshold"])

    gt_test, num_test_images = load_partition_ground_truth("test")
    preds_test, raw_test_preds = generate_raw_predictions(
        weights_path, "test", imgsz=imgsz, nms_iou=nms_iou
    )
    test_metrics = full_partition_metrics(gt_test, preds_test, threshold)

    final_report = {
        "phase": "F_internal_test_evaluation",
        "evaluation_type": "internal_restricted_holdout_not_official_dentex_test",
        "evaluated_at_utc": datetime.now(timezone.utc).isoformat(),
        "git_commit": current_commit(),
        "frozen_model": frozen,
        "partition": "test",
        "images_evaluated": num_test_images,
        "operating_threshold_used": threshold,
        "metrics": test_metrics,
        "predictions_above_operating_threshold": [
            p for p in raw_test_preds if p["score"] >= threshold
        ],
        "total_raw_candidates_for_ap_curve": len(raw_test_preds),
        "limitations": [
            "Evaluación interna sobre 127 imágenes reservadas con etiquetas objetivo; no equivale al test oficial LabelMe de DENTEX.",
            "La selección excluye imágenes sin etiquetas objetivo; no establece especificidad ni rendimiento en radiografías sanas.",
            "No está demostrada la independencia por paciente al carecer la fuente de IDs de paciente/estudio.",
            "Las cajas delimitan dientes anotados con una condición, no el contorno exacto de una lesión ni el número dental FDI.",
        ],
    }
    FINAL_TEST_REPORT_JSON.write_text(
        json.dumps(final_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    write_markdown_summary(final_report)
    return final_report


def write_markdown_summary(final_report):
    frozen = final_report["frozen_model"]
    m = final_report["metrics"]
    c0 = m["per_class"]["caries"]
    c1 = m["per_class"]["caries_profunda"]
    val_s = frozen["validation_summary"]

    md = f"""# CORONYX — Informe de Selección (Fase E) y Evaluación Interna Final (Fase F)

**Fecha UTC:** `{final_report["evaluated_at_utc"]}`  
**Modelo congelado:** `{frozen["model_name"]}` (`{frozen["model_version"]}`)  
**SHA-256 de los pesos (`best.pt`):** `{frozen["weights_sha256"]}`  
**Umbral operativo congelado en validación:** `{frozen["operating_confidence_threshold"]}` (IoU de coincidencia `0.50`)

---

## 1. Resumen en Validación (`44` imágenes — Fase E)

- **Media no ponderada AP50:** `{val_s["mean_AP50_unweighted"]}`
- **Media no ponderada AP50-95:** `{val_s["mean_AP50_95_unweighted"]}`
- **Macro F1 (en umbral `{frozen["operating_confidence_threshold"]}`):** `{val_s["macro_F1"]}` (Precisión macro `{val_s["macro_precision"]}`, Recall macro `{val_s["macro_recall"]}`)

---

## 2. Resultados en Prueba Interna (`127` imágenes reservadas — Fase F)

- **Media no ponderada AP50:** `{m["mean_AP50_unweighted"]}`
- **Media no ponderada AP50-95:** `{m["mean_AP50_95_unweighted"]}`
- **Macro F1 (en umbral congelado `{frozen["operating_confidence_threshold"]}`):** `{m["macro_F1"]}` (Precisión macro `{m["macro_precision"]}`, Recall macro `{m["macro_recall"]}`)

| Clase | Imágenes con ref. | Anotaciones ref. | TP | FP | FN | Precisión | Recall | F1 | AP50 | AP50-95 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`0: caries`** | {c0["images_with_reference"]} | {c0["reference_annotations"]} | {c0["TP"]} | {c0["FP"]} | {c0["FN"]} | {c0["precision"]} | {c0["recall"]} | {c0["F1"]} | {c0["AP50"]} | {c0["AP50-95"]} |
| **`1: caries_profunda`** | {c1["images_with_reference"]} | {c1["reference_annotations"]} | {c1["TP"]} | {c1["FP"]} | {c1["FN"]} | {c1["precision"]} | {c1["recall"]} | {c1["F1"]} | {c1["AP50"]} | {c1["AP50-95"]} |

---

## 3. Límites metodológicos que acompañan estos resultados

1. **Prueba interna restringida:** las 127 imágenes proceden de la reserva interna estratificada (semilla `20261025`), no del test oficial de DENTEX (cuyo esquema LabelMe utiliza otra taxonomía).
2. **Sesgo de selección:** tanto entrenamiento como validación y prueba interna contienen únicamente radiografías con al menos una etiqueta objetivo. Estas cifras **no establecen rendimiento sobre radiografías sanas o población general no seleccionada**.
3. **Independencia por paciente no verificada:** la fuente pública DENTEX no incluye identificadores de paciente; aunque se verificó ausencia de duplicados exactos y perceptuales, no puede garantizarse que distintas radiografías pertenezcan siempre a pacientes distintos.
4. **Uso previsto:** apoyo visual sujeto a revisión obligatoria por el odontólogo (`PENDIENTE` → `ACEPTADO` / `RECHAZADO` / `CORREGIDO`).
"""
    FINAL_REPORT_MD.write_text(md, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--particion",
        choices=("validation", "test", "ambas"),
        default="validation",
        help="Ejecuta Fase E (validation), Fase F (test con modelo ya congelado) o ambas en orden.",
    )
    parser.add_argument(
        "--pesos",
        type=str,
        default=None,
        help="Ruta opcional al archivo best.pt (por defecto busca d2_referencia_20261025/weights/best.pt).",
    )
    args = parser.parse_args()
    weights = Path(args.pesos) if args.pesos else default_best_weights()

    if args.particion in ("validation", "ambas"):
        val_report, frozen = run_validation_and_freeze(weights)
        print(
            json.dumps(
                {
                    "phase": "E_completed",
                    "weights_used": str(weights),
                    "selected_threshold": frozen["operating_confidence_threshold"],
                    "validation_summary": frozen["validation_summary"],
                    "frozen_manifest": str(FROZEN_MODEL_JSON),
                },
                ensure_ascii=True,
                indent=2,
            )
        )

    if args.particion in ("test", "ambas"):
        final_report = run_internal_test()
        print(
            json.dumps(
                {
                    "phase": "F_completed",
                    "test_images": final_report["images_evaluated"],
                    "operating_threshold_used": final_report["operating_threshold_used"],
                    "mean_AP50_unweighted": final_report["metrics"]["mean_AP50_unweighted"],
                    "mean_AP50_95_unweighted": final_report["metrics"]["mean_AP50_95_unweighted"],
                    "macro_F1": final_report["metrics"]["macro_F1"],
                    "report_json": str(FINAL_TEST_REPORT_JSON),
                    "report_markdown": str(FINAL_REPORT_MD),
                },
                ensure_ascii=True,
                indent=2,
            )
        )


if __name__ == "__main__":
    main()

