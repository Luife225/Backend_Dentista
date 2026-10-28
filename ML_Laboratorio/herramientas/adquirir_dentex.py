"""Adquisición reanudable y acotada; no convierte etiquetas ni entrena modelos.

Por defecto solo muestra el estado local. --descargar obtiene exclusivamente
las imágenes del manifiesto fijado, sin descargar los ZIP completos.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path, PureWindowsPath
import re
import shutil
import struct
import time
import urllib.error
import urllib.request
import zlib

from PIL import Image


LAB = Path(__file__).resolve().parents[1]
REVISION = "7b27ccc8e342dcb774f69adc6ca5e6c09fefce93"
REPO = "https://huggingface.co/datasets/ibrahimhamamci/DENTEX"
MANIFEST = LAB / "MANIFIESTO_ADQUISICION_2026-10-25.json"
RAW_ROOT = LAB / "data" / "dentex_raw" / REVISION
CACHE = LAB / "data" / "dentex_audit" / REVISION
SOURCES = LAB / "fuentes" / "dentex" / REVISION
RECEIPT = LAB / "data" / "adquisicion" / "recibo.json"
MAX_RECORD = 32_000_000
MIN_FREE = 10_000_000_000
ANNOTATIONS = {
    "train": ("train_quadrant_enumeration_disease.json",
              "6e1f702cfd6c83bc63660d9a0fd79fe5b26d5a54b63d93b954a02cc254314483"),
    "validation": ("validation_triple.json",
                   "d058afd35d2849923c7c045e61fd3e05d231dcf74d55009993fff88bd9b6f5a2"),
}


class IntegrityError(RuntimeError):
    """Datos o respuesta incompatibles con la fuente fijada."""


def contained(path, root):
    candidate = Path(path).resolve()
    if not candidate.is_relative_to(Path(root).resolve()):
        raise IntegrityError("La ruta sale de su carpeta autorizada")
    return candidate


def metadata_path(name):
    cached = CACHE / name
    return cached if cached.is_file() else SOURCES / name


def image_destination(row):
    historical = PureWindowsPath(row["proposed_destination"])
    suffix = ("data", "dentex_raw", REVISION, row["source_split"], "images", row["file_name"])
    if historical.parts[-6:] != suffix or ".." in historical.parts:
        raise IntegrityError("Destino histórico distinto del plan fijado")
    return contained(RAW_ROOT / row["source_split"] / "images" / row["file_name"], RAW_ROOT)


def load_manifest():
    manifest_bytes = MANIFEST.read_bytes()
    manifest = json.loads(manifest_bytes)
    if manifest["revision"] != REVISION or manifest["repository"] != REPO:
        raise IntegrityError("Revisión o repositorio inesperados")
    if len(manifest["images"]) != 755:
        raise IntegrityError("El alcance debe ser exactamente 755 imágenes")
    summaries = {row["source_split"]: row for row in manifest["summaries"]}
    seen = set()
    for split, archive, expected_count in (
        ("train", "training_data.zip", 705),
        ("validation", "validation_data.zip", 50),
    ):
        annotation_name, annotation_hash = ANNOTATIONS[split]
        annotation_bytes = metadata_path(annotation_name).read_bytes()
        if sha256(annotation_bytes).hexdigest() != annotation_hash:
            raise IntegrityError("Cambió el JSON original de anotaciones")
        source_images = {row["id"]: row for row in json.loads(annotation_bytes)["images"]}
        directory_bytes = metadata_path(archive + ".directory.json").read_bytes()
        if sha256(directory_bytes).hexdigest() != summaries[split]["archive_directory_sha256"]:
            raise IntegrityError("Cambió el índice del ZIP")
        entries = {row["name"]: row for row in json.loads(directory_bytes)["entries"]}
        rows = [row for row in manifest["images"] if row["source_split"] == split]
        if len(rows) != expected_count or {row["image_id"] for row in rows} != set(source_images):
            raise IntegrityError("El manifiesto no corresponde a las imágenes del JSON")
        for row in rows:
            image = source_images[row["image_id"]]
            name = row["file_name"]
            if not re.fullmatch(r"(?:train|val)_\d+\.png", name):
                raise IntegrityError("Nombre de imagen no permitido")
            key = (split, name)
            if key in seen:
                raise IntegrityError("Destino repetido")
            seen.add(key)
            # El manifiesto histórico conserva la procedencia de la primera PC.
            # Solo el destino en memoria se adapta a la carpeta de este clon.
            row["proposed_destination"] = str(image_destination(row))
            expected_url = f"{REPO}/resolve/{REVISION}/DENTEX/{archive}"
            if row["archive_url"] != expected_url:
                raise IntegrityError("URL fuera de la fuente fijada")
            entry = entries[row["archive_entry"]]
            for field, source_field in (
                ("archive_entry_offset", "offset"), ("compression_method", "method"),
                ("compressed_bytes", "compressed"), ("original_bytes", "uncompressed"),
                ("original_crc32", "crc32"),
            ):
                if row[field] != entry[source_field]:
                    raise IntegrityError("El registro no coincide con el índice")
            if (name, row["expected_width"], row["expected_height"]) != (
                image["file_name"], image["width"], image["height"]
            ):
                raise IntegrityError("Las dimensiones no coinciden con el JSON")
            if not all(0 < row[key] <= MAX_RECORD for key in ("compressed_bytes", "original_bytes")):
                raise IntegrityError("Registro demasiado grande o vacío")
            row["archive_bytes"] = summaries[split]["archive_bytes"]
    return manifest, sha256(manifest_bytes).hexdigest()


def range_bytes(url, start, end, archive_bytes, opener=urllib.request.urlopen):
    if not 0 <= start <= end < archive_bytes or end - start + 1 > MAX_RECORD:
        raise IntegrityError("Rango inválido o demasiado grande")
    # La consulta evita reutilizar una respuesta cacheada para otro Range.
    request = urllib.request.Request(
        f"{url}?coronyx_range={start}-{end}",
        headers={"Range": f"bytes={start}-{end}", "Accept-Encoding": "identity",
                 "User-Agent": "CORONYX-academic-acquisition/1"},
    )
    with opener(request, timeout=60) as response:
        if response.status != 206:
            raise IntegrityError("El servidor ignoró Range; no se leerá el ZIP completo")
        expected = f"bytes {start}-{end}/{archive_bytes}"
        if response.headers.get("Content-Range") != expected:
            raise IntegrityError("Content-Range no coincide con lo solicitado")
        size = end - start + 1
        raw = response.read(size + 1)
        if len(raw) != size:
            raise IntegrityError("Respuesta parcial truncada o excesiva")
        return raw


def unpack_record(packed, row):
    if row["compression_method"] == 0:
        raw = packed
    elif row["compression_method"] == 8:
        decoder = zlib.decompressobj(-15)
        raw = decoder.decompress(packed, row["original_bytes"] + 1)
        if not decoder.eof or decoder.unconsumed_tail or decoder.unused_data:
            raise IntegrityError("Flujo comprimido truncado o excede el tamaño previsto")
    else:
        raise IntegrityError("Método de compresión no admitido")
    return raw


def validate_image(raw, row):
    if len(raw) != row["original_bytes"] or zlib.crc32(raw) != row["original_crc32"]:
        raise IntegrityError("La imagen no coincide por tamaño/CRC32")
    with Image.open(BytesIO(raw)) as image:
        if image.format != "PNG" or image.size != (row["expected_width"], row["expected_height"]):
            raise IntegrityError("Formato o dimensiones distintos de las anotaciones")
        image.verify()
    with Image.open(BytesIO(raw)) as image:
        image.load()
    return sha256(raw).hexdigest()


def publish_bytes(path, raw):
    """No sustituye un archivo existente diferente, incluso tras reanudar."""
    target = contained(path, RAW_ROOT)
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if target.read_bytes() != raw:
            raise IntegrityError("Archivo existente diferente; se conserva para revisión")
        return
    part = target.with_suffix(target.suffix + ".part")
    # .part solo es un temporal propio; un original nunca se abre para escritura.
    if part.exists() and part.is_symlink():
        raise IntegrityError("Temporal enlazado no permitido")
    with part.open("wb") as output:
        output.write(raw)
    try:
        # rename en Windows no reemplaza un destino que aparezca durante la escritura.
        if target.exists():
            if target.read_bytes() != raw:
                raise IntegrityError("Conflicto concurrente de destino")
        else:
            part.rename(target)
    finally:
        if part.exists():
            part.unlink()


def local_file(row):
    destination = contained(row["proposed_destination"], RAW_ROOT)
    if destination.exists():
        raw = destination.read_bytes()
        return raw, validate_image(raw, row), "existing_verified"
    for candidate in row["verified_reuse_candidates"]:
        path = Path(candidate["path"])
        if path.is_file():
            raw = path.read_bytes()
            if sha256(raw).hexdigest() != candidate["sha256"]:
                continue
            return raw, validate_image(raw, row), "copied_verified_sample"
    return None


def acquire_one(row):
    local = local_file(row)
    if local:
        raw, digest, action = local
    else:
        offset = row["archive_entry_offset"]
        header = range_bytes(row["archive_url"], offset, offset + 29, row["archive_bytes"])
        values = struct.unpack("<4s5H3L2H", header)
        if values[0] != b"PK\x03\x04" or values[3] != row["compression_method"] or values[2] & 1:
            raise IntegrityError("Cabecera ZIP inesperada o cifrada")
        metadata_size = values[-2] + values[-1]
        if not 0 < metadata_size <= 65535:
            raise IntegrityError("Longitud de cabecera no admitida")
        metadata = range_bytes(row["archive_url"], offset + 30,
                               offset + 29 + metadata_size, row["archive_bytes"])
        if metadata[:values[-2]].decode("utf-8") != row["archive_entry"]:
            raise IntegrityError("La cabecera pertenece a otra entrada")
        start = offset + 30 + metadata_size
        packed = range_bytes(row["archive_url"], start,
                             start + row["compressed_bytes"] - 1, row["archive_bytes"])
        raw = unpack_record(packed, row)
        digest = validate_image(raw, row)
        action = "retrieved_verified_record"
    publish_bytes(row["proposed_destination"], raw)
    return {"source_split": row["source_split"], "file_name": row["file_name"],
            "image_id": row["image_id"], "path": row["proposed_destination"],
            "sha256": digest, "bytes": len(raw), "action": action,
            "width": row["expected_width"], "height": row["expected_height"]}


def acquire_with_retry(row):
    for attempt in range(3):
        try:
            return acquire_one(row)
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            if isinstance(error, urllib.error.HTTPError) and error.code not in (408, 429, 500, 502, 503, 504):
                raise
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def save_receipt(receipt):
    RECEIPT.parent.mkdir(parents=True, exist_ok=True)
    receipt["updated_at_utc"] = datetime.now(timezone.utc).isoformat()
    temporary = RECEIPT.with_suffix(".json.part")
    temporary.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(RECEIPT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--descargar", action="store_true")
    parser.add_argument("--trabajadores", type=int, choices=range(1, 5), default=3)
    parser.add_argument("--limite", type=int, default=None, help="Límite de imágenes para comprobación inicial")
    args = parser.parse_args()
    if args.limite is not None and args.limite < 1:
        parser.error("--limite debe ser positivo")
    manifest, manifest_hash = load_manifest()
    rows = manifest["images"]
    if args.limite:
        rows = rows[:args.limite]
    reusable, existing, missing_bytes = 0, 0, 0
    for row in rows:
        if Path(row["proposed_destination"]).exists():
            existing += 1
        elif any(Path(candidate["path"]).is_file() for candidate in row["verified_reuse_candidates"]):
            reusable += 1
        else:
            missing_bytes += row["compressed_bytes"]
    free = shutil.disk_usage(LAB).free
    print(json.dumps({"mode": "download" if args.descargar else "local_status_only",
                      "images_in_scope": len(rows), "existing_to_revalidate": existing,
                      "samples_to_revalidate": reusable, "estimated_pending_compressed_bytes": missing_bytes,
                      "free_bytes": free, "branch_scope": "ML_Laboratorio only"}), flush=True)
    if not args.descargar:
        return
    needed = sum(row["original_bytes"] for row in rows if not Path(row["proposed_destination"]).exists())
    if free < MIN_FREE + needed + 2 * MAX_RECORD * args.trabajadores:
        raise RuntimeError("Espacio insuficiente para datos, temporales y margen de 10 GB")
    receipt = {"version": 1, "source_revision": REVISION, "manifest_sha256": manifest_hash,
               "status": "in_progress", "expected_images": len(manifest["images"]),
               "selected_images": len(rows), "images": [], "errors": [], "annotations": [],
               "source_annotations_modified": False, "training_executed": False,
               "dataset_ready_to_train": False}
    for split, (name, expected_hash) in ANNOTATIONS.items():
        raw = metadata_path(name).read_bytes()
        if sha256(raw).hexdigest() != expected_hash:
            raise IntegrityError("Anotaciones originales modificadas")
        target = RAW_ROOT / split / "annotations.original.json"
        publish_bytes(target, raw)
        receipt["annotations"].append({"source_split": split, "path": str(target),
                                        "sha256": expected_hash, "bytes": len(raw)})
    save_receipt(receipt)
    with ThreadPoolExecutor(max_workers=args.trabajadores) as pool:
        futures = {pool.submit(acquire_with_retry, row): row for row in rows}
        for future in as_completed(futures):
            row = futures[future]
            try:
                receipt["images"].append(future.result())
            except Exception as error:
                receipt["errors"].append({"source_split": row["source_split"],
                                          "file_name": row["file_name"],
                                          "error": f"{type(error).__name__}: {error}"})
                # Un fallo de integridad o respuesta Range detiene la adquisición.
                if isinstance(error, IntegrityError):
                    for pending in futures:
                        pending.cancel()
                    receipt["status"] = "stopped_integrity_error"
                    save_receipt(receipt)
                    raise
            completed = len(receipt["images"]) + len(receipt["errors"])
            if completed % 10 == 0 or completed == len(rows):
                save_receipt(receipt)
                print(json.dumps({"verified": len(receipt["images"]), "errors": len(receipt["errors"]),
                                  "selected": len(rows)}), flush=True)
    receipt["images"].sort(key=lambda row: (row["source_split"], row["file_name"]))
    receipt["status"] = ("complete" if len(receipt["images"]) == 755 and not receipt["errors"]
                         else "partial_verified")
    receipt["verified_original_bytes"] = sum(row["bytes"] for row in receipt["images"])
    save_receipt(receipt)
    print(json.dumps({"status": receipt["status"], "receipt": str(RECEIPT),
                      "verified_images": len(receipt["images"]), "errors": len(receipt["errors"])}), flush=True)
    if receipt["errors"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
