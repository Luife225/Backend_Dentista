from io import BytesIO
import unittest

from PIL import Image, PngImagePlugin

from herramientas.auditar_imagenes import decoded_pixel_hash, duplicate_groups, similarity_candidates


class ImageAuditTests(unittest.TestCase):
    def test_different_png_metadata_same_decoded_pixels(self):
        image = Image.new("L", (16, 16), 80)
        files = []
        for label in ("first", "second"):
            metadata = PngImagePlugin.PngInfo()
            metadata.add_text("comment", label)
            buffer = BytesIO()
            image.save(buffer, format="PNG", pnginfo=metadata)
            files.append(buffer.getvalue())
        self.assertNotEqual(*files)
        with Image.open(BytesIO(files[0])) as left, Image.open(BytesIO(files[1])) as right:
            self.assertEqual(decoded_pixel_hash(left), decoded_pixel_hash(right))

    def test_different_transparency_not_identical_pixels(self):
        left = Image.new("L", (16, 16), 80)
        right = left.copy()
        right.info["transparency"] = 80
        self.assertNotEqual(decoded_pixel_hash(left), decoded_pixel_hash(right))

    def test_duplicate_cross_split_is_reported(self):
        rows = [{"file_name": "train_0.png", "source_split": "train", "hash": "same"},
                {"file_name": "val_0.png", "source_split": "validation", "hash": "same"}]
        groups = duplicate_groups(rows, "hash")
        self.assertEqual(len(groups), 1)
        self.assertTrue(groups[0]["crosses_source_splits"])

    def test_similar_image_is_candidate_not_confirmed_duplicate(self):
        rows = [{"source_split": "train", "file_name": name, "width": 100, "height": 50,
                 "decoded_pixel_sha256": name, "dhash256": "0" * 64, "average_hash256": "0" * 64}
                for name in ("train_0.png", "train_1.png")]
        candidates = similarity_candidates(rows)
        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0]["status"], "candidate_requires_review_not_confirmed_duplicate")


if __name__ == "__main__":
    unittest.main()
