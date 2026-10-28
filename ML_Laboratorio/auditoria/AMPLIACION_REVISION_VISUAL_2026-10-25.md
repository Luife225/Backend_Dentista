# DENTEX — ampliación de la revisión visual

Fecha: 25/10/2026. Estado: **cinco muestras adicionales revisadas técnicamente**. Junto con los tres casos dirigidos anteriores se alcanza el mínimo de **ocho imágenes de entrenamiento** previsto para esta revisión. La muestra inicial de validación es adicional: nueve imágenes distintas revisadas en total, no nueve de entrenamiento.

No se realizó validación clínica, entrenamiento, inferencia, corrección de etiquetas ni exclusión de imágenes. Las marcas mostradas proceden de las anotaciones originales.

## Selección antes de ver las imágenes

Se utilizó la revisión de DENTEX `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93` y el JSON diagnóstico ya auditado. Se fijaron cuatro estratos a partir de sus etiquetas: pocas marcas de caries sin caries profunda anotada, pocas de caries profunda sin caries anotada, presencia de ambas y ocho o más marcas objetivo. Para cada estrato se eligió el menor hash SHA-256 de la cadena formada por `CORONYX-visual-20261025`, revisión y nombre de archivo, separados por `|`. La quinta imagen se eligió por la menor área en píxeles entre las candidatas restantes, con el mismo hash para desempatar.

Se excluyeron de esta selección adicional las imágenes de los 16 casos ya registrados. Esto no las excluye del dataset ni las declara erróneas; evita repetir una revisión dirigida a incidencias. La selección quedó guardada antes de obtener imágenes. Por su diseño, **esta muestra no es representativa para estimar calidad clínica o rendimiento**.

El programa [de selección y comprobación](ampliar_revision_visual.py) permite repetir la selección. Los originales, selección, hashes, comprobaciones y figuras están bajo `data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/`, excluido de Git.

## Comprobaciones realizadas

- Las cinco imágenes se obtuvieron con lecturas parciales del ZIP de entrenamiento, verificando tamaño y CRC32 contra su índice.
- Se decodificaron correctamente y sus dimensiones reales coincidieron con el JSON.
- Sus 25 anotaciones, 23 de categorías objetivo y dos de otras categorías, quedan dentro de las dimensiones reales de las imágenes.
- Se inspeccionaron los originales y las cinco superposiciones. No se observó desplazamiento global, inversión o deformación aparente de las cajas; se sitúan visualmente sobre regiones dentales.
- No se observaron nombres o identificadores personales legibles en estas cinco muestras. Esto no certifica anonimización de toda la colección ni de todos los metadatos.
- Las figuras mantienen la imagen completa y solo aplican escalado para mostrarla, cuadros y texto. No se modificó contraste, contenido radiográfico o etiquetas. C = caries, P = caries profunda y O = otra categoría; se conserva esta última en la vista para evitar confundir su ausencia del alcance con salud.

La revisión de localización es técnica. No certifica la severidad de una caries, la exhaustividad de las anotaciones ni la corrección clínica de cada diagnóstico.

## Muestra 1 — pocas marcas de caries

`train_204.png`, 2 872 × 1 316 píxeles. Una anotación de caries y una de diente impactado; ninguna de caries profunda. Sus dos cuadros quedan sobre regiones separadas, sin desplazamiento global aparente.

![Una marca de caries y otra categoría](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/muestra-1-marcas.png)

## Muestra 2 — pocas marcas de caries profunda

`train_622.png`, 2 977 × 1 316 píxeles. Dos anotaciones de caries profunda y una de diente impactado; ninguna de caries. Las marcas están en regiones dentales diferenciadas. El tamaño del cuadro varía entre dientes; no se necesita que todos los cuadros tengan el mismo tamaño.

![Dos marcas de caries profunda y otra categoría](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/muestra-2-marcas.png)

## Muestra 3 — ambas categorías en una radiografía

`train_4.png`, 2 820 × 1 316 píxeles. Una anotación de caries y una de caries profunda, con cuadros en regiones separadas. Sirve para comprobar que la misma imagen puede contener varios hallazgos de distintas categorías. No se convierte la radiografía completa en una única clase.

![Caries y caries profunda en regiones distintas](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/muestra-3-marcas.png)

## Muestra 4 — muchas marcas

`train_185.png`, 2 894 × 1 316 píxeles. Tres anotaciones de caries y ocho de caries profunda. Hay cuadros de dientes vecinos que se superponen parcialmente; eso no demuestra una duplicación. La vista conserva las 11 marcas y muestra por qué el futuro visor debe distinguir categorías y permitir ampliar la imagen.

![Once anotaciones objetivo sobre la misma radiografía](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/muestra-4-marcas.png)

## Muestra 5 — menor resolución y márgenes negros

`train_251.png`, 1 976 × 976 píxeles. Siete anotaciones de caries, ninguna de caries profunda. La imagen tiene menor resolución y un marco negro. Los cuadros corresponden al sistema de coordenadas de la imagen completa, incluido ese marco. Si más adelante se recortan márgenes o se redimensiona, deberán transformarse las coordenadas de forma coherente y recuperar las originales al mostrar resultados. No se realizó ese preprocesamiento aquí.

![Imagen de menor resolución con siete marcas de caries](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/visual_review_5/muestra-5-marcas.png)

## Qué se cierra y qué sigue abierto

La ampliación de la muestra visual técnica queda completada con cinco imágenes adicionales y ocho de entrenamiento acumuladas. No se identificó una nueva incidencia de alineación global en estas cinco. Esto no resuelve los 15 grupos pendientes del registro anterior ni demuestra que toda la colección esté correctamente anotada.

Para el día 2 permanecen la estrategia de evaluación (incluida la compatibilidad del test con ambas categorías), el plan de archivos/espacio y los controles de duplicados por contenido y grupos de pacientes cuando se disponga de las imágenes de trabajo. La copia preparada para entrenamiento todavía no existe.

Fuente: [DENTEX, Hamamci y colaboradores](https://huggingface.co/datasets/ibrahimhamamci/DENTEX), licencia declarada CC BY-NC-SA 4.0. Se añadieron escalado de presentación, cuadros y etiquetas a las figuras, bajo la misma licencia. Los originales y anotaciones se preservaron.
