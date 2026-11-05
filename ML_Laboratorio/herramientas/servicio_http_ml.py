"""Fase H: Servicio HTTP ligero de inferencia ML para CORONYX.

Expone en http://127.0.0.1:8091 (configurable con --puerto):
- GET  /health           -> Distingue proceso activo de modelo disponible y GPU activa.
- POST /predict          -> Recibe imagen (multipart/form-data campo 'file' o binario directo)
                            y devuelve el contrato JSON coronyx.ml.panoramic_caries.v1.
- POST /predict/preview  -> Recibe imagen y devuelve directamente el PNG con las cajas dibujadas
                            (ideal para visualizar al instante en Postman).
"""

import argparse
from email.parser import BytesParser
from email.policy import default as default_email_policy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
import tempfile
from urllib.parse import parse_qs, urlparse

from PIL import Image, ImageDraw

from .inferir_radiografia import MAX_FILE_BYTES, PanoramicDetectorSession


def extract_uploaded_bytes(headers, body):
    """Soporta tanto Body -> binary como Body -> form-data (campo 'file') desde Postman."""
    content_type = headers.get("Content-Type", "")
    if "multipart/form-data" in content_type:
        raw_mime = (
            f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode("utf-8")
            + body
        )
        msg = BytesParser(policy=default_email_policy).parsebytes(raw_mime)
        for part in msg.iter_parts():
            filename = part.get_filename()
            name = part.get_param("name", header="content-disposition")
            if filename or name in ("file", "imagen", "radiografia"):
                payload = part.get_payload(decode=True)
                return payload or b"", filename or "upload.png"
        return b"", "empty_multipart"
    filename = headers.get("X-File-Name", "radiografia_postman.png")
    return body, filename


def render_annotated_png_bytes(raw_bytes, response_payload):
    """Dibuja las cajas predichas en memoria y devuelve los bytes PNG sin dejar archivos temporales."""
    with Image.open(BytesIO(raw_bytes)) as img:
        canvas = img.convert("RGB")
    draw = ImageDraw.Draw(canvas)
    colors = {0: (255, 190, 0), 1: (239, 68, 68)}
    for item in response_payload.get("findings", []):
        b = item["bbox_pixels"]
        color = colors.get(item["class_id"], (0, 200, 255))
        coords = [b["xMin"], b["yMin"], b["xMax"], b["yMax"]]
        draw.rectangle(coords, outline=color, width=5)
        label = f"{item['class_name']} ({item['score']:.2f})"
        text_y = max(0.0, b["yMin"] - 20)
        draw.rectangle(
            [b["xMin"], text_y, b["xMin"] + len(label) * 7 + 8, text_y + 18],
            fill=(20, 20, 20),
        )
        draw.text((b["xMin"] + 4, text_y + 2), label, fill=color)
    out = BytesIO()
    canvas.save(out, format="PNG")
    return out.getvalue()


