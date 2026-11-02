"""Local Whisper transcription with the same audio and resource limits as CoroVoz v4."""

import io
import json
import math
import os
import subprocess
import tempfile
import threading
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAX_AUDIO_BYTES = 960_044
MODEL = Path(os.getenv("COROVOZ_MODEL_PATH", ROOT / ".corovoz/whisper/ggml-base-q5_1.bin"))
COMMAND = Path(os.getenv("COROVOZ_WHISPER_COMMAND", ROOT / ".corovoz/whisper/runtime/Release/whisper-cli.exe"))
GATE = threading.BoundedSemaphore(1)


class SpeechError(Exception):
    def __init__(self, status, message):
        self.status = status
        super().__init__(message)


def installed():
    try:
        return COMMAND.is_file() and MODEL.is_file() and MODEL.stat().st_size > 50_000_000
    except OSError:
        return False


def validate_audio(wav):
    if len(wav) < 44 or len(wav) > MAX_AUDIO_BYTES:
        raise SpeechError(400, "Graba una frase de hasta 30 segundos")
    try:
        with wave.open(io.BytesIO(wav), "rb") as audio:
            if audio.getnchannels() != 1 or audio.getsampwidth() != 2 or audio.getframerate() != 16000 or audio.getcomptype() != "NONE":
                raise SpeechError(400, "Se requiere audio WAV PCM16 mono de 16 kHz")
            pcm = audio.readframes(MAX_AUDIO_BYTES)
    except SpeechError:
        raise
    except Exception as error:
        raise SpeechError(400, "El audio recibido no es WAV válido") from error
    count = len(pcm) // 2
    energy = sum((int.from_bytes(pcm[i:i + 2], "little", signed=True) / 32768) ** 2 for i in range(0, len(pcm) - 1, 2))
    if len(pcm) < 6400 or math.sqrt(energy / max(1, count)) < 0.002:
        raise SpeechError(400, "No se detectó voz suficiente. Acércate al micrófono y vuelve a hablar.")


def transcribe(wav):
    validate_audio(wav)
    if not installed():
        raise SpeechError(503, "Falta instalar Whisper local para CoroVoz.")
    if not GATE.acquire(blocking=False):
        raise SpeechError(429, "CoroVoz está procesando otro audio. Intenta nuevamente.")
    try:
        with tempfile.TemporaryDirectory(prefix="corovoz-") as folder:
            directory = Path(folder)
            source, output = directory / "input.wav", directory / "result"
            source.write_bytes(wav)
            command = [str(COMMAND), "-m", str(MODEL), "-f", str(source), "-l", "es", "-t", "4", "-nt", "-np", "-oj", "-of", str(output),
                       "--no-gpu", "--no-fallback", "--prompt", "Coronyx, CoroVoz, teleodontología, agenda, paciente, alergias, radiografías, documentos, historia clínica, odontograma. Maxilar superior, maxilar inferior. Diente número treinta y seis, pieza dieciocho. Sano, caries, restauración, restaurado, obturación, corona, extracción, extraído, endodoncia, implante, fractura. Cara oclusal, vestibular, lingual, palatina, mesial, distal. Confirmar, editar, eliminar, repetir dictado, guardar borrador."]
            try:
                subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60, check=True)
                segments = json.loads((directory / "result.json").read_text(encoding="utf-8"))["transcription"]
            except subprocess.TimeoutExpired as error:
                raise SpeechError(504, "La transcripción tardó demasiado. Dicta una frase más corta.") from error
            except (OSError, subprocess.CalledProcessError, ValueError, KeyError) as error:
                raise SpeechError(503, "No se pudo procesar el audio local. Revisa la instalación del modelo.") from error
            text = " ".join(str(part.get("text", "")) for part in segments).strip()
            text = " ".join(text.split())
            if len(text) > 600:
                raise SpeechError(400, "La frase es demasiado larga. Dicta una tarea por vez.")
            return text
    finally:
        GATE.release()
