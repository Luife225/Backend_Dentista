"""Controles sin red para no aceptar datos incorrectos ni sobrescribir fuentes."""

from hashlib import sha256
from io import BytesIO
from pathlib import Path
from contextlib import contextmanager
import tempfile
import struct
import unittest
from unittest.mock import patch
import zlib

from PIL import Image
from herramientas import adquirir_dentex as acquisition


class Response:
    def __init__(self, status, content_range, raw):
        self.status = status
        self.headers = {"Content-Range": content_range}
        self.raw = raw
        self.read_called = False

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self, size):
        self.read_called = True
        return self.raw[:size]


def fixture():
    image = Image.new("L", (16, 12), color=82)
    output = BytesIO()
    image.save(output, format="PNG")
    raw = output.getvalue()
    return raw, {"original_bytes": len(raw), "original_crc32": zlib.crc32(raw),
                 "expected_width": 16, "expected_height": 12, "compression_method": 8}


@contextmanager
def workspace_temporary_directory():
    root = acquisition.LAB / "data" / "test_temporales"
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=root) as folder:
        # Comprobar el destino absoluto antes de la limpieza recursiva del contexto.
        acquisition.contained(folder, root)
        yield folder


class AcquisitionTests(unittest.TestCase):
    def test_ignored_range_never_reads_archive(self):
        response = Response(200, None, b"whole archive")
        with self.assertRaises(acquisition.IntegrityError):
            acquisition.range_bytes("https://example.test/archive", 3, 6, 20,
                                    opener=lambda *_args, **_kwargs: response)
        self.assertFalse(response.read_called)

    def test_wrong_range_never_reads_body(self):
        response = Response(206, "bytes 0-3/20", b"abcd")
        with self.assertRaises(acquisition.IntegrityError):
            acquisition.range_bytes("https://example.test/archive", 3, 6, 20,
                                    opener=lambda *_args, **_kwargs: response)
        self.assertFalse(response.read_called)

    def test_valid_exact_range(self):
        response = Response(206, "bytes 3-6/20", b"abcd")
        self.assertEqual(acquisition.range_bytes("https://example.test/archive", 3, 6, 20,
                         opener=lambda *_args, **_kwargs: response), b"abcd")

    def test_truncated_and_excessive_range_rejected(self):
        for raw in (b"abc", b"abcde"):
            with self.subTest(raw=raw), self.assertRaises(acquisition.IntegrityError):
                acquisition.range_bytes("https://example.test/archive", 3, 6, 20,
                    opener=lambda *_args, **_kwargs: Response(206, "bytes 3-6/20", raw))

    def test_valid_png_and_crc(self):
        raw, row = fixture()
        self.assertEqual(acquisition.validate_image(raw, row), sha256(raw).hexdigest())

    def test_crc_and_dimensions_rejected(self):
        raw, row = fixture()
        for bad in (dict(row, original_crc32=0), dict(row, expected_width=17)):
            with self.subTest(bad=bad), self.assertRaises(acquisition.IntegrityError):
                acquisition.validate_image(raw, bad)

    def test_bounded_decompression(self):
        raw, row = fixture()
        compressor = zlib.compressobj(wbits=-15)
        packed = compressor.compress(raw) + compressor.flush()
        self.assertEqual(acquisition.unpack_record(packed, row), raw)
        with self.assertRaises(acquisition.IntegrityError):
            acquisition.unpack_record(packed, dict(row, original_bytes=1))
        with self.assertRaises(acquisition.IntegrityError):
            acquisition.unpack_record(packed[:-1], row)

    def test_conflicting_original_preserved_and_same_file_resumes(self):
        with workspace_temporary_directory() as folder, patch.object(acquisition, "RAW_ROOT", Path(folder)):
            target = Path(folder) / "train" / "images" / "train_0.png"
            acquisition.publish_bytes(target, b"original")
            acquisition.publish_bytes(target, b"original")
            with self.assertRaises(acquisition.IntegrityError):
                acquisition.publish_bytes(target, b"replacement")
            self.assertEqual(target.read_bytes(), b"original")
            self.assertFalse(target.with_suffix(".png.part").exists())

    def test_path_escape_rejected(self):
        with workspace_temporary_directory() as folder:
            with self.assertRaises(acquisition.IntegrityError):
                acquisition.contained(Path(folder) / ".." / "outside.png", Path(folder))

    def test_selected_zip_entry_and_resume_without_network(self):
        raw, row = fixture()
        compressor = zlib.compressobj(wbits=-15)
        packed = compressor.compress(raw) + compressor.flush()
        name = b"selected/train_0.png"
        header = struct.pack("<4s5H3L2H", b"PK\x03\x04", 20, 0, 8, 0, 0,
                             row["original_crc32"], len(packed), len(raw), len(name), 0)
        archive = header + name + packed
        with workspace_temporary_directory() as folder, patch.object(acquisition, "RAW_ROOT", Path(folder)):
            target = Path(folder) / "train" / "images" / "train_0.png"
            row.update(source_split="train", file_name="train_0.png", image_id=1,
                       proposed_destination=str(target), archive_url="https://example.test/zip",
                       archive_entry=name.decode(), archive_entry_offset=0,
                       compressed_bytes=len(packed), archive_bytes=len(archive), verified_reuse_candidates=[])
            with patch.object(acquisition, "range_bytes", side_effect=lambda _url, a, b, _size: archive[a:b + 1]):
                result = acquisition.acquire_one(row)
            self.assertEqual(result["action"], "retrieved_verified_record")
            self.assertEqual(target.read_bytes(), raw)
            with patch.object(acquisition, "range_bytes", side_effect=AssertionError("No debe acceder a la red")):
                result = acquisition.acquire_one(row)
            self.assertEqual(result["action"], "existing_verified")

    def test_wrong_zip_entry_does_not_create_original(self):
        raw, row = fixture()
        name = b"other.png"
        header = struct.pack("<4s5H3L2H", b"PK\x03\x04", 20, 0, 8, 0, 0,
                             row["original_crc32"], 1, len(raw), len(name), 0)
        archive = header + name + b"x"
        with workspace_temporary_directory() as folder, patch.object(acquisition, "RAW_ROOT", Path(folder)):
            target = Path(folder) / "train_0.png"
            row.update(proposed_destination=str(target), archive_url="https://example.test/zip",
                       archive_entry="expected.png", archive_entry_offset=0,
                       compressed_bytes=1, archive_bytes=len(archive), verified_reuse_candidates=[])
            with patch.object(acquisition, "range_bytes", side_effect=lambda _url, a, b, _size: archive[a:b + 1]):
                with self.assertRaises(acquisition.IntegrityError):
                    acquisition.acquire_one(row)
            self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
