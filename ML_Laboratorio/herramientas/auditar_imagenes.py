"""Comprueba originales y busca duplicados por archivo, píxeles y similitud.

No modifica etiquetas, no asigna particiones y no interpreta diagnósticos.
Las coincidencias perceptuales son candidatos para revisión, no duplicados
confirmados ni prueba de pertenecer a un mismo paciente.
"""

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path

from PIL import Image

from .adquirir_dentex import LAB, RAW_ROOT, RECEIPT, REVISION, IntegrityError, load_manifest, validate_image


def perceptual_fingerprints(image):
    grey = image.convert("L")
    resized = grey.resize((17, 16), Image.Resampling.LANCZOS)
    pixels = resized.tobytes()
    difference = 0
    for y in range(16):
        for x in range(16):
            difference = (difference << 1) | (pixels[y * 17 + x] > pixels[y * 17 + x + 1])
    small = grey.resize((16, 16), Image.Resampling.LANCZOS).tobytes()
    mean = sum(small) / len(small)
    average = 0
    for pixel in small:
        average = (average << 1) | (pixel > mean)
    return f"{difference:064x}", f"{average:064x}"


def duplicate_groups(rows, field):
    groups = defaultdict(list)
    for row in rows:
        groups[row[field]].append({"source_split": row["source_split"], "file_name": row["file_name"]})
    return [{"fingerprint": key, "images": group,
             "crosses_source_splits": len({row["source_split"] for row in group}) > 1}
            for key, group in groups.items() if len(group) > 1]


def decoded_pixel_hash(image):
    digest = sha256()
    digest.update(json.dumps([image.mode, *image.size]).encode("ascii"))
    digest.update(image.tobytes())
    if image.mode == "P":
        digest.update(bytes(image.getpalette() or []))
    # tRNS cambia la interpretación de píxeles aunque los canales no tengan alfa.
    digest.update(repr(image.info.get("transparency")).encode("ascii"))
    return digest.hexdigest()


def similarity_candidates(rows):
    fingerprints = [(int(row["dhash256"], 16), int(row["average_hash256"], 16)) for row in rows]
    candidates = []
    for index, left in enumerate(rows):
        for j in range(index + 1, len(rows)):
            right = rows[j]
            if left["decoded_pixel_sha256"] == right["decoded_pixel_sha256"]:
                continue
            left_ratio = left["width"] / left["height"]
            right_ratio = right["width"] / right["height"]
            if abs(left_ratio / right_ratio - 1) > 0.02:
                continue
            difference = (fingerprints[index][0] ^ fingerprints[j][0]).bit_count()
            average = (fingerprints[index][1] ^ fingerprints[j][1]).bit_count()
            if difference <= 8 and average <= 12:
                candidates.append({
                    "left": {"source_split": left["source_split"], "file_name": left["file_name"]},
                    "right": {"source_split": right["source_split"], "file_name": right["file_name"]},
                    "dhash_hamming_distance": difference, "average_hash_hamming_distance": average,
                    "crosses_source_splits": left["source_split"] != right["source_split"],
                    "status": "candidate_requires_review_not_confirmed_duplicate",
                })
    return candidates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fecha", default="2026-10-27")
    args = parser.parse_args()
    # Evita que la fecha actúe como ruta arbitraria de salida.
    datetime.strptime(args.fecha, "%Y-%m-%d")
    manifest, manifest_hash = load_manifest()
    receipt = json.loads(RECEIPT.read_text(encoding="utf-8"))
    if receipt["status"] != "complete" or receipt["manifest_sha256"] != manifest_hash:
        raise IntegrityError("Primero debe completarse la adquisición con el mismo manifiesto")
    receipts = {(row["source_split"], row["file_name"]): row for row in receipt["images"]}
    if len(receipts) != 755:
        raise IntegrityError("Recibo incompleto o con imágenes repetidas")
    rows = []
    for index, source in enumerate(manifest["images"], 1):
        path = RAW_ROOT / source["source_split"] / "images" / source["file_name"]
        raw = path.read_bytes()
        digest = validate_image(raw, source)
        if digest != receipts[(source["source_split"], source["file_name"])]["sha256"]:
            raise IntegrityError("Un original cambió después de su adquisición")
        with Image.open(path) as image:
            image.load()
            difference, average = perceptual_fingerprints(image)
            rows.append({"source_split": source["source_split"], "image_id": source["image_id"],
                         "file_name": source["file_name"], "width": image.width, "height": image.height,
                         "mode": image.mode, "bytes": len(raw), "file_sha256": digest,
                         "decoded_pixel_sha256": decoded_pixel_hash(image), "dhash256": difference,
                         "average_hash256": average, "metadata_keys": sorted(image.info),
                         "clinical_review_executed": False})
        if index % 100 == 0 or index == 755:
            print(json.dumps({"decoded_and_verified": index, "expected": 755}), flush=True)
    file_groups = duplicate_groups(rows, "file_sha256")
    pixel_groups = duplicate_groups(rows, "decoded_pixel_sha256")
    candidates = similarity_candidates(rows)
    summary = {"images_verified": len(rows), "source_splits": dict(Counter(row["source_split"] for row in rows)),
               "original_image_bytes": sum(row["bytes"] for row in rows),
               "exact_file_duplicate_groups": len(file_groups),
               "exact_decoded_pixel_duplicate_groups": len(pixel_groups),
               "perceptual_candidate_pairs": len(candidates),
               "images_with_metadata_keys": sum(bool(row["metadata_keys"]) for row in rows),
               "dimension_mismatches": 0, "integrity_failures": 0,
               "patient_independence_verified": False, "dataset_ready_to_train": False}
    report = {"date": args.fecha, "created_at_utc": datetime.now(timezone.utc).isoformat(),
              "revision": REVISION, "manifest_sha256": manifest_hash, "summary": summary,
              "method": {"exact_files": "SHA256 of original PNG bytes",
                         "exact_pixels": "SHA256 of native mode, dimensions, decoded bytes, palette when indexed and transparency",
                         "perceptual": "256-bit horizontal dHash <=8 AND 256-bit average hash <=12 AND aspect ratio within 2%",
                         "perceptual_thresholds_fixed_before_comparison": True,
                         "perceptual_screening_exhaustive_for_related_variants": False},
              "exact_file_duplicates": file_groups, "exact_pixel_duplicates": pixel_groups,
              "perceptual_candidates": candidates, "images": rows,
              "limitations": ["No clinical label verification", "No proof of patient independence",
                              "No full visual privacy review; metadata values are not published",
                              "Perceptual fingerprints can miss resized, cropped or edited related images"],
              "annotations_modified": False, "partitions_generated": False, "training_executed": False}
    output = LAB / "auditoria" / f"INTEGRIDAD_IMAGENES_{args.fecha}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"summary": summary, "report": str(output)}), flush=True)


if __name__ == "__main__":
    main()
