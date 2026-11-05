# CORONYX — Microservicio FastAPI de Inferencia ML (`ml-service`)

Esta carpeta contiene **únicamente los archivos necesarios para producción y despliegue en Railway** del detector `coronyx-dentex-yolov8n` (`caries` y `caries_profunda`).

## Estructura

```text
ml-service/
├── app.py                  # Servidor FastAPI + Uvicorn (/health, /predict, /predict/preview, /docs)
├── detector.py             # Motor de inferencia YOLOv8n (detecta GPU local o CPU en Railway)
├── model/
│   ├── best.pt             # Pesos entrenados congelados (6.25 MB, sha256: 30acddf3...)
│   └── model_config.json   # Configuración operativa v1.0.0-mvp (umbral 0.25)
├── requirements.txt        # Dependencias ligeras para despliegue (FastAPI + PyTorch CPU)
├── Dockerfile              # Imagen lista para Railway / Docker
└── railway.json            # Configuración de healthcheck y despliegue en Railway
```

---

## 1. Cómo ejecutarlo en tu Laptop (usando tu `venv` con GPU RTX 5060)

```powershell
cd C:\Users\sebas\Desktop\CoronyxSASS\BackendCoronyx\Backend_Dentista\ml-service
..\ML_Laboratorio\venv\Scripts\python.exe app.py --puerto 8091 --umbral 0.25
```

- **Visor interactivo:** `http://127.0.0.1:8091/`
- **Swagger UI (FastAPI):** `http://127.0.0.1:8091/docs`
- **Healthcheck:** `http://127.0.0.1:8091/health`

---

## 2. Cómo desplegarlo en Railway desde GitHub

1. En tu proyecto de **Railway** (donde ya tienes PostgreSQL y el Backend de Spring Boot), haz clic en **+ New** $\rightarrow$ **GitHub Repo** y selecciona el repositorio `Backend_Dentista`.
2. Entra a **Settings** de ese nuevo servicio y en **Root Directory** escribe:
   ```text
   /ml-service
   ```
3. Railway detectará automáticamente `Dockerfile` y `railway.json`, instalará FastAPI + `best.pt` y verificará `/health`.
4. En el servicio de **Spring Boot** en Railway, agrega la variable de entorno:
   ```text
   CORONYX_ML_URL=http://${{ml-service.RAILWAY_PRIVATE_DOMAIN}}:8091
   ```
   *(O la URL pública `https://...up.railway.app` generada por Railway para el servicio ML).*

