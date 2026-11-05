# DENTEX — revisión de etiquetas de entrenamiento y validación

Fecha: 25/10/2026, hora de Perú. Estado: revisión estructural ejecutada; preparación para entrenamiento pendiente de resolver casos y de revisar imágenes. No se entrenó.

## Resultado principal

Las anotaciones tienen una estructura utilizable y los nombres de sus 755 imágenes aparecen en los índices de los paquetes oficiales. Se encontraron **un registro duplicado**, **11 grupos con caries y caries profunda bajo la misma identidad dental registrada**, y **31 imágenes sin ninguna anotación** entre entrenamiento y validación. Ninguna de estas observaciones se corrigió automáticamente.

La recomendación es conservar las dos categorías acordadas y revisar los casos señalados antes de convertir los datos. La revisión geométrica se hizo contra las dimensiones del JSON; no se verificaron las dimensiones reales de todas las radiografías.

> **Consulta posterior del protocolo:** el [resultado documental sobre etiquetas múltiples](PROTOCOLO_ETIQUETAS_MULTIPLES_2026-10-25.md) distingue coexistencia de diagnósticos, identidades repetidas y duplicados exactos. Los 11 grupos no se consideran errores confirmados. No se encontró una regla explícita para resolver Caries + Deep Caries en el mismo cuadro; su tratamiento sigue pendiente.

## Fuentes, método y alcance

