"""Microservicio FastAPI de inferencia ML para CORONYX (Local y Railway).

Endpoints expuestos:
- GET  /                -> Visor web interactivo de demostración
- GET  /docs            -> Documentación interactiva Swagger UI generada por FastAPI
- GET  /health          -> Estado del servicio, modelo cargado y dispositivo (GPU / CPU)
- POST /predict         -> Recibe radiografía PNG/JPEG (multipart 'file' o binario directo) y devuelve JSON
- POST /predict/preview -> Recibe radiografía y devuelve imagen PNG con las cajas dibujadas
"""

import argparse
from email.parser import BytesParser
from email.policy import default as default_email_policy
import os
from typing import Optional

from fastapi import FastAPI, File, Query, Request, Response, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse

from detector import PanoramicDetectorSession, render_annotated_png_bytes

DEFAULT_THRESHOLD = float(os.getenv("CORONYX_ML_THRESHOLD", "0.25"))
default_session = PanoramicDetectorSession(threshold=DEFAULT_THRESHOLD)

app = FastAPI(
    title="CORONYX ML Service — Detector de Caries en Radiografías Panorámicas",
    description=(
        "Microservicio de inferencia basado en YOLOv8n entrenado con DENTEX para la detección "
        "asistida de `caries` (clase 0) y `caries_profunda` (clase 1)."
    ),
    version="1.0.0-mvp",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

DEMO_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8" />
  <title>CORONYX — Microservicio FastAPI ML (YOLOv8n)</title>
  <style>
    body { font-family: system-ui, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
    .container { max-width: 1200px; margin: 0 auto; }
    .top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
    h1 { margin: 0; color: #38bdf8; font-size: 1.45rem; }
    .docs-link { background: #10b981; color: #052e16; text-decoration: none; padding: 8px 14px; border-radius: 6px; font-weight: 700; font-size: 0.9rem; }
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
    <div class="top-bar">
      <h1>CORONYX — Microservicio FastAPI ML (YOLOv8n · Caries y Caries Profunda)</h1>
      <a class="docs-link" href="/docs" target="_blank">Abrir Swagger UI (/docs)</a>
    </div>
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
      if (!fileInput.files.length) { alert('Selecciona primero una radiografía PNG o JPEG'); return; }
      const file = fileInput.files[0];
      status.textContent = 'Analizando radiografía...';
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


async def _extract_request_image(request: Request, file: Optional[UploadFile]) -> tuple[bytes, str]:
    """Soporta tanto multipart/form-data ('file' desde Swagger/Postman) como binario directo (desde Spring Boot)."""
    if file is not None:
        raw_bytes = await file.read()
        return raw_bytes, file.filename or "upload.png"

    content_type = request.headers.get("content-type", "")
    body = await request.body()
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

    file_name = request.headers.get("x-file-name", "radiografia.png")
    return body, file_name


@app.get("/", response_class=HTMLResponse, include_in_schema=False)
@app.get("/demo", response_class=HTMLResponse, include_in_schema=False)
def demo_page():
    return HTMLResponse(content=DEMO_HTML, status_code=200)


@app.get("/health", summary="Estado del modelo YOLOv8n y dispositivo (GPU/CPU)")
def health_check():
    model_ready = False
    error_msg = None
    try:
        default_session.load()
        model_ready = default_session.model is not None
    except Exception as err:
        error_msg = str(err)

    import torch  # noqa: PLC0415  # pyrefly: ignore[missing-import]

    status_code = 200 if model_ready else 503
    payload = {
        "service_status": "alive",
        "framework": "FastAPI",
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
    }
    return JSONResponse(content=payload, status_code=status_code)


@app.post("/predict", summary="Analiza una radiografía PNG/JPEG y devuelve hallazgos JSON")
async def predict_radiograph(
    request: Request,
    file: Optional[UploadFile] = File(
        default=None,
        description="Radiografía panorámica en formato PNG o JPEG (opcional si se envía binario directo).",
    ),
    threshold: float = Query(
        default=DEFAULT_THRESHOLD,
        ge=0.01,
        le=0.99,
        description="Umbral mínimo de confianza para reportar hallazgos (por defecto 0.25).",
    ),
):
    raw_bytes, filename = await _extract_request_image(request, file)
    result = default_session.analyze_bytes(
        raw_bytes=raw_bytes,
        file_name=filename,
        custom_threshold=threshold,
    )
    status = result.get("status")
    if status in (" analysis_completed_with_findings", "analysis_completed_with_findings", "analysis_completed_no_findings"):
        http_code = 200
    elif status == "invalid_image_error":
        http_code = 400
    elif status == "model_unavailable_error":
        http_code = 503
    else:
        http_code = 500
    return JSONResponse(content=result, status_code=http_code)


@app.post("/predict/preview", summary="Analiza una radiografía y devuelve el PNG con las cajas dibujadas")
async def predict_preview(
    request: Request,
    file: Optional[UploadFile] = File(
        default=None,
        description="Radiografía panorámica en formato PNG o JPEG.",
    ),
    threshold: float = Query(
        default=DEFAULT_THRESHOLD,
        ge=0.01,
        le=0.99,
        description="Umbral mínimo de confianza para dibujar cajas (por defecto 0.25).",
    ),
):
    raw_bytes, filename = await _extract_request_image(request, file)
    result = default_session.analyze_bytes(
        raw_bytes=raw_bytes,
        file_name=filename,
        custom_threshold=threshold,
    )
    status = result.get("status")
    if status not in ("analysis_completed_with_findings", "analysis_completed_no_findings"):
        http_code = 400 if status == "invalid_image_error" else 503
        return JSONResponse(content=result, status_code=http_code)

    annotated_png = render_annotated_png_bytes(raw_bytes, result)
    return Response(
        content=annotated_png,
        media_type="image/png",
        headers={
            "X-Findings-Count": str(result.get("findings_count", 0)),
            "X-Request-Id": str(result.get("request_id", "")),
        },
    )


def main():
    parser = argparse.ArgumentParser(
        description="Inicia el microservicio FastAPI de inferencia ML para CORONYX."
    )
    parser.add_argument("--host", default=os.getenv("HOST", "0.0.0.0"), help="Host de escucha")
    parser.add_argument(
        "--puerto",
        type=int,
        default=int(os.getenv("PORT", "8091")),
        help="Puerto HTTP (por defecto 8091 o $PORT en Railway)",
    )
    parser.add_argument(
        "--umbral",
        type=float,
        default=DEFAULT_THRESHOLD,
        help="Umbral de confianza por defecto (ej. 0.25)",
    )
    args = parser.parse_args()
    default_session.operating_threshold = args.umbral

    import uvicorn  # noqa: PLC0415

    print(f"Iniciando CORONYX FastAPI ML Service en http://{args.host}:{args.puerto}")
    print(f"Swagger UI disponible en: http://127.0.0.1:{args.puerto}/docs")
    uvicorn.run(app, host=args.host, port=args.puerto)


if __name__ == "__main__":
    main()
