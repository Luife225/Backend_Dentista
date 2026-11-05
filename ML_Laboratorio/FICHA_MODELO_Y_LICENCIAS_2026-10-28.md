# CORONYX — ficha de selección de modelo, licencias y entorno GPU (Fase C)

**Fecha:** 28/10/2026, hora de Perú.  
**Estado:** candidato seleccionado, licencias revisadas y entorno GPU verificado en laptop; **entrenamiento y descarga de pesos aún no ejecutados** (sujetos a autorización explícita de Fase D).

---

## 1. Definición de la tarea y alcance del detector

- **Tarea técnica:** detección de objetos mediante cuadros delimitadores (*bounding boxes*) de **dos clases** sobre radiografías panorámicas dentales en formato PNG/JPEG:
  - Clase `0`: `caries` (procedente de `category_id_3 = 1` en DENTEX).
  - Clase `1`: `caries_profunda` (procedente de `category_id_3 = 3` en DENTEX).
- **Interpretación de las cajas:** cada caja delimita un **diente anotado con la condición correspondiente** según las anotaciones diagnósticas de DENTEX. No representa segmentación por píxel del contorno de la lesión ni numeración dental automática (FDI).
- **Uso en CORONYX (RF-15 / RF-16):** generación de propuestas visuales con puntuación de confianza para revisión obligatoria por el odontólogo (`PENDIENTE` → `ACEPTADO`, `RECHAZADO` o `CORREGIDO`), conservando siempre la imagen original y la predicción inicial del modelo.

---

## 2. Selección del modelo candidato: `yolov8n` (YOLOv8 nano)

- **Candidato seleccionado para la línea base:** **Ultralytics YOLOv8 nano (`yolov8n`)**.
- **Justificación técnica:**
  1. **Adecuación al tamaño del conjunto:** la copia experimental fijada ([PARTICIONES_DENTEX_V1.json](preparacion/PARTICIONES_DENTEX_V1.json)) cuenta con `508` imágenes de entrenamiento, `44` de validación y `127` de prueba interna. Un modelo de escala *nano* (~3,2 millones de parámetros) reduce el riesgo de sobreajuste frente a variantes grandes y permite ciclos de entrenamiento y validación reproducibles en una GPU de 8 GB de VRAM.
  2. **Compatibilidad directa con la preparación existente:** consume de forma nativa las etiquetas normalizadas (`clase centro_x centro_y ancho alto`) y el archivo `dataset.yaml` generados por `herramientas.preparar_dataset`.
  3. **Viabilidad para el servicio de inferencia (Fases G y H):** menor huella de memoria y menor latencia por radiografía al desplegarse como servicio HTTP independiente.
- **Criterio frente a otras familias:** aunque la documentación reciente de Ultralytics incluye familias posteriores, se mantiene `yolov8n` como referencia controlada acordada en el plan. Cualquier comparación adicional (máximo una variante controlada según [PLAN_EVALUACION_2026-10-25.md](PLAN_EVALUACION_2026-10-25.md)) solo se planteará si la validación evidencia una limitación concreta.
- **Advertencia sobre pesos preentrenados:** los pesos iniciales `yolov8n.pt` están preentrenados sobre objetos cotidianos (COCO) para transferencia de aprendizaje; **no constituyen un detector dental ni clínico**. Su descarga y el registro de su huella `SHA-256` se realizarán únicamente al iniciar la Fase D autorizada.

---

## 3. Revisión de licencias (datos, código, pesos y entorno)

El carácter académico del prototipo **no constituye una exención automática de licencias**. Se registran por separado las condiciones de cada componente y su forma de integración prevista:

