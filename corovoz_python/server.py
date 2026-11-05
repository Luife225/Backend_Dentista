"""Loopback-only HTTP service for the Python implementation of CoroVoz."""

import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from dental import parse, revise
from knowledge import Knowledge
from luna import LunaClassifier
from patient import analyze
from speech import MAX_AUDIO_BYTES, SpeechError, installed, transcribe

KNOWLEDGE = Knowledge()
LUNA = LunaClassifier.from_environment()
TOKEN = os.getenv("COROVOZ_SERVICE_TOKEN", "")
if LUNA.enabled and not TOKEN:
    LUNA.enabled = False
    print("Luna desactivado: configura COROVOZ_SERVICE_TOKEN", flush=True)


def general_answer(text, role, selected_intent=None, user_id=None, clinic_id=None):
    result = KNOWLEDGE.answer(text, role, selected_intent)
    if selected_intent is None and result["intent"] == "UNKNOWN":
        intent = LUNA.classify(text, role, KNOWLEDGE, user_id, clinic_id)
        if intent:
            result = KNOWLEDGE.answer(text, role, intent)
    return result


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if TOKEN and not hmac.compare_digest(self.headers.get("X-CoroVoz-Token", ""), TOKEN):
            self.respond(403, {"message": "Acceso denegado"})
            return False
        return True

    def do_GET(self):
        if self.path == "/health":
            ready = installed()
            self.respond(200 if ready else 503, {"ready": ready})
            return
        if not self.authorized():
            return
        if self.path == "/status":
            self.respond(200, {"version": 4, "speechReady": installed()})
        else:
            self.respond(404, {"message": "Ruta desconocida"})

    def do_POST(self):
        if not self.authorized():
            return
        try:
            length = int(self.headers.get("Content-Length", "-1"))
            if length < 0 or length > MAX_AUDIO_BYTES + 4096:
                self.respond(413, {"message": "Solicitud demasiado grande"})
                return
            content = self.rfile.read(length)
            if self.path == "/transcribir":
                if self.headers.get("Content-Type", "").split(";", 1)[0] != "audio/wav":
                    self.respond(415, {"message": "Se requiere audio/wav"})
                    return
                self.respond(200, {"text": transcribe(content)})
                return
            data = json.loads(content)
            text = data.get("text", "")
            if not isinstance(text, str) or not text.strip() or len(text) > 600:
                self.respond(400, {"message": "Escribe una frase de hasta 600 caracteres"})
                return
            if self.path == "/general":
                self.respond(200, general_answer(text, data.get("role", ""), data.get("selectedIntent"),
                                                 data.get("userId"), data.get("clinicId")))
            elif self.path == "/patient/analyze":
                self.respond(200, analyze(text))
            elif self.path in {"/odontograma/interpretar", "/odontograma/editar"}:
                if data.get("role") != "ODONTOLOGO":
                    self.respond(403, {"message": "Rol no autorizado"})
                else:
                    self.respond(200, revise(text, data.get("field"), data.get("draft")) if self.path.endswith("/editar") else parse(text, data.get("contextPiece")))
            else:
                self.respond(404, {"message": "Ruta desconocida"})
        except SpeechError as error:
            self.respond(error.status, {"message": str(error)})
        except (ValueError, TypeError, json.JSONDecodeError):
            self.respond(400, {"message": "Solicitud inválida"})
        except PermissionError:
            self.respond(403, {"message": "Rol no autorizado"})


if __name__ == "__main__":
    host = os.getenv("COROVOZ_PYTHON_HOST", "127.0.0.1")
    if host not in {"127.0.0.1", "::1", "localhost"} and not TOKEN:
        raise SystemExit("Configura COROVOZ_SERVICE_TOKEN antes de exponer el servicio fuera de localhost")
    port = int(os.getenv("COROVOZ_PYTHON_PORT", "8787"))
    print(f"CoroVoz Python escuchando en {host}:{port}", flush=True)
    ThreadingHTTPServer((host, port), Handler).serve_forever()
