"""Ejecución controlada de Fase D (D1 prueba corta / D2 entrenamiento de referencia).

Por defecto solo muestra y valida la configuración prevista sin descargar pesos ni entrenar.
Al ejecutar --ejecutar humo o --ejecutar referencia:
- Utiliza exclusivamente las particiones train y validation (genera un YAML sin ruta a test).
- Registra el hash SHA-256 de los pesos iniciales y de los checkpoints generados.
- Desactiva aumentos que distorsionen la anatomía panorámica (mosaic=0, flipud=0, fliplr=0).
"""

import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import shutil
import subprocess
import time

from .adquirir_dentex import LAB, IntegrityError
from .preparar_dataset import OUTPUT as DATASET_DIR, PARTITIONS_PATH, POLICY_PATH
from .verificar_entorno_gpu import verify_gpu_environment
from .verificar_preparacion import verify as verify_dataset

CONFIG_PATH = LAB / "CONFIG_ENTRENAMIENTO_2026-10-28.json"
MODELS_DIR = LAB / "models"
RUNS_DIR = LAB / "runs"


def file_sha256(path):
    return sha256(Path(path).read_bytes()).hexdigest()


def current_commit():
    return subprocess.check_output(
        ["git", "-C", str(LAB), "rev-parse", "HEAD"], text=True
    ).strip()


def write_train_val_only_yaml():
    """Garantiza que Ultralytics solo conozca train y validation durante Fase D."""
    yaml_path = DATASET_DIR / "dataset_train_val.yaml"
    root_str = str(DATASET_DIR.resolve()).replace("\\", "/")
    content = (
        f"path: {json.dumps(root_str)}\n"
        "train: images/train\n"
        "val: images/validation\n"
        "names:\n"
        "  0: caries\n"
        "  1: caries_profunda\n"
    )
    yaml_path.write_text(content, encoding="utf-8")
    return yaml_path


def ensure_base_weights(weights_name):
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    target = MODELS_DIR / weights_name
    from ultralytics import YOLO  # noqa: PLC0415

    # Si no existe aún en models/, YOLO lo descarga y lo movemos a models/
    if not target.is_file():
        local_root_copy = LAB / weights_name
        model = YOLO(weights_name)
        if local_root_copy.is_file():
            shutil.move(str(local_root_copy), str(target))
        elif Path(weights_name).is_file():
            shutil.copy2(weights_name, target)
        else:
            ckpt_path = getattr(model, "ckpt_path", None)
            if ckpt_path and Path(ckpt_path).is_file():
                shutil.copy2(ckpt_path, target)
    if not target.is_file():
        raise IntegrityError(f"No se pudo localizar el peso base {weights_name}")
    return target, file_sha256(target)


