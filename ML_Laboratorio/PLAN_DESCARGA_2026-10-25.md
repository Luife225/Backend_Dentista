# CORONYX — plan de adquisición de datos DENTEX

> **Ejecución posterior, 27/10/2026:** se completó la colección prevista de 755 imágenes y dos JSON, 3 088 539 253 bytes, con cero errores de adquisición. Ver [continuidad y controles](CONTINUIDAD_MVP_2026-10-27.md). Este documento conserva el plan y el inventario histórico del 25/10; el recibo real se guarda por separado en `data/adquisicion/recibo.json`.

Versión 1, 25/10/2026. **Plan cerrado y manifiesto generado; descarga de trabajo no ejecutada.** No se instalaron herramientas ni se entrenó.

## 1. Archivos necesarios para la ruta de evaluación acordada

Se utilizará únicamente el subconjunto diagnóstico de la revisión `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93` de [DENTEX](https://huggingface.co/datasets/ibrahimhamamci/DENTEX).

- Entrenamiento de origen: 705 imágenes PNG bajo `training_data/quadrant-enumeration-disease/xrays/`, dentro de `training_data.zip`, y su JSON `train_quadrant_enumeration_disease.json`.
- Validación de origen: 50 imágenes PNG identificadas por `validation_triple.json`, dentro de `validation_data.zip`, y ese JSON independiente.
- Los dos JSON ya están guardados y verificados en el laboratorio. Se conservarán como fuentes originales, sin aplicar todavía las reglas de depuración.

Se obtienen las 705 imágenes originales, incluidas las que tienen dudas o ninguna anotación, para conservar la distribución y poder auditarla. Su adquisición no las declara elegibles para entrenamiento. Las correcciones, exclusiones y el reparto interno se decidirán/documentarán después según las reglas y el plan de evaluación.

El paquete de imágenes de test queda fuera de esta adquisición inicial por su taxonomía sin correspondencia verificada. Los 250 JSON usados para comprobar su esquema ya se conservan aparte; no se incorporan a entrenamiento. Tampoco se necesitan las imágenes de cuadrantes, de enumeración sin diagnóstico ni las no anotadas del ZIP grande para este primer experimento.

## 2. Tamaños calculados desde los índices oficiales

Todos los GB siguientes son decimales: 1 GB = 1 000 000 000 bytes. Los tamaños son de archivos, no consumo de RAM/VRAM ni tamaño de un modelo.

- Las 705 imágenes diagnósticas: **2 891 368 969 bytes**, aproximadamente **2,89 GB** sin comprimir.
- Las 50 imágenes de validación: **195 917 600 bytes**, aproximadamente **0,196 GB** sin comprimir.
- Los dos JSON: **1 252 684 bytes**, aproximadamente **1,25 MB**.
- Total de la colección original necesaria: **3 088 539 253 bytes**, aproximadamente **3,09 GB**.

Los registros comprimidos de esas 755 imágenes suman **2 292 759 821 bytes**, aproximadamente 2,29 GB. Es la suma de los contenidos comprimidos seleccionados, no un ZIP independiente publicado con ese tamaño.

Inventario local al cerrar el plan:

- Ocho originales de entrenamiento ya están guardados y verificados.
- El original `val_33.png` sigue disponible en una carpeta temporal y también coincide por tamaño/CRC32; debe revalidarse antes de reutilizarlo, porque podría desaparecer.
- Se pueden reutilizar nueve originales en total. Quedan **697 de entrenamiento y 49 de validación: 746 imágenes**.
- Los contenidos comprimidos pendientes suman **2 267 802 864 bytes**, aproximadamente **2,27 GB de transferencia**, más cabeceras, consultas auxiliares y posibles reintentos.
- Esas imágenes pendientes ocuparían **3 053 742 533 bytes**, aproximadamente 3,05 GB, una vez obtenidas. Al colocar una copia organizada de todas las fuentes, presupuestar los 3,09 GB completos.

Si una muestra local ya no está o falla su integridad, se reclasifica como pendiente y se actualiza el inventario. No se reutilizan superposiciones como si fueran originales.

## 3. Método preferido: obtener solo los registros necesarios

Las solicitudes parciales HTTP Range ya funcionaron para las muestras y los índices. La adquisición futura usará esa capacidad para leer cada registro PNG seleccionado dentro de los dos ZIP, sin obtener su contenido completo.

El [manifiesto de adquisición](MANIFIESTO_ADQUISICION_2026-10-25.json) lista las 755 imágenes con partición de origen, ID, dimensiones, URL fijada, entrada y posición en el ZIP, tamaños, CRC32, posible reutilización local y destino propuesto. Cada acción está marcada como no ejecutada.

Secuencia cuando se inicie la adquisición:

1. Revalidar espacio libre, revisión fijada, hashes de JSON/índices y manifiesto. No cambiar silenciosamente a la versión `main` más reciente.
2. Revalidar los nueve archivos locales. Copiar únicamente originales que coincidan; conservar sus hashes y comprobar dimensiones al abrirlos.
3. Obtener solo los registros pendientes, con lecturas acotadas, pocos archivos simultáneos y reintentos limitados. Si el servidor ignora Range, detener esa ruta sin leer el cuerpo completo del ZIP.
4. Escribir cada archivo en una ubicación parcial dentro del laboratorio; comprobar tamaño/CRC32 del registro, decodificación y dimensiones antes de declararlo completo. Registrar SHA-256 del original obtenido. Un archivo incompleto nunca se marca como terminado.
5. Evitar rutas arbitrarias del ZIP: usar los nombres y destinos comprobados del manifiesto, contenidos dentro del directorio autorizado. No sobrescribir un original existente que no coincida; registrar el conflicto para revisión.
6. Copiar los dos JSON originales con sus hashes verificados. No renumerar categorías ni retirar anotaciones durante la adquisición.
7. Emitir un recibo con archivos disponibles, faltantes, errores, bytes y hashes. Completar 755 originales y dos JSON es un control de adquisición, no una aprobación para entrenar.

No se promete duración a partir del tamaño: el tiempo dependerá de la conexión, las solicitudes, el alojamiento y los reintentos. Se puede continuar una adquisición incompleta reutilizando solo archivos íntegros ya verificados.

## 4. Ubicación y conservación

Destino propuesto, todavía sin crear ni poblar por esta tarea:

`ML_Laboratorio/data/dentex_raw/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/`

Dentro habría `train/images/`, `validation/images/` y una copia de `annotations.original.json` por partición de origen. El nombre `train` aquí indica procedencia; el futuro reparto de entrenamiento y prueba interna se fija después en manifiestos separados.

Los datos permanecen en `data/`, excluido por el `.gitignore` del laboratorio. Las anotaciones depuradas, cuando se generen, se guardarán como derivados separados y con las reglas aplicadas. Se evitará duplicar imágenes para cada partición si la herramienta elegida permite utilizar listas de rutas; el presupuesto cubre una copia adicional como margen conservador.

Las muestras actuales de auditoría no se borran ni se mueven con este plan. No se usan archivos de pacientes de CORONYX ni se modifican frontend, backend, base de datos o despliegue.

## 5. Espacio y presupuesto de trabajo

Última medición guardada en el manifiesto: **42 933 907 456 bytes libres en C:**, aproximadamente **42,93 GB**. Es una observación puntual; el espacio no está reservado.

Presupuesto propuesto, con cantidades redondeadas y explícitamente estimadas:

- Datos originales: 3,1 GB.
- Posible copia de preparación: 3,1 GB.
- Futuro entorno y caché de dependencias: margen de 10 GB, aún no medido ni instalado.
- Resultados experimentales: margen de 5 GB, aún no generado.
- Temporales y trabajo intermedio: margen de 2 GB.
- Trabajo presupuestado: **23,2 GB**. Mantener además **10 GB libres**, para un umbral de disponibilidad de **33,2 GB** antes de comprometer todo ese trabajo.

La medición actual supera ese presupuesto en unos 9,7 GB. Esto acredita viabilidad de espacio según esas previsiones, no compatibilidad de PyTorch/CUDA ni memoria suficiente para cualquier entrenamiento. Antes de adquirir o instalar se volverá a medir y se ajustarán los márgenes según el consumo real.

## 6. Alternativa si las lecturas parciales dejan de funcionar

Los dos ZIP completos suman **11 076 903 785 bytes**, aproximadamente 11,08 GB; además se necesitan los datos seleccionados extraídos y los demás márgenes. Mantener ambos ZIP junto con todo el presupuesto futuro elevaría la necesidad a unos 44,28 GB incluyendo los 10 GB libres, por encima de la medición actual.

Por eso, la descarga completa no es una alternativa automática. Se revisaría el nuevo espacio, el alcance y el plan de conservación antes de elegirla. No extraer los otros subconjuntos por accidente ni borrar archivos ajenos para hacer espacio. Este plan no inicia ninguna descarga completa ni eliminación.

## 7. Condiciones y cierre de esta tarea

La [ficha de datos](FICHA_DATOS_DENTEX_2026-10-25.md) y la [atribución preparada](ATRIBUCION_DENTEX.md) registran procedencia, licencia declarada, modificaciones realizadas y límites. La discrepancia entre los artículos y la distribución sobre licencia sigue documentada; no se interpreta como permiso comercial.

**Plan de adquisición: completado.** Hay lista exacta de archivos, ruta preferida comprobada en muestras, inventario de lo existente y presupuesto de espacio. La adquisición masiva, la depuración y la generación de particiones finales no se ejecutaron. La preparación para entrenar sigue sujeta a los pendientes de la ficha.
