# DENTEX — integridad y repeticiones por contenido

27/10/2026. Control técnico del laboratorio ML, sin interpretación clínica, modificación de etiquetas, particiones ni entrenamiento.

## Alcance y evidencia

Se adquirió exclusivamente la colección diagnóstica acordada de la revisión `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93`: 705 imágenes de entrenamiento de origen y 50 de validación, con sus dos JSON intactos. No se obtuvieron radiografías del test publicado.

El recibo real `../data/adquisicion/recibo.json` declara adquisición completa y guarda tamaño y SHA-256 de cada archivo. Los datos originales, recibo y cachés están excluidos de Git. El [resultado detallado de este control](INTEGRIDAD_IMAGENES_2026-10-27.json) guarda las huellas, dimensiones y parámetros de comparación.

Las 755 imágenes suman 3 087 286 569 bytes. Con los dos JSON, 3 088 539 253 bytes: aproximadamente 3,09 GB decimales.

## Resultado observado

- 755 imágenes decodificadas y verificadas contra tamaño, CRC32, SHA-256 del recibo y dimensiones del JSON original.
- Cero fallos de integridad y cero diferencias de dimensiones.
- Cero grupos de archivos PNG idénticos por SHA-256.
- Cero grupos de imágenes con píxeles decodificados idénticos según el criterio definido.
- Cero parejas candidatas con el filtro perceptual fijado.
- Cero imágenes con claves de metadatos PNG expuestas por Pillow. No se publicaron valores de metadatos.

Los dos primeros controles de repeticiones comparan toda la colección y, por tanto, también los cruces entre entrenamiento y validación de origen. No se generó todavía la prueba interna.

## Método y límites

La igualdad de archivo utiliza SHA-256 de los bytes originales. La de píxeles utiliza SHA-256 de modo nativo, dimensiones, bytes decodificados, paleta si corresponde y transparencia. Modos distintos no se consideran automáticamente iguales; el filtro perceptual sirve como control adicional.

Filtro de candidatos fijado antes de comparar: dHash horizontal de 256 bits con distancia de Hamming de hasta 8, hash de promedio de 256 bits con distancia de hasta 12 y diferencia de proporción de aspecto de hasta el 2 %. Deben cumplirse los tres criterios. Se compararon todas las parejas; se omitieron de este filtro las que ya fueran idénticas por píxeles. No hubo ninguna de estas últimas.

Es un filtro técnico, no un detector exhaustivo de imágenes relacionadas. Puede omitir recortes, cambios de contraste, imágenes de otro estudio del mismo paciente y otras transformaciones. No encontrar candidatos no demuestra independencia por paciente. DENTEX no proporcionó identificadores explícitos de paciente/estudio en los registros consultados.

La ausencia de claves de metadatos tampoco acredita que no haya texto identificable dentro de los píxeles. La revisión visual anterior abarca ocho originales de entrenamiento y uno de validación; esta auditoría de integridad no amplía esa revisión clínica o visual a las 755 imágenes.

El duplicado `2335`/`2337` sigue siendo un duplicado de **anotación** en `train_377.png`. Cero imágenes repetidas no resuelve ese caso ni los 15 grupos dudosos. Tampoco convierte imágenes sin etiquetas en negativos fiables.

## Decisión

Se completa la adquisición y este control de integridad/repetición técnica. El siguiente paso es aplicar decisiones de calidad a una copia derivada y fijar las particiones del experimento según el plan de evaluación. El conjunto aún no se declara listo para entrenar; no hay modelo, métricas o predicciones producidas.
