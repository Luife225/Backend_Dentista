# CORONYX: Documentación de Recursos y Decisión de Despliegue de ML (Sección 23)

- **Fecha:** 2026-10-31
- **Proyecto:** CORONYX — Sistema Integral de Gestión Odontológica con IA Asistida
- **Módulo:** Detección de Caries y Caries Profunda en Radiografías Panorámicas (DENTEX MVP v1)
- **Modelo:** YOLOv8n (`best.pt`, 6.5 MB)
- **Rama:** `mvp-ml`

---

## 1. Resumen Ejecutivo de Recursos Medidos

A partir de las pruebas de validación, auditoría y latencia formal ejecutadas sobre la GPU local (`NVIDIA GeForce RTX 5060 Laptop GPU`, 8GB VRAM) y CPU, se obtuvieron las siguientes métricas de consumo y rendimiento:

| Métrica | Valor Medido | Observaciones |
|---|---|---|
| **Tamaño de pesos (`best.pt`)** | **6.25 MB** (6,248,306 bytes) | Arquitectura nano (`yolov8n`), altamente portable y transferible. |
| **VRAM consumida en inferencia** | **~312.7 MiB** | Apenas el 3.8% de los 8 GB de la RTX 5060 Laptop. |
| **RAM en proceso Python** | **~410 - 450 MB** | Incluye PyTorch 2.11 + CUDA 12.8 runtime y dependencias. |
| **Arranque en frío (Cold start)** | **1,277.7 ms** (1.28 s) | Tiempo de carga inicial del modelo y pesos en memoria. |
| **Inferencia GPU en caliente (p50)** | **9.03 ms** | Tiempo puro de cálculo en tensor cores (NVIDIA sm_120). |
| **Inferencia GPU en caliente (p95)** | **11.88 ms** | Percentil 95 en condiciones de carga. |
| **Tiempo total por radiografía (p50)** | **69.84 ms** | Incluye lectura de bytes, decodificación PNG, preproceso e inferencia. |
| **Tiempo total por radiografía (p95)** | **85.75 ms** | Latencia total de respuesta extremo a extremo. |
| **Rendimiento estimado en CPU** | **~110 - 160 ms** | Probado sin GPU mediante PyTorch CPU (completamente admisible). |

---

## 2. Análisis de Alternativas de Despliegue

### Opción 1: Instancia dedicada con GPU en la Nube (AWS EC2 g4dn / Azure NC / GCP)
* **Ventajas:** Máxima velocidad de inferencia (<15 ms), soporte para múltiples clínicas concurrentes.
* **Desventajas:** Costo elevado (~$0.50 - $1.00 USD/hora, $350 - $700 USD/mes), sobredimensionado para una carga académica o clínica pequeña donde las radiografías se solicitan de forma esporádica (algunas por hora).

### Opción 2: Serverless GPU por demanda (RunPod / Modal / Replicate / AWS SageMaker Serverless)
* **Ventajas:** Se paga exclusivamente por segundo de inferencia activa ($0.0002/segundo).
* **Desventajas:** Penalización por arranque en frío (*cold start* de 2 a 8 segundos en aprovisionar contenedor), complejidad adicional de orquestación y credenciales en la nube.

### Opción 3: Microservicio desacoplado en CPU optimizada (ONNX Runtime / Docker en Railway / Render / DigitalOcean)
* **Ventajas:** Costo mínimo (~$5 a $15 USD/mes), aprovecha que `yolov8n` es ultra ligero (6.5 MB), sin dependencias de drivers CUDA ni GPU en el servidor, latencia de 120-160 ms que para un odontólogo en un navegador sigue siendo prácticamente instantánea.
* **Desventajas:** No apto si se requiere procesar cientos de imágenes simultáneamente por segundo.

### Opción 4: Arquitectura Híbrida Local / Sidecar (Seleccionada para el MVP)
* **Ventajas:** Aprovecha la GPU local del equipo del odontólogo o laboratorio para desarrollo y sustentación con 0 costos de nube, desacoplamiento estricto mediante contrato REST versionado (`coronyx.ml.panoramic_caries.v1`).

---

## 3. Decisión Adoptada y Justificación

### A. Para la Sustentación Académica y Demostración en Vivo (MVP)
Se adoptó la **Arquitectura Híbrida Desacoplada (Sidecar HTTP)**:
1. **Frontend:** React + Vite en puerto `5173`.
2. **Backend de Negocio:** Spring Boot 4 + PostgreSQL en puerto `8081` (maneja autenticación, historial clínico, multi-tenant y durabilidad de archivos).
3. **Motor de Inferencia ML:** Microservicio Python en puerto `8091` (`servicio_http_ml.py`) ejecutando sobre la GPU RTX 5060 Laptop con CUDA 12.8.
4. **Beneficio:** Permite demostrar al docente la inferencia en tiempo real en menos de 70 ms, sin costos de hosting y con tolerancia a fallos (si el motor ML se apaga, Spring Boot responde limpiamente con `503 Service Unavailable` sin tumbar el sistema).

### B. Hoja de Ruta para Despliegue en Producción Real
Para migrar el MVP a un entorno de producción en la nube sin incurrir en costos de GPUs dedicadas:
1. **Exportar a ONNX:** Convertir `best.pt` a formato ONNX (`model.export(format='onnx')`).
2. **Contenedor Docker CPU:** Empaquetar el microservicio con `onnxruntime` (imagen Docker de ~180 MB en vez de 3 GB de CUDA PyTorch).
3. **Alojamiento:** Desplegar en el mismo clúster de contenedores que el backend (por ejemplo en Railway o AWS ECS Fargate) consumiendo 0.5 vCPU y 512 MB de RAM.

---

## 4. Garantías de Privacidad y Cumplimiento Normativo Médico

1. **Aislamiento Multi-tenant:** Solo el odontólogo acreditado de la misma clínica puede solicitar el análisis y revisar los hallazgos de un paciente.
2. **Inmutabilidad de la Evidencia Original:** El backend guarda `originalModelOutput` de forma inalterable en `archivo_adjunto.ia_hallazgos_json`. Cualquier corrección del odontólogo se almacena en la colección `findings` y `review_history` sin sobreescribir la predicción de la IA.
3. **No alteración automática:** La inferencia no altera diagnósticos, no toca el odontograma técnico ni prescribe tratamientos. Requiere siempre la validación explícita del profesional tratante.