def run_training(mode, epochs_override=None, batch_override=None, imgsz_override=None):
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    dataset_status = verify_dataset()
    gpu_status = verify_gpu_environment()
    if not gpu_status["ready_for_phase_d_when_authorized"]:
        raise RuntimeError("El entorno GPU no está verificado para iniciar Fase D")

    hp = config["training_hyperparameters"]
    aug = config["preprocessing_and_augmentations"]
    epochs = epochs_override or (hp["smoke_epochs"] if mode == "humo" else hp["reference_epochs"])
    batch = batch_override or hp["batch"]
    imgsz = imgsz_override or hp["imgsz"]

    if mode is None:
        print(
            json.dumps(
                {
                    "mode": "plan_only_no_training_executed",
                    "experiment_id": config["experiment_id"],
                    "dataset_verified": dataset_status["status"] == "verified",
                    "gpu_ready": gpu_status["ready_for_phase_d_when_authorized"],
                    "gpu_device": gpu_status["cuda"]["device_name"],
                    "planned_smoke_epochs": hp["smoke_epochs"],
                    "planned_reference_epochs": hp["reference_epochs"],
                    "planned_imgsz": imgsz,
                    "planned_batch": batch,
                },
                ensure_ascii=True,
                indent=2,
            )
        )
        return

    train_val_yaml = write_train_val_only_yaml()
    weights_path, weights_sha256 = ensure_base_weights(config["model"]["pretrained_weights_name"])
    run_name = f"d1_humo_{hp['seed']}" if mode == "humo" else f"d2_referencia_{hp['seed']}"

    import torch  # noqa: PLC0415
    from ultralytics import YOLO  # noqa: PLC0415

    torch.cuda.reset_peak_memory_stats(0)
    started_utc = datetime.now(timezone.utc).isoformat()
    t0 = time.perf_counter()

    model = YOLO(str(weights_path))
    model.train(
        data=str(train_val_yaml),
        project=str(RUNS_DIR / "detect"),
        name=run_name,
        exist_ok=True,
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        device=0,
        workers=hp["workers"],
        seed=hp["seed"],
        deterministic=hp["deterministic"],
        patience=hp["patience"],
        optimizer=hp["optimizer"],
        lr0=hp["lr0"],
        lrf=hp["lrf"],
        val=True,
        plots=True,
        save=True,
        mosaic=aug["mosaic"],
        mixup=aug["mixup"],
        copy_paste=aug["copy_paste"],
        degrees=aug["degrees"],
        flipud=aug["flipud"],
        fliplr=aug["fliplr"],
        translate=aug["translate"],
        scale=aug["scale"],
        hsv_h=aug["hsv_h"],
        hsv_s=aug["hsv_s"],
        hsv_v=aug["hsv_v"],
        erasing=aug["erasing"],
    )

    elapsed_seconds = round(time.perf_counter() - t0, 2)
    peak_vram_mib = round(torch.cuda.max_memory_allocated(0) / (1024 ** 2), 2)
    save_dir = RUNS_DIR / "detect" / run_name
    best_pt = save_dir / "weights" / "best.pt"
    last_pt = save_dir / "weights" / "last.pt"

    receipt = {
        "phase": "D1_smoke_check" if mode == "humo" else "D2_reference_training",
        "experiment_id": config["experiment_id"],
        "started_at_utc": started_utc,
        "completed_at_utc": datetime.now(timezone.utc).isoformat(),
        "elapsed_seconds": elapsed_seconds,
        "git_commit": current_commit(),
        "source_revision": config["source_revision"],
        "policy_sha256": file_sha256(POLICY_PATH),
        "partitions_sha256": file_sha256(PARTITIONS_PATH),
        "training_config_sha256": file_sha256(CONFIG_PATH),
        "initial_weights": {
            "path": str(weights_path),
            "sha256": weights_sha256,
        },
        "environment": gpu_status["packages"],
        "gpu": {
            "device_name": gpu_status["cuda"]["device_name"],
            "compute_capability": gpu_status["cuda"]["compute_capability"],
            "peak_allocated_vram_mib": peak_vram_mib,
        },
        "effective_parameters": {
            "epochs": epochs,
            "imgsz": imgsz,
            "batch": batch,
            "seed": hp["seed"],
            "augmentations": aug,
            "dataset_yaml_used": str(train_val_yaml),
            "test_partition_touched": False,
        },
        "Checkpoints": {
            "save_dir": str(save_dir),
            "best_pt_path": str(best_pt) if best_pt.is_file() else None,
            "best_pt_sha256": file_sha256(best_pt) if best_pt.is_file() else None,
            "last_pt_path": str(last_pt) if last_pt.is_file() else None,
            "last_pt_sha256": file_sha256(last_pt) if last_pt.is_file() else None,
        },
    }

    receipt_name = "recibo_humo_d1.json" if mode == "humo" else "recibo_entrenamiento_d2.json"
    receipt_path = RUNS_DIR / receipt_name
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=True, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--ejecutar",
        choices=("humo", "referencia"),
        default=None,
        help="Ejecuta D1 (humo, 2 épocas) o D2 (referencia completa, 40 épocas).",
    )
    parser.add_argument("--epocas", type=int, default=None, help="Sobrescribe el número de épocas.")
    parser.add_argument("--batch", type=int, default=None, help="Sobrescribe el tamaño de batch.")
    parser.add_argument("--imgsz", type=int, default=None, help="Sobrescribe la resolución de entrada.")
    args = parser.parse_args()
    run_training(args.ejecutar, args.epocas, args.batch, args.imgsz)


if __name__ == "__main__":
    main()

