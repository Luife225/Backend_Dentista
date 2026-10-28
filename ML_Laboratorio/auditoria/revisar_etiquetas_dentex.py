"""Read DENTEX annotation JSONs and ZIP directories; never retrieve image payloads.

Uses only Python's standard library. Raw source data is stored under the
laboratory's ignored data/ directory. Reports do not modify source annotations.
"""

from collections import Counter, defaultdict
from hashlib import sha256
import json
import math
from pathlib import Path, PurePosixPath
import struct
import urllib.request
import zlib


LAB = Path(__file__).resolve().parents[1]
CACHE = LAB / "data" / "dentex_audit"
OUT = Path(__file__).resolve().parent
REPO = "https://huggingface.co/datasets/ibrahimhamamci/DENTEX"
API = "https://huggingface.co/api/datasets/ibrahimhamamci/DENTEX/revision/main"
TARGETS = {1, 3}
DATE = "2026-10-25"


def request(url, byte_range=None, limit=4_000_000):
    headers = {"Accept-Encoding": "identity", "User-Agent": "CORONYX-annotation-audit/1"}
    if byte_range:
        headers["Range"] = "bytes=" + byte_range
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=35) as response:
        if byte_range and response.status != 206:
            raise RuntimeError("Server ignored Range; stopped before reading the archive body")
        body = response.read(limit + 1)
        if len(body) > limit:
            raise RuntimeError("Response exceeded the audit's bounded read limit")
        return body, response.headers.get("Content-Range")


def get_directory(url):
    tail, content_range = request(url, "-65557")
    pos = tail.rfind(b"PK\x05\x06")
    if pos < 0:
        raise RuntimeError("ZIP end record not found")
    end = struct.unpack_from("<4s4H2LH", tail, pos)
    size, offset = end[5:7]
    if 0xFFFFFFFF in (size, offset):
        locator = tail.rfind(b"PK\x06\x07", 0, pos)
        zip64_offset = struct.unpack_from("<4sLQL", tail, locator)[2]
        raw, _ = request(url, f"{zip64_offset}-{zip64_offset + 55}")
        size, offset = struct.unpack("<4sQ2H2L4Q", raw)[-2:]
    if size > 4_000_000:
        raise RuntimeError("ZIP directory exceeded the audit limit")
    raw, _ = request(url, f"{offset}-{offset + size - 1}")
    entries, pos = [], 0
    while pos < len(raw) and raw[pos:pos + 4] == b"PK\x01\x02":
        values = struct.unpack_from("<4s6H3L5H2L", raw, pos)
        name_length, extra_length, comment_length = values[10:13]
        name = raw[pos + 46:pos + 46 + name_length].decode("utf-8")
        extra = raw[pos + 46 + name_length:pos + 46 + name_length + extra_length]
        uncompressed, compressed, local_offset = values[9], values[8], values[-1]
        ep = 0
        while ep < len(extra):
            tag, length = struct.unpack_from("<HH", extra, ep)
            payload = extra[ep + 4:ep + 4 + length]
            if tag == 1:
                cursor = 0
                if uncompressed == 0xFFFFFFFF:
                    uncompressed = struct.unpack_from("<Q", payload, cursor)[0]
                    cursor += 8
                if compressed == 0xFFFFFFFF:
                    compressed = struct.unpack_from("<Q", payload, cursor)[0]
                    cursor += 8
                if local_offset == 0xFFFFFFFF:
                    local_offset = struct.unpack_from("<Q", payload, cursor)[0]
            ep += 4 + length
        entries.append(dict(name=name, uncompressed=uncompressed, compressed=compressed,
                            offset=local_offset, method=values[4], crc32=values[7]))
        pos += 46 + name_length + extra_length + comment_length
    if pos != len(raw):
        raise RuntimeError("ZIP directory was not parsed completely")
    return dict(archive_bytes=int(content_range.split("/")[-1]), entries=entries)


