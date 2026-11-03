# Procedencia y atribución de DENTEX en el laboratorio CORONYX

Material: DENTEX, conjunto de radiografías panorámicas y anotaciones publicado por **Ibrahim Ethem Hamamci y colaboradores**, asociado al reto MICCAI 2023.

- Fuente del dataset: [distribución de los autores](https://huggingface.co/datasets/ibrahimhamamci/DENTEX).
- Revisión utilizada: `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93`.
- Ficha de esa revisión: [README fijado](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/README.md).
- Repositorio de los autores: [DENTEX en GitHub](https://github.com/ibrahimethemhamamci/DENTEX).
- Licencia declarada por la distribución: **Creative Commons Atribución-NoComercial-CompartirIgual 4.0 Internacional (CC BY-NC-SA 4.0)**.
- Licencia: [texto de CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
- Consulta y preparación de este aviso: 25/10/2026.

Referencia académica del dataset: Hamamci, I. E., y colaboradores. *DENTEX: An Abnormal Tooth Detection with Dental Enumeration and Diagnosis Benchmark for Panoramic X-rays* (2023), arXiv:2305.19112. La [versión posterior del artículo](https://arxiv.org/abs/2305.19112) tiene un título actualizado.

En CORONYX se utilizan anotaciones de referencia para planificar una demostración académica. Los originales consultados se conservan sin cambios. En las figuras de revisión se añadieron escalado para presentación, algunos recortes, cuadros, colores y texto; esas adaptaciones se presentan bajo CC BY-NC-SA 4.0. No son predicciones generadas por un modelo y los autores de DENTEX no han revisado ni avalado nuestro prototipo.

Todavía no se ejecutó una conversión o depuración de etiquetas ni se entrenó un modelo. Si más adelante se generan derivados, registrar sus cambios y condiciones aplicables. Este aviso no pretende resolver la licencia de código/pesos ni autorizar un uso comercial. La discrepancia entre licencia mencionada en los artículos y distribución está registrada en la ficha de datos; no se relajó la condición de los archivos adquiridos.

## Actualización de derivados experimentales (28/10/2026)

Se conserva íntegra la procedencia del 25/10/2026 anterior y los archivos originales bajo `data/dentex_raw/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/` permanecen sin modificaciones.

Para el primer experimento académico restringido ([POLITICA_MVP_V1.json](preparacion/POLITICA_MVP_V1.json) y [PARTICIONES_DENTEX_V1.json](preparacion/PARTICIONES_DENTEX_V1.json)) se definió una copia derivada local en `datasets/dentex_mvp_v1/` (excluida de Git por tamaño, reproducible mediante `herramientas.preparar_dataset --aplicar`) con los siguientes cambios documentados respecto de la colección diagnóstica original de 755 imágenes:

- **Deduplicación exacta de anotación en la copia:** en `train_377.png` se conserva la anotación `2335` y se omite `2337` únicamente en la copia derivada, tras comprobar coincidencia exacta de todos los campos salvo el identificador.
- **Exclusión de imágenes completas en la copia (76 excluidas, 679 conservadas):**
  - 15 imágenes de entrenamiento con grupos de etiquetas pendientes de resolución clínica (`train_85.png`, `train_212.png`, `train_250.png`, `train_343.png`, `train_350.png`, `train_397.png`, `train_440.png`, `train_451.png`, `train_467.png`, `train_481.png`, `train_485.png`, `train_523.png`, `train_557.png`, `train_572.png`, `train_644.png`).
  - 55 imágenes de entrenamiento y 6 de validación sin anotaciones de las dos categorías objetivo (`caries` y `caries_profunda`), sin declararlas sanas ni negativos verificados.
- **Conversión y remapeo de etiquetas en la copia:** se exportan únicamente las categorías diagnósticas originales `1` (*Caries*) → `0` (`caries`) y `3` (*Deep Caries*) → `1` (`caries_profunda`) en formatos YOLO (coordenadas normalizadas `centro_x centro_y ancho alto`) y vista COCO derivada, repartidas en `508` imágenes de entrenamiento, `44` de validación y `127` de prueba interna (semilla `20261025`).
- **Condiciones aplicables:** cualquier material derivado o adaptación que llegue a compartirse mantiene la atribución a **Ibrahim Ethem Hamamci y colaboradores (DENTEX, MICCAI 2023)** y las condiciones **CC BY-NC-SA 4.0** declaradas por la distribución. Las licencias del código del detector y de sus pesos preentrenados son independientes y se documentan en `FICHA_MODELO_Y_LICENCIAS_2026-10-28.md`.
