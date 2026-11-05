from pathlib import Path
import unittest
from unittest.mock import patch

from herramientas.adquirir_dentex import REVISION, image_destination, IntegrityError
from herramientas.preparar_dataset import unique_annotations, split_training, make_plan


class PreparationTests(unittest.TestCase):
    def test_deduplication_preserves_different_diagnoses_and_boxes(self):
        original = {"id": 3, "image_id": 1, "bbox": [1, 2, 3, 4], "category_id_3": 1}
        rows = [original, dict(original, id=2), dict(original, id=4, category_id_3=3),
                dict(original, id=5, bbox=[2, 2, 3, 4])]
        kept, omitted = unique_annotations(rows)
        self.assertEqual([row["id"] for row in kept], [2, 4, 5])
        self.assertEqual(omitted, [{"keep_id": 2, "omit_id": 3, "image_id": 1}])
        self.assertEqual(len(rows), 4)

    def test_split_is_reproducible_and_keeps_inspected_out_of_test(self):
        rows = [{"file_name": f"train_{i}.png", "targets": [{"category_id_3": 1 if i < 10 else 3}]}
                for i in range(20)]
        first = split_training(rows, 20261025, 0.2, {"train_1.png", "train_11.png"})
        second = split_training(list(reversed(rows)), 20261025, 0.2, {"train_1.png", "train_11.png"})
        self.assertEqual(first, second)
        self.assertEqual(sum(value == "test" for value in first.values()), 4)
        self.assertEqual(first["train_1.png"], "train")
        self.assertEqual(first["train_11.png"], "train")

    def test_relocated_clone_uses_its_own_data_folder(self):
        root = Path("new-laptop-data").resolve()
        row = {"source_split": "train", "file_name": "train_0.png",
               "proposed_destination": f"C:\\old-computer\\ML_Laboratorio\\data\\dentex_raw\\{REVISION}\\train\\images\\train_0.png"}
        with patch("herramientas.adquirir_dentex.RAW_ROOT", root):
            self.assertEqual(image_destination(row), root / "train" / "images" / "train_0.png")
            with self.assertRaises(IntegrityError):
                image_destination(dict(row, proposed_destination="C:\\unrelated\\secret.png"))

    def test_real_plan_has_no_overlap_or_unresolved_images(self):
        plan, _eligible, policy = make_plan()
        self.assertEqual({split: row["images"] for split, row in plan["summary"].items()},
                         {"train": 508, "validation": 44, "test": 127})
        included = {(row["source_split"], row["file_name"]) for row in plan["images"]}
        excluded = {(row["source_split"], row["file_name"]) for row in plan["excluded_images"]}
        self.assertFalse(included & excluded)
        self.assertEqual(len(included) + len(excluded), 755)
        held_out = {row["file_name"] for row in plan["images"] if row["partition"] == "test"}
        self.assertFalse(held_out & set(policy["already_inspected_excluded_from_test"]))
        self.assertFalse(any(2337 in row["annotation_ids"] for row in plan["images"]))
        self.assertTrue(any(2335 in row["annotation_ids"] for row in plan["images"]))
        for row in plan["images"]:
            self.assertTrue(row["annotation_ids"])


if __name__ == "__main__":
    unittest.main()