def annotation_from_zip(url, entry):
    if not entry["name"].endswith(".json"):
        raise RuntimeError("Only annotation JSON payloads may be requested")
    if entry["compressed"] > 2_000_000 or entry["uncompressed"] > 3_000_000:
        raise RuntimeError("Annotation file exceeded the audit limit")
    start = entry["offset"]
    header, _ = request(url, f"{start}-{start + 29}")
    values = struct.unpack("<4s5H3L2H", header)
    if values[0] != b"PK\x03\x04":
        raise RuntimeError("Invalid ZIP local record")
    start += 30 + values[-2] + values[-1]
    packed, _ = request(url, f"{start}-{start + entry['compressed'] - 1}", limit=2_000_000)
    if entry["method"] == 8:
        raw = zlib.decompress(packed, -15)
    elif entry["method"] == 0:
        raw = packed
    else:
        raise RuntimeError("Unsupported annotation compression")
    if len(raw) != entry["uncompressed"] or zlib.crc32(raw) != entry["crc32"]:
        raise RuntimeError("Annotation size or CRC verification failed")
    return raw


def repeated(values):
    return [dict(value=value, count=count) for value, count in Counter(values).items() if count > 1]


def iou(a, b):
    x1, y1 = max(a[0], b[0]), max(a[1], b[1])
    x2, y2 = min(a[0] + a[2], b[0] + b[2]), min(a[1] + a[3], b[1] + b[3])
    inter = max(0, x2 - x1) * max(0, y2 - y1)
    union = a[2] * a[3] + b[2] * b[3] - inter
    return inter / union if union > 0 else 0


