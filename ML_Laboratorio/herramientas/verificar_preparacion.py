"""Comprueba la copia, las cajas convertidas y la reserva sin entrenar."""

from collections import Counter
from hashlib import sha256
import json

from .adquirir_dentex import LAB, IntegrityError
from .preparar_dataset import OUTPUT, PARTITIONS_PATH, make_plan


def verify():
    expected, eligible, policy = make_plan()
    actual = json.loads(PARTITIONS_PATH.read_text(encoding="utf-8"))
    if actual != expected:
        raise IntegrityError("Las particiones no coinciden con la política y la fuente")
    sources = {(row["source_split"], row["file_name"]): row for row in eligible}
    mapping = {int(key): value for key, value in policy["target_mapping"].items()}
    partitions = {}
    for split in ("train", "validation", "test"):
        rows = [row for row in actual["images"] if row["partition"] == split]
        expected_images = {row["file_name"] for row in rows}
        actual_images = {path.name for path in (OUTPUT / "images" / split).glob("*.png")}
        expected_labels = {name.rsplit(".", 1)[0] + ".txt" for name in expected_images}
        actual_labels = {path.name for path in (OUTPUT / "labels" / split).glob("*.txt")}
        if expected_images != actual_images or expected_labels != actual_labels:
            raise IntegrityError("Faltan o sobran imágenes/etiquetas en la copia")
        counts, annotation_ids = Counter(), []
        for row in rows:
            original = LAB / row["original_relative_path"]
            copied = OUTPUT / "images" / split / row["file_name"]
            if any(sha256(path.read_bytes()).hexdigest() != row["file_sha256"] for path in (original, copied)):
                raise IntegrityError("Una imagen fuente o derivada fue modificada")
            targets = sources[(row["source_split"], row["file_name"])]["targets"]
            label = OUTPUT / "labels" / split / (copied.stem + ".txt")
            lines = label.read_text(encoding="ascii").splitlines()
            if len(lines) != len(targets):
                raise IntegrityError("Se perdieron o añadieron cajas en la conversión")
            for line, target in zip(lines, targets):
                tokens = line.split()
                if len(tokens) != 5 or int(tokens[0]) != mapping[target["category_id_3"]]:
                    raise IntegrityError("Categoría o formato convertido incorrectos")
                center_x, center_y, width, height = map(float, tokens[1:])
                reconstructed = [(center_x - width / 2) * row["width"],
                                 (center_y - height / 2) * row["height"],
                                 width * row["width"], height * row["height"]]
                if max(abs(left - right) for left, right in zip(reconstructed, target["bbox"])) > 0.00001:
                    raise IntegrityError("El cuadro convertido no recupera sus coordenadas originales")
                counts[str(int(tokens[0]))] += 1
                annotation_ids.append(target["id"])
        coco = json.loads((OUTPUT / "annotations" / f"{split}.json").read_text(encoding="utf-8"))
        if sorted(annotation_ids) != sorted(annotation["id"] for annotation in coco["annotations"]):
            raise IntegrityError("Las vistas COCO y YOLO no conservan las mismas anotaciones")
        if counts != Counter(actual["summary"][split]["annotations_by_class"]):
            raise IntegrityError("Conteos por categoría distintos del manifiesto")
        partitions[split] = {"images": len(rows), "annotations_by_class": dict(counts)}
    return {"status": "verified", "partitions": partitions, "images_verified": len(actual["images"]),
            "excluded_images": len(actual["excluded_images"]), "originals_unchanged": True,
            "source_to_yolo_box_error_tolerance_pixels": 0.00001,
            "patient_independence_verified": False, "training_executed": False}


if __name__ == "__main__":
    print(json.dumps(verify(), ensure_ascii=True, indent=2))