DEMO_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8" />
  <title>CORONYX — Laboratorio ML (YOLOv8n DENTEX)</title>
  <style>
    body { font-family: system-ui, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
    .container { max-width: 1200px; margin: 0 auto; }
    h1 { margin-top: 0; color: #38bdf8; font-size: 1.5rem; }
    .card { background: #1e293b; border: 1px solid #334155; border-radius: 10px; padding: 18px; margin-bottom: 18px; }
    .controls { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
    input[type="file"], input[type="number"] { background: #0f172a; color: #e2e8f0; border: 1px solid #475569; padding: 8px 12px; border-radius: 6px; }
    button { background: #0284c7; color: white; border: none; padding: 10px 18px; border-radius: 6px; font-weight: 600; cursor: pointer; }
    button:hover { background: #0369a1; }
    .grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 18px; }
    img { width: 100%; border-radius: 8px; border: 1px solid #475569; background: #000; }
    table { width: 100%; border-collapse: collapse; font-size: 0.9rem; margin-top: 8px; }
    th, td { border-bottom: 1px solid #334155; padding: 8px; text-align: left; }
    .badge-0 { background: #f59e0b; color: #000; padding: 2px 8px; border-radius: 4px; font-weight: 700; }
    .badge-1 { background: #ef4444; color: #fff; padding: 2px 8px; border-radius: 4px; font-weight: 700; }
    pre { background: #0f172a; padding: 12px; border-radius: 6px; overflow: auto; max-height: 280px; font-size: 0.8rem; }
  </style>
</head>
<body>
  <div class="container">
    <h1>CORONYX — Demostración de Inferencia ML (YOLOv8n · Caries y Caries Profunda)</h1>
    <div class="card controls">
      <label>Radiografía PNG/JPEG: <input type="file" id="fileInput" accept="image/png,image/jpeg" /></label>
      <label>Umbral de confianza: <input type="number" id="threshInput" value="0.25" min="0.05" max="0.95" step="0.05" style="width:70px" /></label>
      <button onclick="runAnalysis()">Analizar Radiografía</button>
      <span id="statusText" style="color:#94a3b8">Listo para analizar.</span>
    </div>
    <div class="grid">
      <div class="card">
        <h3>Radiografía con Cajas Detectadas (<span style="color:#f59e0b">■ Caries</span> / <span style="color:#ef4444">■ Caries profunda</span>)</h3>
        <img id="previewImg" alt="Sube una radiografía y presiona Analizar" />
      </div>
      <div class="card">
        <h3>Hallazgos Detectados (<span id="countSpan">0</span>)</h3>
        <table>
          <thead><tr><th>ID</th><th>Clase</th><th>Confianza</th><th>Coordenadas (px)</th><th>Estado</th></tr></thead>
          <tbody id="findingsBody"></tbody>
        </table>
        <h4>Respuesta JSON (/predict)</h4>
        <pre id="jsonOutput">{}</pre>
      </div>
    </div>
  </div>
  <script>
    async function runAnalysis() {
      const fileInput = document.getElementById('fileInput');
      const thresh = document.getElementById('threshInput').value || '0.25';
      const status = document.getElementById('statusText');
      if (!fileInput.files.length) { alert('Selecciona primero una radiografía (por ejemplo original_val_10.png)'); return; }
      const file = fileInput.files[0];
      status.textContent = 'Analizando en GPU RTX 5060...';
      const form1 = new FormData(); form1.append('file', file);
      const form2 = new FormData(); form2.append('file', file);
      const [jsonRes, imgRes] = await Promise.all([
        fetch('/predict?threshold=' + encodeURIComponent(thresh), { method: 'POST', body: form1 }),
        fetch('/predict/preview?threshold=' + encodeURIComponent(thresh), { method: 'POST', body: form2 })
      ]);
      const data = await jsonRes.json();
      const blob = await imgRes.blob();
      document.getElementById('previewImg').src = URL.createObjectURL(blob);
      document.getElementById('jsonOutput').textContent = JSON.stringify(data, null, 2);
      document.getElementById('countSpan').textContent = data.findings_count || 0;
      status.textContent = `Completado en ${data.timing_ms ? data.timing_ms.total_wall_ms : '?'} ms (${data.findings_count} hallazgos)`;
      const tbody = document.getElementById('findingsBody');
      tbody.innerHTML = '';
      (data.findings || []).forEach(f => {
        const b = f.bbox_pixels;
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${f.finding_id}</td>
          <td><span class="badge-${f.class_id}">${f.class_name}</span></td>
          <td>${(f.score * 100).toFixed(1)}%</td>
          <td>[${Math.round(b.xMin)}, ${Math.round(b.yMin)}, ${Math.round(b.xMax)}, ${Math.round(b.yMax)}]</td>
          <td>${f.review_status}</td>`;
        tbody.appendChild(tr);
      });
    }
  </script>
</body>
</html>
"""


def make_handler(default_session):
    class MLServiceHandler(BaseHTTPRequestHandler):
        def _send_json(self, status_code, payload):
            raw = (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path in ("/", "/demo"):
                raw = DEMO_HTML.encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
                return
            if parsed.path == "/health":
                model_ready = False
                error_msg = None
                try:
                    default_session.load()
                    model_ready = default_session.model is not None
                except Exception as err:
                    error_msg = str(err)

                import torch  # noqa: PLC0415

                status_code = 200 if model_ready else 503
                self._send_json(
                    status_code,
                    {
                        "service_status": "alive",
                        "model_ready": model_ready,
                        "model_name": default_session.model_name,
                        "model_version": default_session.model_version,
                        "weights_path": str(default_session.weights_path),
                        "weights_sha256": default_session.weights_sha256,
                        "default_operating_threshold": default_session.operating_threshold,
                        "device": (
                            torch.cuda.get_device_name(0)
                            if torch.cuda.is_available()
                            else "CPU"
                        ),
                        "error": error_msg,
                    },
                )
                return

            self._send_json(
                404,
                {
                    "error": "NOT_FOUND",
                    "available_endpoints": [
                        "GET /health",
                        "POST /predict",
                        "POST /predict/preview",
                    ],
                },
            )

        def do_POST(self):
            parsed = urlparse(self.path)
            if parsed.path not in ("/predict", "/predict/preview"):
                self._send_json(404, {"error": "NOT_FOUND"})
                return

            content_length = int(self.headers.get("Content-Length", "0"))
            if content_length <= 0 or content_length > MAX_FILE_BYTES + 1024 * 1024:
                self._send_json(
                    400,
                    {
                        "status": "invalid_image_error",
                        "error": {
                            "code": "INVALID_CONTENT_LENGTH",
                            "message": "El cuerpo de la petición está vacío o excede el tamaño máximo.",
                        },
                    },
                )
                return

            body = self.rfile.read(content_length)
            raw_bytes, file_name = extract_uploaded_bytes(self.headers, body)

            # Permitir umbral opcional por query param (?threshold=0.25) para pruebas en Postman
            query = parse_qs(parsed.query)
            custom_threshold = query.get("threshold", [None])[0]
            original_threshold = default_session.operating_threshold
            try:
                if custom_threshold is not None:
                    val = float(custom_threshold)
                    if not (0.01 <= val <= 0.99):
                        raise ValueError("threshold debe estar entre 0.01 y 0.99")
                    default_session.operating_threshold = val
                payload = default_session.analyze_bytes(raw_bytes, file_name=file_name)
            except ValueError as err:
                self._send_json(
                    400,
                    {
                        "status": "invalid_parameter_error",
                        "error": {"code": "INVALID_THRESHOLD", "message": str(err)},
                    },
                )
                return
            finally:
                default_session.operating_threshold = original_threshold

            status_map = {
                "analysis_completed_with_findings": 200,
                "analysis_completed_no_findings": 200,
                "invalid_image_error": 400,
                "model_unavailable_error": 503,
                "inference_runtime_error": 500,
            }
            http_code = status_map.get(payload["status"], 500)

            if parsed.path == "/predict/preview" and http_code == 200:
                png_out = render_annotated_png_bytes(raw_bytes, payload)
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(png_out)))
                self.send_header("X-Coronyx-Request-Id", payload["request_id"])
                self.send_header("X-Coronyx-Findings-Count", str(payload["findings_count"]))
                self.end_headers()
                self.wfile.write(png_out)
                return

            self._send_json(http_code, payload)

        def log_message(self, fmt, *args):
            print(f"[CORONYX-ML-HTTP] {self.address_string()} - {fmt % args}", flush=True)

    return MLServiceHandler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", type=str, default="127.0.0.1", help="Host de escucha (por defecto 127.0.0.1).")
    parser.add_argument("--puerto", type=int, default=8091, help="Puerto HTTP (por defecto 8091).")
    parser.add_argument("--pesos", type=str, default=None, help="Ruta opcional a best.pt.")
    parser.add_argument("--umbral", type=float, default=None, help="Umbral operativo por defecto.")
    args = parser.parse_args()

    session = PanoramicDetectorSession(weights_path=args.pesos, threshold=args.umbral)
    session.load()
    server = ThreadingHTTPServer((args.host, args.puerto), make_handler(session))
    print(
        json.dumps(
            {
                "status": "listening",
                "url": f"http://{args.host}:{args.puerto}",
                "endpoints": {
                    "health": f"http://{args.host}:{args.puerto}/health",
                    "predict_json": f"http://{args.host}:{args.puerto}/predict?threshold=0.25",
                    "predict_preview_image": f"http://{args.host}:{args.puerto}/predict/preview?threshold=0.25",
                },
                "model_loaded": str(session.weights_path),
                "weights_sha256": session.weights_sha256,
            },
            ensure_ascii=True,
            indent=2,
        ),
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()

