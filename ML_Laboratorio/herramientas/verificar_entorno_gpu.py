"""Verifica de forma reproducible el entorno Python, PyTorch, CUDA y Ultralytics.

No descarga pesos preentrenados, no accede a la red y no inicia entrenamiento.
"""

from datetime import datetime, timezone
from importlib import metadata
import json
from pathlib import Path
import platform
import shutil
import sys

from .adquirir_dentex import LAB

REPORT_PATH = LAB / "auditoria" / "ENTORNO_GPU_2026-10-28.json"


def package_version(name):
    try:
        return metadata.version(name)
    except metadata.PackageNotFoundError:
        return None


def verify_gpu_environment():
    in_venv = sys.prefix != getattr(sys, "base_prefix", sys.prefix)
    report = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "os_platform": platform.platform(),
        "python_version": sys.version.split()[0],
        "python_executable": sys.executable,
        "isolated_virtualenv": in_venv,
        "free_disk_bytes": shutil.disk_usage(LAB).free,
        "packages": {
            "Pillow": package_version("Pillow"),
            "torch": package_version("torch"),
            "torchvision": package_version("torchvision"),
            "ultralytics": package_version("ultralytics"),
        },
        "cuda": {
            "available": False,
            "runtime_version": None,
            "device_count": 0,
            "device_name": None,
            "compute_capability": None,
            "total_vram_mib": None,
            "tensor_matmul_verified": False,
        },
        "ultralytics_import_verified": False,
        "weights_downloaded": False,
        "training_executed": False,
    }

    import torch  # noqa: PLC0415

    report["cuda"]["available"] = bool(torch.cuda.is_available())
    report["cuda"]["runtime_version"] = torch.version.cuda
    report["cuda"]["device_count"] = int(torch.cuda.device_count())

    if report["cuda"]["available"] and report["cuda"]["device_count"] > 0:
        props = torch.cuda.get_device_properties(0)
        cap = torch.cuda.get_device_capability(0)
        report["cuda"]["device_name"] = torch.cuda.get_device_name(0)
        report["cuda"]["compute_capability"] = f"{cap[0]}.{cap[1]}"
        report["cuda"]["total_vram_mib"] = round(props.total_memory / (1024 ** 2))

        tensor_a = torch.tensor([[1.0, 2.0], [3.0, 4.0]], device="cuda:0")
        tensor_b = tensor_a @ tensor_a
        torch.cuda.synchronize()
        report["cuda"]["tensor_matmul_verified"] = (
            tensor_b.cpu().tolist() == [[7.0, 10.0], [15.0, 22.0]]
        )

    from ultralytics import YOLO  # noqa: PLC0415

    report["ultralytics_import_verified"] = callable(YOLO)
    report["ready_for_phase_d_when_authorized"] = bool(
        in_venv
        and report["cuda"]["available"]
        and report["cuda"]["tensor_matmul_verified"]
        and report["ultralytics_import_verified"]
    )
    return report


def main():
    report = verify_gpu_environment()
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=True, indent=2))
    if not report["ready_for_phase_d_when_authorized"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

