from io import BytesIO
import unittest

from PIL import Image

from herramientas.evaluar_modelo import evaluate_at_threshold, select_operating_threshold
from herramientas.inferir_radiografia import PanoramicDetectorSession
from herramientas.servicio_http_ml import extract_uploaded_bytes, render_annotated_png_bytes


class EvaluationAndInferenceTests(unittest.TestCase):
    def test_controlled_cases_hit_miss_duplicate_and_wrong_class(self):
        # 1 referencia de caries (clase 0) en img_a, 1 referencia de caries_profunda (clase 1) en img_b
        gt_by_image = {
            "img_a.png": {
                0: [{"id": 10, "bbox_xyxy": [100.0, 100.0, 200.0, 200.0]}],
                1: [],
            },
            "img_b.png": {
                0: [],
                1: [{"id": 20, "bbox_xyxy": [300.0, 300.0, 400.0, 400.0]}],
            },
        }
        # Predicciones controladas:
        # - En img_a (clase 0): 1 acierto exacto (score 0.90) + 1 duplicado sobrante sobre la misma caja (score 0.70)
        # - En img_b: 0 predicciones de clase 1 (omisión FN), pero 1 predicción en la misma caja con clase equivocada 0 (FP en clase 0 + confusión cruzada)
        preds_by_image = {
            "img_a.png": {
                0: [
                    {"score": 0.90, "bbox_xyxy": [100.0, 100.0, 200.0, 200.0]},
                    {"score": 0.70, "bbox_xyxy": [102.0, 102.0, 198.0, 198.0]},
                ],
                1: [],
            },
            "img_b.png": {
                0: [
                    {"score": 0.85, "bbox_xyxy": [300.0, 300.0, 400.0, 400.0]},
                ],
                1: [],
            },
        }

        res = evaluate_at_threshold(gt_by_image, preds_by_image, conf_threshold=0.50, iou_threshold=0.50)
        caries_metrics = res["per_class"]["caries"]
        profunda_metrics = res["per_class"]["caries_profunda"]

        # Clase 0 (caries): 1 TP (el de 0.90), 2 FP (el duplicado de 0.70 en img_a y la clase equivocada de 0.85 en img_b), 0 FN
        self.assertEqual(caries_metrics["TP"], 1)
        self.assertEqual(caries_metrics["FP"], 2)
        self.assertEqual(caries_metrics["FN"], 0)
        self.assertEqual(len(caries_metrics["cross_class_confusions"]), 1)

        # Clase 1 (caries_profunda): 0 TP, 0 FP, 1 FN (la omisión en img_b; la caja con clase 0 no cuenta como acierto)
        self.assertEqual(profunda_metrics["TP"], 0)
        self.assertEqual(profunda_metrics["FP"], 0)
        self.assertEqual(profunda_metrics["FN"], 1)

    def test_threshold_selection_tie_break_prefers_recall_then_lower_threshold(self):
        gt_by_image = {
            "img_1.png": {
                0: [{"id": 1, "bbox_xyxy": [10.0, 10.0, 50.0, 50.0]}],
                1: [{"id": 2, "bbox_xyxy": [100.0, 100.0, 150.0, 150.0]}],
            }
        }
        preds_by_image = {
            "img_1.png": {
                0: [{"score": 0.80, "bbox_xyxy": [10.0, 10.0, 50.0, 50.0]}],
                1: [{"score": 0.80, "bbox_xyxy": [100.0, 100.0, 150.0, 150.0]}],
            }
        }
        best, _grid = select_operating_threshold(gt_by_image, preds_by_image)
        # Empate de F1=1.0 y recall=1.0 desde 0.05 hasta 0.80 -> debe elegir el menor umbral (0.05)
        self.assertEqual(best["confidence_threshold"], 0.05)

    def test_missing_weights_returns_explicit_error_not_empty_valid_response(self):
        session = PanoramicDetectorSession(weights_path="no_existe_este_modelo.pt")
        out = session.analyze_bytes(b"fake_bytes", file_name="test.png")
        self.assertEqual(out["status"], "model_unavailable_error")
        self.assertIsNotNone(out["error"])

    def test_http_multipart_extraction_and_preview_rendering(self):
        img = Image.new("RGB", (320, 320), color=(40, 40, 40))
        buf = BytesIO()
        img.save(buf, format="PNG")
        png_bytes = buf.getvalue()

        boundary = "----CoronyxTestBoundary"
        body = (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="val_10.png"\r\n'
            "Content-Type: image/png\r\n\r\n"
        ).encode("utf-8") + png_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")
        headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}

        extracted, fname = extract_uploaded_bytes(headers, body)
        self.assertEqual(fname, "val_10.png")
        self.assertEqual(extracted, png_bytes)

        rendered = render_annotated_png_bytes(
            png_bytes,
            {
                "findings": [
                    {
                        "class_id": 1,
                        "class_name": "caries_profunda",
                        "score": 0.64,
                        "bbox_pixels": {"xMin": 20, "yMin": 30, "xMax": 120, "yMax": 150},
                    }
                ]
            },
        )
        with Image.open(BytesIO(rendered)) as out_img:
            self.assertEqual(out_img.size, (320, 320))


if __name__ == "__main__":
    unittest.main()