- [Repositorio oficial del dataset](https://huggingface.co/datasets/ibrahimhamamci/DENTEX).
- Revisión fijada: `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93`.
- [Paquete de entrenamiento fijado](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/DENTEX/training_data.zip): se leyó su índice y únicamente `training_data/quadrant-enumeration-disease/train_quadrant_enumeration_disease.json`. Integridad del JSON comprobada con tamaño y CRC32 del ZIP.
- [Anotaciones de validación fijadas](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/DENTEX/validation_triple.json): leídas directamente. También se consultó el índice de `validation_data.zip`.
- No se solicitaron contenidos de imágenes, otros subconjuntos de entrenamiento, datos de prueba ni pesos de modelos.
- Los JSON y los índices consultados se conservan bajo `ML_Laboratorio/data/dentex_audit/`, excluido por el `.gitignore` del laboratorio. No se redistribuyen mediante este informe.
- [Resultado detallado](resultado_etiquetas_2026-10-25.json) y [programa de revisión reproducible](revisar_etiquetas_dentex.py). El programa solo utiliza la biblioteca estándar; no instala herramientas, convierte etiquetas ni entrena.

SHA-256 de los JSON utilizados:

- Entrenamiento, 1 188 275 bytes: `6e1f702cfd6c83bc63660d9a0fd79fe5b26d5a54b63d93b954a02cc254314483`.
- Validación, 64 409 bytes: `d058afd35d2849923c7c045e61fd3e05d231dcf74d55009993fff88bd9b6f5a2`.

## Paso 1 — categorías y cantidades

Las definiciones de categorías coinciden entre entrenamiento y validación. El diagnóstico usa 0 = Impacted, 1 = Caries, 2 = Periapical Lesion y 3 = Deep Caries. Cuadrante, número de diente y diagnóstico están en campos separados.

Entrenamiento: **705 imágenes y 3 529 anotaciones**.

- Caries: 2 189 anotaciones en 623 imágenes.
- Caries profunda: 578 anotaciones en 321 imágenes.
- Otras categorías: 604 anotaciones de dientes impactados y 158 de lesión periapical.
- 294 imágenes contienen ambas categorías objetivo, en el conjunto de sus dientes. Eso por sí solo no es inconsistencia.

Validación: **50 imágenes y 182 anotaciones**.

- Caries: 101 anotaciones en 37 imágenes.
- Caries profunda: 32 anotaciones en 20 imágenes.
- Otras categorías: 40 anotaciones de dientes impactados y 9 de lesión periapical.
- 13 imágenes contienen ambas categorías objetivo.

Las cantidades son anotaciones, no pacientes ni dientes únicos. La frecuencia de las dos categorías es distinta; las métricas futuras deben informarse por categoría.

## Paso 2 — controles estructurales superados

En ambas particiones, los controles ejecutados no encontraron:

- IDs de imágenes o anotaciones repetidos, ni nombres de imagen repetidos dentro de cada JSON.
- Referencias a IDs de imagen inexistentes.
- Categorías desconocidas en ninguno de los tres niveles.
- Cuadros con formato incorrecto, coordenadas no finitas, tamaño no positivo o fuera de las dimensiones declaradas.
- Polígonos ausentes, de estructura inválida o fuera de las dimensiones declaradas, ni áreas no positivas/no finitas en los metadatos.

Además, las 705 imágenes de entrenamiento y las 50 de validación figuran de forma no ambigua en los índices de sus ZIP. Esto verifica los nombres publicados; no demuestra que todas las imágenes puedan abrirse ni que sus marcas sean clínicamente correctas.

## Paso 3 — un registro duplicado confirmado

En `train_377.png`, las anotaciones **2335 y 2337** son idénticas en todos sus campos salvo el identificador: misma imagen, cuadro, polígono, área, cuadrante, número de diente y diagnóstico de caries.

Consecuencia: una conversión directa incluiría el mismo ejemplo dos veces. Propuesta para una futura copia de entrenamiento: conservar una sola anotación y registrar qué identificador se omitió. **Esta depuración no se ejecutó; los originales siguen intactos.**

La validación no contiene un registro idéntico de este tipo en los controles realizados.

## Paso 4 — identidades dentales repetidas y etiquetas múltiples

Agrupando por imagen, cuadrante y número de diente registrados:

- Entrenamiento: 66 grupos tienen varios diagnósticos; cinco grupos repiten un diagnóstico bajo la misma identidad dental. La combinación de condiciones distintas puede ser legítima y no se elimina por este conteo.
- Validación: tres grupos tienen varios diagnósticos; ninguno de esos tres combina caries y caries profunda.
- Entrenamiento: **11 grupos en 11 imágenes combinan específicamente caries y caries profunda bajo la misma identidad registrada**.

No se concluye que sean 11 dientes clínicamente contradictorios: puede haber varias anotaciones, diferencias de localización o problemas de numeración. Los ejemplos siguientes demuestran por qué necesitan revisión:

1. `train_481.png`, anotaciones 2780 y 2782: ambas categorías tienen exactamente el mismo cuadro (IoU 1,0).
2. `train_440.png`, anotaciones 209 y 211: el número de diente registrado coincide, pero los cuadros están separados (IoU 0,0). Una coincidencia de número no prueba que marquen el mismo diente físico.
3. `train_250.png`, anotaciones 736 y 740: solapamiento parcial (IoU aproximada 0,267).

Las otras ocho imágenes son `train_451.png`, `train_485.png`, `train_343.png`, `train_557.png`, `train_212.png`, `train_572.png`, `train_397.png` y `train_350.png`.

IoU mide cuánto se superponen dos cuadros, de 0 a 1; no mide coincidencia clínica. También se encontraron nueve pares con IoU de al menos 0,95 en entrenamiento, ninguno en validación. Son candidatos de revisión y se solapan con los grupos anteriores; no sumar todos esos conteos como problemas independientes.

**Acción siguiente:** visualizar primero los casos de cuadro idéntico, de cuadros separados y el duplicado confirmado. Conservar las etiquetas originales. No elegir automáticamente la categoría profunda, borrar todos los solapamientos ni convertir cada imagen en una única clase. Si queda una duda clínica, documentarla para revisión profesional. Si no se logra resolver un caso antes del experimento, habrá que definir y registrar su tratamiento en la copia derivada sin usar la prueba final para decidirlo.

## Paso 5 — imágenes sin etiquetas

- Entrenamiento: 27 imágenes no tienen ninguna anotación. Un total de 55 no tiene anotaciones de las dos categorías objetivo; ese total incluye las 27 anteriores y otras 28 con condiciones diferentes.
- Validación: cuatro imágenes no tienen ninguna anotación. Seis no tienen anotaciones de las categorías objetivo; incluyen las cuatro anteriores y dos con otras condiciones.

Esto no significa automáticamente que falten archivos ni que los pacientes estén sanos. Hay que confirmar cómo representa el protocolo los negativos y las anotaciones completas. No descartar estas imágenes ni declararlas negativas fiables únicamente a partir de un JSON vacío.

## Paso 6 — separación entre particiones y pacientes

No hay nombres de imagen iguales entre entrenamiento y validación. Tampoco aparecieron coincidencias simultáneas de CRC32 y tamaño descomprimido entre sus imágenes listadas. Esta comprobación de metadatos es limitada: no sustituye comparar contenidos, buscar variantes, ni revisar imágenes similares.

En los dos JSON, cada imagen solo tiene `file_name`, `height`, `id` y `width`. No se encontraron campos explícitos de paciente o estudio en esos registros ni un grupo de pacientes en las claves principales. Los IDs de imagen no se pueden tratar como IDs de paciente. No se ha demostrado independencia por paciente ni ausencia de duplicados visuales.

El conjunto de prueba sigue sin inspeccionarse en esta auditoría.

## Conclusión y siguiente acción

La revisión estructural de los JSON diagnósticos de entrenamiento y validación está ejecutada. Hay evidencia suficiente para planear una revisión visual dirigida, pero no para declarar completada toda la preparación de datos.

Conservar dos categorías sigue siendo el objetivo del prototipo. Antes de la conversión, revisar los grupos señalados y definir el tratamiento del duplicado y de las imágenes sin etiquetas. La próxima muestra debe priorizar `train_481.png`, `train_440.png` y `train_377.png`, con las anotaciones originales visibles y sin reinterpretar diagnósticos por cuenta propia.

La suma de tamaños descomprimidos publicada en el índice para las 705 imágenes diagnósticas de entrenamiento es 2 891 368 969 bytes; para las 50 de validación, 195 917 600 bytes. Son tamaños de archivo del índice, no consumo de memoria ni tamaño del resto del ZIP. Se calcularon sin obtener las imágenes y sirven para planificar una descarga posterior.

## Ampliación autorizada: revisión visual de tres casos

Después de esta auditoría, el usuario pidió mostrar las tres muestras y explicar sus problemas. Se obtuvieron exclusivamente `train_377.png`, `train_481.png` y `train_440.png` de la misma revisión fijada, mediante lecturas parciales del ZIP. Se comprobó su CRC32/tamaño contra el índice, se decodificaron y sus dimensiones coincidieron con el JSON. Los originales permanecen bajo `data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/samples/`; el manifiesto de esa carpeta registra SHA-256 de cada imagen.

Se inspeccionaron visualmente los originales y las comparaciones generadas. No se observaron nombres ni identificadores personales legibles en estas tres imágenes; esto no certifica anonimización de toda la colección. Las comparaciones muestran únicamente los registros elegidos. Solo se aplicaron recorte, escalado de presentación, cuadros y texto; no se reinterpretaron ni modificaron las etiquetas.

- **Caso 1, `train_377.png`:** los registros 2335 y 2337 dan la misma caja y la misma categoría, con todos los campos iguales salvo ID. La comparación pone lado a lado el mismo recorte para hacer visible la duplicación. Se mantiene la propuesta de deduplicar únicamente en una copia derivada futura; no se ejecutó.
- **Caso 2, `train_481.png`:** los registros 2780 (caries) y 2782 (caries profunda) cubren exactamente la misma región. Esto requiere aclarar el significado de las dos etiquetas según el protocolo y revisión profesional; la imagen no basta para elegir cuál conservar.
- **Caso 3, `train_440.png`:** los registros 209 (caries) y 211 (caries profunda) aparecen en zonas separadas, aunque comparten cuadrante y número de diente registrados. Hay que revisar la numeración/identidad; compartir esos valores no demuestra que ambos registros correspondan al mismo diente físico ni que sus diagnósticos sean incorrectos.

Comparaciones disponibles:

![Duplicación de la misma anotación](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/examples/caso-1-duplicado.png)

![Dos categorías en la misma caja](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/examples/caso-2-dos-categorias.png)

![Misma identidad registrada en zonas separadas](../data/dentex_audit/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/examples/caso-3-codigo-repetido.png)

Fuente de las imágenes: DENTEX, Hamamci y colaboradores, CC BY-NC-SA 4.0. Comparaciones adaptadas bajo la misma licencia; recortes, escalado y marcas añadidos. Son anotaciones originales, no predicciones de un modelo. Esta revisión dirigida de tres imágenes no completa la muestra variada propuesta ni sustituye validación clínica.