| Componente | Fuente oficial | Licencia declarada | Implicación para el MVP académico CORONYX |
| :--- | :--- | :--- | :--- |
| **Dataset DENTEX** (originales y copia derivada `dentex_mvp_v1`) | [ibrahimhamamci/DENTEX](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/README.md) (rev. `7b27ccc8...`) | **CC BY-NC-SA 4.0** | Uso exclusivamente académico y **No Comercial (NC)**. Exige atribución ([ATRIBUCION_DENTEX.md](ATRIBUCION_DENTEX.md)), indicación de modificaciones/exclusiones y mantener la misma licencia (**SA**) en derivados de datos que se compartan. Las radiografías no se suben a GitHub ni se empaquetan en el despliegue de producción. |
| **Framework `ultralytics` y pesos `yolov8n`** (iniciales y ajustados) | [Ultralytics YOLOv8](https://docs.ultralytics.com/models/yolov8/) / [Licencia Ultralytics](https://www.ultralytics.com/license) | **AGPL-3.0** (*GNU Affero General Public License v3.0*) | Licencia *copyleft* fuerte que cubre tanto el código como los pesos preentrenados y ajustados con dicho framework. Permite uso gratuito en investigación, educación y proyectos de código abierto compatibles con AGPL-3.0. **No permite** distribución propietaria cerrada ni explotación comercial como SaaS privado sin adquirir licencia empresarial o publicar el código bajo AGPL-3.0. |
| **PyTorch y TorchVision** (`torch`, `torchvision`) | [pytorch.org](https://pytorch.org/get-started/locally/) | **BSD-3-Clause** (más componentes de tiempo de ejecución CUDA bajo EULA de NVIDIA) | Licencia permisiva compatible con investigación académica y servicio local/HTTP. |
| **Pillow** (`Pillow==12.3.0`) | [PyPI Pillow](https://pypi.org/project/pillow/12.3.0/) | **MIT-CMU** | Licencia permisiva usada en la preparación y verificación de imágenes. |

### Forma de integración acorde con las licencias

1. **Propósito:** demostración académica universitaria (UTP) sin explotación comercial ni contratación de licencias de pago.
2. **Desacoplamiento técnico:** el código de entrenamiento e inferencia permanece en `ML_Laboratorio/` y se comunicará con el backend de CORONYX mediante un contrato HTTP explícito, sin incrustar librerías AGPL ni datos de entrenamiento dentro del artefacto Maven de Spring Boot ni del paquete de React.
3. **Pesos fuera de Git:** los archivos `.pt` (tanto iniciales como entrenados) se excluyen de Git mediante `.gitignore` y se identifican en los manifiestos mediante su huella `SHA-256`, origen y fecha.

---

## 4. Compatibilidad del entorno y GPU en el equipo de trabajo

- **Equipo comprobado (laptop):**
  - **Sistema operativo:** Windows (`Windows-10-10.0.26200-SP0`).
  - **Python del entorno aislado (`venv`):** Python `3.11.9` (`ML_Laboratorio/venv`).
  - **GPU detectada:** `NVIDIA GeForce RTX 5060 Laptop GPU` (`8 151 MiB` de VRAM, controlador `592.07`, soporte de driver hasta CUDA `13.1`).
  - **Arquitectura GPU:** NVIDIA Blackwell (`sm_120`, capacidad de cómputo `12.0`).
- **Versiones exactas comprobadas:**
  - `Pillow==12.3.0`
  - `torch==2.11.0+cu128`
  - `torchvision==0.26.0+cu128`
  - `ultralytics==8.4.191`
- **Selección oficial de PyTorch/CUDA:**
  - Las arquitecturas Blackwell (`sm_120`) requieren compilaciones de PyTorch con **CUDA 12.8** (`cu128`). Versiones anteriores de CUDA (`cu118`, `cu121`, `cu124`) no incluyen kernels compatibles con `sm_120`.
  - Se utiliza el índice oficial de PyTorch: `https://download.pytorch.org/whl/cu128`.
- **Separación de dependencias:**
  - [requirements-preparacion.txt](requirements-preparacion.txt): entorno mínimo de preparación y pruebas (`Pillow==12.3.0`), ejecutable sin GPU.
  - [requirements-entrenamiento.txt](requirements-entrenamiento.txt): versiones exactas fijadas de `torch==2.11.0+cu128`, `torchvision==0.26.0+cu128` y `ultralytics==8.4.191` comprobadas en este equipo.
- **Verificación reproducible sin entrenar:**
  - El módulo `herramientas.verificar_entorno_gpu` comprobó importación de librerías, visibilidad de CUDA `12.8`, capacidad de cómputo `12.0` de la GPU, multiplicación matricial en VRAM (`tensor_matmul_verified: true`) y disponibilidad de la clase `YOLO`, guardando el recibo en [ENTORNO_GPU_2026-10-28.json](auditoria/ENTORNO_GPU_2026-10-28.json) **sin descargar pesos ni iniciar entrenamiento**.

