"""Prepara una copia acotada y congela particiones; no entrena ni cambia fuentes."""

import argparse
from collections import Counter, defaultdict
from hashlib import sha256
import json
import math
from pathlib import Path
import shutil

from .adquirir_dentex import (
    ANNOTATIONS, LAB, RAW_ROOT, REVISION, IntegrityError, contained, metadata_path,
)

POLICY_PATH = LAB / "preparacion" / "POLITICA_MVP_V1.json"
PARTITIONS_PATH = LAB / "preparacion" / "PARTICIONES_DENTEX_V1.json"
OUTPUT = LAB / "datasets" / "dentex_mvp_v1"


def canonical_bytes(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def unique_annotations(annotations):
    """La igualdad excluye únicamente el ID; no fusiona diagnósticos o cuadros."""
    seen, kept, omitted = {}, [], []
    for annotation in sorted(annotations, key=lambda row: row["id"]):
        key = canonical_bytes({k: v for k, v in annotation.items() if k != "id"})
        if key in seen:
            omitted.append({"keep_id": seen[key], "omit_id": annotation["id"],
                            "image_id": annotation["image_id"]})
        else:
            seen[key] = annotation["id"]
            kept.append(annotation)
    return kept, omitted


def split_training(rows, seed, fraction, inspected):
    """Estratifica presencia de etiquetas sin separar dientes ni forzar balance."""
    strata = defaultdict(list)
    for row in rows:
        strata[tuple(sorted({annotation["category_id_3"] for annotation in row["targets"]}))].append(row)
    total = round(len(rows) * fraction)
    allocations = {key: math.floor(len(group) * fraction) for key, group in strata.items()}
    order = sorted(strata, key=lambda key: (-(len(strata[key]) * fraction - allocations[key]), key))
    for key in order[:total - sum(allocations.values())]:
        allocations[key] += 1
    held_out = set()
    for key, group in sorted(strata.items()):
        candidates = [row for row in group if row["file_name"] not in inspected]
        candidates.sort(key=lambda row: sha256(f"{seed}|{row['file_name']}".encode()).hexdigest())
        if len(candidates) < allocations[key]:
            raise IntegrityError("La reserva solicitada viola las restricciones de imágenes inspeccionadas")
        held_out.update(row["file_name"] for row in candidates[:allocations[key]])
    return {row["file_name"]: "test" if row["file_name"] in held_out else "train" for row in rows}


def write_new_or_same(path, raw, root):
    target = contained(path, root)
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if target.read_bytes() != raw:
            raise IntegrityError(f"Se conserva un archivo de otra preparación: {target.name}")
        return
    partial = target.with_suffix(target.suffix + ".part")
    if partial.is_symlink():
        raise IntegrityError("Temporal enlazado no permitido")
    partial.write_bytes(raw)
    partial.rename(target)


def make_plan():
    policy = json.loads(POLICY_PATH.read_text(encoding="utf-8"))
    if policy["source_revision"] != REVISION:
        raise IntegrityError("Política de otra revisión")
    registry = json.loads((LAB / "auditoria" / "REGISTRO_CASOS_2026-10-25.json").read_text(encoding="utf-8"))
    integrity = json.loads((LAB / "auditoria" / "INTEGRIDAD_IMAGENES_2026-10-27.json").read_text(encoding="utf-8"))
    if integrity["revision"] != REVISION or integrity["summary"]["images_verified"] != 755:
        raise IntegrityError("Falta la auditoría completa de la fuente fijada")
    if any(integrity[key] for key in ("exact_file_duplicates", "exact_pixel_duplicates", "perceptual_candidates")):
        raise IntegrityError("Hay posibles grupos relacionados que deben revisarse antes de separar")
    verified = {(row["source_split"], row["file_name"]): row for row in integrity["images"]}
    pending_images = {case["image"] for case in registry["cases"] if case["status"] == "pendiente_revision"}
    mapping = {int(key): value for key, value in policy["target_mapping"].items()}
    eligible, exclusions, duplicate_actions, source_hashes = [], [], [], {}
    for origin, (filename, expected_hash) in ANNOTATIONS.items():
        raw = metadata_path(filename).read_bytes()
        if sha256(raw).hexdigest() != expected_hash:
            raise IntegrityError("Cambió una anotación original")
        source_hashes[origin] = expected_hash
        source = json.loads(raw)
        annotations, omitted = unique_annotations(source["annotations"])
        duplicate_actions.extend({"source_split": origin, **row} for row in omitted)
        by_image = defaultdict(list)
        for annotation in annotations:
            by_image[annotation["image_id"]].append(annotation)
        for image in sorted(source["images"], key=lambda row: row["file_name"]):
            reasons = []
            if origin == "train" and image["file_name"] in pending_images:
                reasons.append("unresolved_registered_case")
            targets = [row for row in by_image[image["id"]] if row["category_id_3"] in mapping]
            if not targets:
                reasons.append("no_target_annotations_not_verified_negative")
            if reasons:
                exclusions.append({"source_split": origin, "file_name": image["file_name"],
                                   "image_id": image["id"], "reasons": reasons,
                                   "all_original_annotation_ids": [row["id"] for row in source["annotations"]
                                                                   if row["image_id"] == image["id"]]})
                continue
            proof = verified[(origin, image["file_name"])]
            if (image["width"], image["height"]) != (proof["width"], proof["height"]):
                raise IntegrityError("Dimensiones distintas de la auditoría")
            eligible.append({**image, "source_split": origin, "targets": targets,
                             "file_sha256": proof["file_sha256"]})
    expected_duplicate = [{"source_split": "train", "keep_id": 2335, "omit_id": 2337, "image_id": 447}]
    if duplicate_actions != expected_duplicate:
        raise IntegrityError("Los duplicados no coinciden con la decisión técnica documentada")
    assignments = split_training([row for row in eligible if row["source_split"] == "train"],
                                 policy["seed"], policy["internal_test_fraction"],
                                 set(policy["already_inspected_excluded_from_test"]))
    images = []
    for row in eligible:
        split = "validation" if row["source_split"] == "validation" else assignments[row["file_name"]]
        images.append({"source_split": row["source_split"], "partition": split,
                       "file_name": row["file_name"], "image_id": row["id"],
                       "width": row["width"], "height": row["height"],
                       "group_id": f"image:{row['source_split']}:{row['id']}",
                       "patient_id": None, "file_sha256": row["file_sha256"],
                       "original_relative_path": f"data/dentex_raw/{REVISION}/{row['source_split']}/images/{row['file_name']}",
                       "annotation_ids": [annotation["id"] for annotation in row["targets"]],
                       "class_counts": dict(Counter(str(mapping[a["category_id_3"]]) for a in row["targets"]))})
    images.sort(key=lambda row: (row["partition"], row["file_name"]))
    summary = {}
    for split in ("train", "validation", "test"):
        rows = [row for row in images if row["partition"] == split]
        counts = Counter()
        for row in rows:
            counts.update(row["class_counts"])
        if not rows or any(counts[str(cls)] == 0 for cls in (0, 1)):
            raise IntegrityError("Una partición carece de soporte para ambas categorías")
        summary[split] = {"images": len(rows), "annotations_by_class": dict(counts)}
    plan = {"version": 1, "date": policy["date"], "status": "frozen_for_initial_research_experiment",
            "source_revision": REVISION, "policy_sha256": sha256(canonical_bytes(policy)).hexdigest(),
            "source_annotation_sha256": source_hashes, "seed": policy["seed"],
            "split_method": "presence_stratification_with_deterministic_sha256_rank_and_inspection_exclusions",
            "class_mapping": policy["target_mapping"], "classes": policy["target_names"],
            "summary": summary, "excluded_images": exclusions, "duplicate_annotation_actions": duplicate_actions,
            "images": images, "patient_independence_verified": False,
            "evaluation_scope": policy["cohort_limitation"], "clinical_validation_executed": False,
            "training_executed_by_preparation": False}
    return plan, eligible, policy


def apply_plan(plan, eligible, policy):
    # Si ya existe una versión congelada, una política distinta requiere otra versión.
    plan_bytes = json.dumps(plan, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
    if PARTITIONS_PATH.exists() and PARTITIONS_PATH.read_bytes() != plan_bytes:
        raise IntegrityError("Ya hay particiones congeladas distintas; no se sustituirán")
    free = shutil.disk_usage(LAB).free
    estimated = sum((RAW_ROOT / row["source_split"] / "images" / row["file_name"]).stat().st_size
                    for row in plan["images"]
                    if not (OUTPUT / "images" / row["partition"] / row["file_name"]).exists())
    if free < estimated + 10_000_000_000:
        raise IntegrityError("Falta espacio para la copia y el margen de 10 GB")
    sources = {(row["source_split"], row["file_name"]): row for row in eligible}
    derived = defaultdict(lambda: {"images": [], "annotations": [],
                                  "categories": [{"id": 0, "name": "caries"},
                                                 {"id": 1, "name": "caries_profunda"}]})
    mapping = {int(key): value for key, value in policy["target_mapping"].items()}
    for index, row in enumerate(plan["images"], 1):
        original = contained(LAB / row["original_relative_path"], RAW_ROOT)
        raw = original.read_bytes()
        if sha256(raw).hexdigest() != row["file_sha256"]:
            raise IntegrityError("Una imagen original cambió desde la auditoría")
        split = row["partition"]
        write_new_or_same(OUTPUT / "images" / split / row["file_name"], raw, OUTPUT)
        annotations = sources[(row["source_split"], row["file_name"])]["targets"]
        labels = []
        for annotation in annotations:
            x, y, width, height = annotation["bbox"]
            normalized = ((x + width / 2) / row["width"], (y + height / 2) / row["height"],
                          width / row["width"], height / row["height"])
            if not all(math.isfinite(value) and 0 < value <= 1 for value in normalized):
                raise IntegrityError("Cuadro inválido durante la conversión")
            category = mapping[annotation["category_id_3"]]
            labels.append(f"{category} " + " ".join(f"{value:.10f}" for value in normalized))
            derived[split]["annotations"].append({**annotation, "category_id": category,
                                                   "source_category_id_3": annotation["category_id_3"]})
        content = ("\n".join(labels) + "\n").encode("ascii")
        write_new_or_same(OUTPUT / "labels" / split / (Path(row["file_name"]).stem + ".txt"), content, OUTPUT)
        derived[split]["images"].append({"id": row["image_id"], "file_name": row["file_name"],
                                         "width": row["width"], "height": row["height"]})
        if index % 100 == 0 or index == len(plan["images"]):
            print(json.dumps({"prepared_images": index, "expected": len(plan["images"])}), flush=True)
    for split, data in derived.items():
        write_new_or_same(OUTPUT / "annotations" / f"{split}.json",
                          json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8") + b"\n", OUTPUT)
    yaml = (f"path: {json.dumps(str(OUTPUT.resolve()).replace(chr(92), '/'))}\n"
            "train: images/train\nval: images/validation\ntest: images/test\n"
            "names:\n  0: caries\n  1: caries_profunda\n")
    write_new_or_same(OUTPUT / "dataset.yaml", yaml.encode("utf-8"), OUTPUT)
    # El manifiesto se publica solo después de completar y comprobar la copia.
    write_new_or_same(PARTITIONS_PATH, plan_bytes, LAB / "preparacion")
    receipt = {"version": 1, "policy_sha256": plan["policy_sha256"],
               "partitions_sha256": sha256(plan_bytes).hexdigest(), "status": "complete",
               "prepared_images": len(plan["images"]), "summary": plan["summary"],
               "originals_modified": False, "model_selected": False, "training_executed": False}
    write_new_or_same(OUTPUT / "recibo.preparacion.json",
                      json.dumps(receipt, ensure_ascii=False, indent=2).encode("utf-8") + b"\n", OUTPUT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aplicar", action="store_true", help="Crear copia y congelar las particiones verificadas")
    args = parser.parse_args()
    plan, eligible, policy = make_plan()
    print(json.dumps({"mode": "apply" if args.aplicar else "plan_only", "summary": plan["summary"],
                      "excluded_images": len(plan["excluded_images"]),
                      "duplicate_annotation_actions": plan["duplicate_annotation_actions"]}), flush=True)
    if args.aplicar:
        apply_plan(plan, eligible, policy)
        print(json.dumps({"status": "complete", "partitions": str(PARTITIONS_PATH),
                          "dataset": str(OUTPUT), "training_executed": False}), flush=True)


if __name__ == "__main__":
    main()
