"""Install the pinned local Whisper model and Windows runtime with SHA-256 checks."""

import hashlib
import os
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / ".corovoz" / "whisper"
MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
MODEL_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
RUNTIME_URL = "https://github.com/ggml-org/whisper.cpp/releases/download/b5130/whisper-bin-x64.zip"
RUNTIME_SHA = "f9ec6c52a2e949b62ab51fa21d0d497958f9e41c3010c157c4e42932d5316f3c"


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(32768), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url, path, expected):
    if path.is_file() and checksum(path) == expected:
        return
    partial = path.with_name(path.name + ".part")
    try:
        with urllib.request.urlopen(url, timeout=240) as source, partial.open("wb") as output:
            total = 0
            for chunk in iter(lambda: source.read(32768), b""):
                total += len(chunk)
                if total > 180_000_000:
                    raise ValueError("La descarga supera el tamaño esperado")
                output.write(chunk)
        if checksum(partial) != expected:
            raise ValueError("La descarga no coincide con el SHA256 fijado")
        partial.replace(path)
    finally:
        partial.unlink(missing_ok=True)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    download(MODEL_URL, ROOT / "ggml-base-q5_1.bin", MODEL_SHA)
    if os.name != "nt":
        print("Modelo listo. Instala whisper-cli y configura COROVOZ_WHISPER_COMMAND.")
        return
    executable = ROOT / "runtime" / "Release" / "whisper-cli.exe"
    if not executable.is_file():
        archive = ROOT / "runtime.zip"
        download(RUNTIME_URL, archive, RUNTIME_SHA)
        runtime = ROOT / "runtime"
        runtime.mkdir(parents=True, exist_ok=True)
        total = 0
        try:
            with zipfile.ZipFile(archive) as bundle:
                for entry in bundle.infolist():
                    target = (runtime / entry.filename).resolve()
                    if not target.is_relative_to(runtime.resolve()):
                        raise ValueError("Ruta no permitida en el archivo de instalación")
                    total += entry.file_size
                    if total > 150_000_000:
                        raise ValueError("El ejecutable supera el tamaño esperado")
                    if entry.is_dir():
                        target.mkdir(parents=True, exist_ok=True)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with bundle.open(entry) as source, target.open("wb") as output:
                            for chunk in iter(lambda: source.read(32768), b""):
                                output.write(chunk)
        finally:
            archive.unlink(missing_ok=True)
    if not executable.is_file():
        raise RuntimeError("Instalación incompleta")
    print("CoroVoz listo: reconocimiento local.")


if __name__ == "__main__":
    main()