def audit(data, directory, split):
    images, annotations = data["images"], data["annotations"]
    image_by_id = {image["id"]: image for image in images}
    category_sets = {n: {c["id"] for c in data[f"categories_{n}"]} for n in (1, 2, 3)}
    archive_by_basename = defaultdict(list)
    prefix = "training_data/quadrant-enumeration-disease/" if split == "train" else ""
    for entry in directory["entries"]:
        if entry["name"].startswith(prefix) and entry["name"].lower().endswith((".png", ".jpg", ".jpeg")):
            archive_by_basename[PurePosixPath(entry["name"]).name].append(entry)
    issues = defaultdict(list)
    by_image, by_tooth, by_box, exact = defaultdict(list), defaultdict(list), defaultdict(list), defaultdict(list)
    found_entries = []
    for image in images:
        if not all(isinstance(image.get(k), (int, float)) and image[k] > 0 for k in ("width", "height")):
            issues["invalid_image_dimensions"].append(image["id"])
        matches = archive_by_basename.get(image["file_name"], [])
        if not matches:
            issues["image_not_listed_in_zip"].append(image["file_name"])
        elif len(matches) > 1:
            issues["ambiguous_image_basename"].append(image["file_name"])
        else:
            found_entries.append(matches[0])
    for annotation in annotations:
        aid, iid = annotation["id"], annotation["image_id"]
        by_image[iid].append(annotation)
        if iid not in image_by_id:
            issues["missing_image_reference"].append(aid)
            continue
        image = image_by_id[iid]
        for level in (1, 2, 3):
            if annotation.get(f"category_id_{level}") not in category_sets[level]:
                issues[f"unknown_category_level_{level}"].append(aid)
        box = annotation.get("bbox")
        if not isinstance(box, list) or len(box) != 4 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in box):
            issues["malformed_bbox"].append(aid)
            continue
        x, y, width, height = box
        if width <= 0 or height <= 0:
            issues["nonpositive_bbox"].append(aid)
        if x < 0 or y < 0 or x + width > image["width"] or y + height > image["height"]:
            issues["bbox_outside_image"].append(dict(id=aid, image=image["file_name"], bbox=box,
                                                   dimensions=[image["width"], image["height"]]))
        tooth_key = (iid, annotation.get("category_id_1"), annotation.get("category_id_2"))
        by_tooth[tooth_key].append(annotation)
        by_box[(iid, tuple(box))].append(annotation)
        exact[(iid, tuple(box), annotation.get("category_id_1"), annotation.get("category_id_2"),
               annotation.get("category_id_3"))].append(aid)
        polygons = annotation.get("segmentation")
        if not isinstance(polygons, list) or not polygons:
            issues["missing_or_nonpolygon_segmentation"].append(aid)
        else:
            for polygon in polygons:
                if not isinstance(polygon, list) or len(polygon) < 6 or len(polygon) % 2 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in polygon):
                    issues["malformed_polygon"].append(aid)
                    break
                if any(px < 0 or py < 0 or px > image["width"] or py > image["height"] for px, py in zip(polygon[::2], polygon[1::2])):
                    issues["polygon_outside_image"].append(aid)
                    break
        if not isinstance(annotation.get("area"), (int, float)) or not math.isfinite(annotation["area"]) or annotation["area"] <= 0:
            issues["invalid_area_metadata"].append(aid)
    multiclass_teeth = []
    same_class_teeth = []
    target_conflicts = []
    for key, rows in by_tooth.items():
        if len(rows) < 2:
            continue
        categories = sorted({row["category_id_3"] for row in rows})
        item = dict(image_id=key[0], image=image_by_id[key[0]]["file_name"],
                    quadrant_category_id=key[1], tooth_category_id=key[2],
                    annotations=[r["id"] for r in rows], categories=categories,
                    boxes=[r["bbox"] for r in rows])
        if len(categories) > 1:
            multiclass_teeth.append(item)
        else:
            same_class_teeth.append(item)
        if TARGETS.issubset(set(categories)):
            target_pairs = [(left, right) for left in rows for right in rows
                            if left['category_id_3'] == 1 and right['category_id_3'] == 3]
            item['target_pair_ious'] = [round(iou(left['bbox'], right['bbox']), 6)
                                       for left, right in target_pairs]
            target_conflicts.append(item)
    near = []
    for iid, rows in by_image.items():
        for i, left in enumerate(rows):
            for right in rows[i + 1:]:
                overlap = iou(left["bbox"], right["bbox"])
                if overlap >= 0.95:
                    near.append(dict(image_id=iid, image=image_by_id[iid]["file_name"],
                                     ids=[left["id"], right["id"]],
                                     categories=[left["category_id_3"], right["category_id_3"]], iou=round(overlap, 6)))
    class_counts = []
    for category in data["categories_3"]:
        rows = [a for a in annotations if a["category_id_3"] == category["id"]]
        class_counts.append(dict(id=category["id"], name=category["name"], annotations=len(rows),
                                 images=len({a["image_id"] for a in rows})))
    observed_keys = sorted({k for image in images for k in image})
    identifier_keys = [k for k in observed_keys if any(t in k.lower() for t in ("patient", "study", "subject", "person"))]
    by_annotation_id = {a['id']: a for a in annotations}
    full_duplicates = []
    for ids in exact.values():
        if len(ids) < 2:
            continue
        canonical = {k: v for k, v in by_annotation_id[ids[0]].items() if k != 'id'}
        if all({k: v for k, v in by_annotation_id[aid].items() if k != 'id'} == canonical for aid in ids[1:]):
            full_duplicates.append(dict(image=image_by_id[canonical['image_id']]['file_name'],
                                        ids=ids, category=canonical['category_id_3']))
    return dict(
        split=split, images=len(images), annotations=len(annotations), class_counts=class_counts,
        top_level_keys=sorted(data), image_keys=observed_keys,
        annotation_keys=sorted({k for a in annotations for k in a}),
        explicit_patient_or_study_keys=identifier_keys,
        duplicate_image_ids=repeated(i["id"] for i in images),
        duplicate_image_names=repeated(i["file_name"] for i in images),
        duplicate_annotation_ids=repeated(a["id"] for a in annotations),
        duplicate_semantic_rows=[ids for ids in exact.values() if len(ids) > 1],
        duplicate_full_records_except_id=full_duplicates,
        exact_box_groups=[dict(image_id=key[0], ids=[a["id"] for a in rows],
                               categories=[a["category_id_3"] for a in rows]) for key, rows in by_box.items() if len(rows) > 1],
        images_without_any_annotation=[i["file_name"] for i in images if not by_image[i["id"]]],
        images_without_target_annotations=[i["file_name"] for i in images if not any(a["category_id_3"] in TARGETS for a in by_image[i["id"]])],
        images_with_both_targets=sum(TARGETS.issubset({a["category_id_3"] for a in by_image[i["id"]]}) for i in images),
        tooth_identity_multiclass=multiclass_teeth, tooth_identity_repeated_same_class=same_class_teeth,
        tooth_identity_caries_and_deep_caries=target_conflicts,
        near_identical_boxes_iou_at_least_095=near, issues=dict(issues),
        image_entries_verified_by_directory=len(found_entries),
        selected_image_payload_bytes=sum(e["uncompressed"] for e in found_entries),
    ), found_entries


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    pin = CACHE / "revision.json"
    if pin.exists():
        revision = json.loads(pin.read_text(encoding="utf-8"))["sha"]
    else:
        raw, _ = request(API)
        revision = json.loads(raw)["sha"]
        pin.write_text(json.dumps(dict(sha=revision, repository=REPO, retrieved_date=DATE), indent=2), encoding="utf-8")
    if len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
        raise RuntimeError("Unexpected source revision")
    source_dir = CACHE / revision
    source_dir.mkdir(exist_ok=True)
    base = REPO + "/resolve/" + revision + "/DENTEX/"
    directories = {}
    for split, archive in (("train", "training_data.zip"), ("validation", "validation_data.zip")):
        cache_path = source_dir / (archive + ".directory.json")
        if cache_path.exists():
            directory = json.loads(cache_path.read_text(encoding="utf-8"))
        else:
            directory = get_directory(base + archive)
            cache_path.write_text(json.dumps(directory, indent=2), encoding="utf-8")
        directories[split] = directory
    train_entry = next(e for e in directories["train"]["entries"] if e["name"] == "training_data/quadrant-enumeration-disease/train_quadrant_enumeration_disease.json")
    raw_files = {}
    for split, filename in (("train", "train_quadrant_enumeration_disease.json"), ("validation", "validation_triple.json")):
        local = source_dir / filename
        if local.exists():
            raw = local.read_bytes()
        else:
            raw = annotation_from_zip(base + "training_data.zip", train_entry) if split == "train" else request(base + filename, limit=200_000)[0]
            local.write_bytes(raw)
        if split == "train" and (len(raw) != train_entry["uncompressed"] or zlib.crc32(raw) != train_entry["crc32"]):
            raise RuntimeError("Cached training annotation integrity check failed")
        raw_files[split] = raw
    results, entries = {}, {}
    for split in ("train", "validation"):
        results[split], entries[split] = audit(json.loads(raw_files[split]), directories[split], split)
    train_by_signature = defaultdict(list)
    for entry in entries["train"]:
        train_by_signature[(entry["crc32"], entry["uncompressed"])].append(entry["name"])
    candidates = [dict(train=name, validation=e["name"]) for e in entries["validation"] for name in train_by_signature.get((e["crc32"], e["uncompressed"]), [])]
    train_data, val_data = (json.loads(raw_files[s]) for s in ("train", "validation"))
    report = dict(date=DATE, repository=REPO, revision=revision,
                  scope="diagnostic training and validation annotations plus archive directories; no image payloads or test data",
                  sources={s: dict(sha256=sha256(raw).hexdigest(), bytes=len(raw), archive_bytes=directories[s]["archive_bytes"]) for s, raw in raw_files.items()},
                  results=results,
                  cross_split=dict(
                      same_image_filenames=sorted({i["file_name"] for i in train_data["images"]} & {i["file_name"] for i in val_data["images"]}),
                      matching_crc32_and_size_candidates=candidates,
                      categories_identical=all(train_data[k] == val_data[k] for k in ("categories_1", "categories_2", "categories_3")),
                      patient_independence_verified=False, image_content_duplicates_checked=False),
                  limitations=["No clinical review", "No image payload decoding or visual inspection in this audit", "No test access", "CRC32 and size are candidate screening only, not proof of identity or independence"])
    output = OUT / "resultado_etiquetas_2026-10-25.json"
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(dict(report=str(output), revision=revision,
                         splits={s: dict(images=r["images"], annotations=r["annotations"],
                                         issues={k: len(v) for k, v in r["issues"].items()},
                                         duplicate_ids=len(r["duplicate_annotation_ids"]),
                                         duplicate_rows=len(r["duplicate_semantic_rows"]),
                                         multiclass_teeth=len(r["tooth_identity_multiclass"]),
                                         target_same_tooth=len(r["tooth_identity_caries_and_deep_caries"]),
                                         same_class_teeth=len(r["tooth_identity_repeated_same_class"]),
                                         near_boxes=len(r["near_identical_boxes_iou_at_least_095"]),
                                         no_target=len(r["images_without_target_annotations"]),
                                         explicit_patient_keys=r["explicit_patient_or_study_keys"],
                                         archive_matches=r["image_entries_verified_by_directory"]) for s, r in results.items()},
                         cross_split=report["cross_split"]), indent=2))


if __name__ == "__main__":
    main()
