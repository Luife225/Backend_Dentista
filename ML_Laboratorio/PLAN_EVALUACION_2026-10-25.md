# CORONYX — plan de evaluación del primer detector radiográfico

Versión 1, 25/10/2026. **Diseño definido; evaluación y particiones finales todavía sin ejecutar.** Alcance académico: localizar dientes con anotaciones de caries y caries profunda en panorámicas, con revisión humana. No se evalúa segmentación de lesiones, tratamientos ni utilidad clínica demostrada.

## 1. Hallazgo que cambia la estrategia de prueba

Se inspeccionaron directamente los 250 JSON del [paquete de prueba oficial fijado](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/DENTEX/test_data.zip), con lectura parcial del archivo y sin obtener ni visualizar sus radiografías. Los archivos están en formato LabelMe y contienen 1 600 polígonos. Las anotaciones de entrenamiento/validación consultadas usan otra estructura, con el campo diagnóstico `category_id_3`.

Vocabulario observado en los JSON de prueba, conservado literalmente y sin traducción clínica:

- `0`, `saglam`: 91 polígonos.
- `1`, `çürük`: 747.
- `2`, `küretaj`: 265.
- `3`, `kanal`: 161.
- `5`, `çekim`: 29.
- `6`, `gömülü`: 221.
- `7`, `lezyon`: 75.
- `8`, `kırık`: 11.

**Los códigos numéricos no son equivalentes a los del entrenamiento.** No hay una correspondencia verificada para nuestras dos categorías; la ausencia literal del nombre inglés de caries profunda no demuestra por sí sola qué representan clínicamente esas etiquetas. No se transforma `küretaj`, `kanal` ni otra categoría en caries profunda por traducción o intuición.

La inspección confirma el problema de formato/vocabulario que se había mencionado en una [consulta del repositorio](https://github.com/ibrahimethemhamamci/DENTEX/issues/9); las equivalencias clínicas de esa consulta no se adoptaron. El resultado reproducible está en [ESQUEMA_TEST_2026-10-25.json](auditoria/ESQUEMA_TEST_2026-10-25.json), con hashes de cada JSON. El programa de lectura es [auditar_esquema_test.py](auditoria/auditar_esquema_test.py).

El [evaluador publicado por los organizadores](https://github.com/ibrahimethemhamamci/DENTEX/blob/main/DentexChallenge-AlgorithmPhases-Evaluation-Docker/evaluation.py) espera un JSON con imágenes, anotaciones y categorías jerárquicas; en esa entrada no se define la equivalencia clínica del vocabulario LabelMe observado. No se ejecutó su código ni se instalaron sus dependencias.

## 2. Estrategia inicial: reserva interna antes del entrenamiento

Para el primer experimento se planifica una **prueba interna** a partir del conjunto diagnóstico que sí tiene ambas categorías conocidas. El paquete de prueba oficial queda fuera de entrenamiento, validación, aumentos y evaluación principal hasta que exista un mapeo verificable. Los resultados internos no se presentarán como puntuaciones del test oficial de DENTEX ni como validación externa.

Ruta propuesta para preparar los datos:

1. Aplicar primero las [reglas de calidad documentadas](auditoria/REGLAS_PREPARACION_ETIQUETAS_2026-10-25.md) en una futura copia y registrar decisiones sobre casos dudosos y negativos. No seleccionar muestras según resultados de un modelo.
2. Comprobar duplicados exactos por contenido de archivo y píxeles decodificados; buscar variantes relacionadas mediante similitud y revisión. Agrupar pacientes/estudios si se dispone de identificadores. Los índices y nombres ya revisados no bastan para esta comprobación.
3. Mantener las 50 imágenes oficiales de validación como conjunto de desarrollo, sujeto a los controles de etiquetas y duplicados. No pasarlas a entrenamiento para mejorar cifras. Si se detectan duplicados o grupos que cruzan particiones, resolverlos antes de fijar el reparto, explicando cualquier cambio a las particiones originales.
4. Reservar aproximadamente el **20 % de las imágenes elegibles del conjunto original de 705** como prueba interna. El resto se dedica a entrenamiento. Si las 705 fueran elegibles y todos los grupos fueran de una imagen, la referencia sería 564 para entrenar, 50 para validar y 141 para probar; **estos no son conteos finales ni archivos ya asignados**.
5. Hacer el reparto por grupo de paciente/estudio cuando exista; en su ausencia, por grupos de imágenes duplicadas/relacionadas identificadas y declarar que no se ha demostrado independencia por paciente. Nunca dividir por diente o por anotación.
6. Buscar representación de ambas categorías por presencia en imagen, incluyendo su coexistencia, sin romper grupos. No igualar artificialmente el test por cantidad de anotaciones. Registrar desbalances o falta de soporte si las restricciones impiden un reparto adecuado.
7. Fijar una semilla de planificación `20261025`, las listas de archivos, los grupos, los hashes, las exclusiones y los conteos antes de aumentos, entrenamiento o selección de parámetros. La semilla por sí sola no sustituye guardar los manifiestos.

**Las ocho radiografías de entrenamiento ya usadas para inspección y depuración permanecerán en desarrollo/entrenamiento, fuera de la prueba interna:** `train_377.png`, `train_481.png`, `train_440.png`, `train_204.png`, `train_622.png`, `train_4.png`, `train_185.png`, `train_251.png`. Si una queda excluida por una decisión de calidad, se registra esa exclusión. Una variante relacionada tampoco puede terminar en prueba.

Por ahora no se ha generado el reparto: faltan las comprobaciones por contenido y las decisiones pendientes del registro de casos. La tarea de hoy define cómo hacerlo; no declara preparada la copia de datos.

Si más adelante se recupera una correspondencia oficial fiable para el test público, podrá proponerse una evaluación adicional diferenciada, preservando su procedencia y sin cambiar retrospectivamente el significado de esta prueba interna.

## 3. Qué constituye un acierto

Unidad: **una detección de diente anotado**, no una imagen clasificada como enferma/sana. Se evalúan caries y caries profunda por separado.

- Un acierto debe tener la categoría de referencia correcta y un cuadro que se superponga suficientemente con el cuadro de referencia.
- Para precisión, recall y F1 del punto de operación se fija IoU ≥ 0,50. IoU mide el área compartida dividida por la unión de ambos cuadros; no representa severidad clínica.
- Dentro de cada imagen y categoría, una predicción solo puede corresponder a una referencia y una referencia solo puede corresponder a una predicción. Las predicciones sobrantes cuentan como falsos positivos.
- Una categoría equivocada no es un acierto de esa categoría: puede producir un falso positivo para la categoría propuesta y una omisión de la referencia real.
- Las etiquetas múltiples válidas requieren evaluación que preserve cada categoría. No se fusionan referencias ni se fuerza exclusividad clínica para facilitar las métricas. Esta representación debe quedar resuelta antes de convertir y evaluar.
- Los cuadros se comparan en un sistema de coordenadas consistente. Registrar el escalado/relleno y recuperar las coordenadas originales para visualización.

Se usará un evaluador de detección fijado y comprobado, con su versión y parámetros registrados. Antes de usarlo con resultados reales, verificar con ejemplos controlados que cuente correctamente un acierto, una omisión, un duplicado y una categoría equivocada; esto comprueba el adaptador, no la calidad del modelo.

## 4. Métricas que se guardarán

Para **cada categoría**:

- Número de imágenes y anotaciones de referencia, para entender el tamaño de la evidencia.
- Precisión: de las marcas propuestas, cuántas coinciden con las referencias.
- Recall: de las referencias, cuántas encuentra el detector.
- F1, junto con el umbral de confianza fijado y sus TP/FP/FN.
- AP50 y AP50–95; la segunda resume la detección exigiendo distintos niveles de coincidencia espacial.
- Curva precisión–recall y ejemplos identificados de falsas alarmas, omisiones y confusiones entre las categorías.

Se informará además la media de AP de las dos categorías, manteniendo visibles los resultados de cada una. No se usará un porcentaje genérico de «accuracy» ni una media ponderada por frecuencia como único resultado.

Si una partición no tiene referencias positivas de una categoría, su AP/recall se informa como no evaluable y se explica; no se convierte esa ausencia en rendimiento perfecto. La clase poco frecuente puede producir resultados inestables.

Cuando sea viable, añadir intervalos de incertidumbre mediante remuestreo de imágenes/grupos, sin tratar cada diente como observación independiente. Si no hay grupos de pacientes, declarar que esos intervalos tampoco garantizan independencia por paciente. Nunca presentar solo un intervalo sin el conteo de soporte.

Referencia metodológica: [métricas de detección de Ultralytics](https://docs.ultralytics.com/guides/yolo-performance-metrics/). Consultada para las definiciones generales; no implica elegir una versión nueva de YOLO ni instalar sus herramientas.

## 5. Qué se elige con validación y qué queda congelado

Con entrenamiento se ajustan los pesos. Con validación se eligen el checkpoint, una posible iteración y el punto de operación. La prueba interna no participa en ninguna de esas elecciones.

- Criterio técnico propuesto para elegir checkpoint/configuración: mayor media de AP50–95 de las dos categorías en validación. Si empatan, preferir el experimento de referencia; no añadir variaciones por un empate.
- Para el primer prototipo, elegir un umbral global de confianza que maximice la media de F1 de ambas categorías en validación a IoU 0,50. En empate, preferir mayor recall medio y luego menor umbral. Es una regla experimental, no un umbral clínico validado.
- Si una clase no es evaluable en validación, no declarar elegido un umbral equilibrado para las dos; registrar la limitación y revisar la estrategia antes de congelarla.
- Registrar también los parámetros de supresión de cuadros y su tratamiento entre categorías; no asumir que sus valores predeterminados conservan todas las etiquetas requeridas.
- La selección para AP debe usar una salida de puntuaciones suficientemente amplia y parámetros de evaluación registrados. No confundir el umbral operativo que ve el usuario con un recorte alto de puntuaciones que trunque la curva AP.
- Antes de la prueba final, congelar pesos/hash, categorías, preprocesamiento, tamaño de entrada, umbral operativo, supresión de cuadros, versiones del entorno y versión del evaluador.

Se plantea una referencia pequeña y, como máximo, una comparación controlada adicional dentro del plazo, manteniendo el reparto. No se decide aumentar la búsqueda mirando el test.

## 6. Prueba final y ejemplos

Una vez congelada la versión, ejecutar el protocolo completo sobre la reserva interna. Guardar todas las predicciones originales con sus coordenadas y puntuaciones, además de métricas y errores. Incluir casos con y sin detecciones, sin convertir ausencia de marcas en diagnóstico de salud.

No cambiar el umbral tras observar omisiones del test para volver a anunciar el resultado como evaluación final independiente. Si un fallo exige depurar y volver a usar ese mismo test, declarar que pasó a ser exploratorio y que hace falta una reserva nueva para una afirmación independiente.

Los ejemplos de error se seleccionan con una regla registrada (por ejemplo, mayor puntuación de falsos positivos y omisiones por categoría) y con casos habituales adicionales. No mostrar solo imágenes exitosas. Comparar siempre contra la versión de referencia usada; los resultados son acuerdos con anotaciones de investigación, no sensibilidad/especificidad clínica acreditadas.

## 7. Velocidad y comportamiento del prototipo

Plan de latencia independiente, usando imágenes de validación para no consumir la reserva final durante desarrollo:

- Una imagen por solicitud, con el mismo tamaño de entrada, dispositivo y preprocesamiento del prototipo.
- Medir arranque/carga del modelo por separado.
- Hacer cinco calentamientos y registrar después 30 imágenes distintas de validación, o todas las disponibles si quedaran menos.
- Separar decodificación/preprocesamiento, inferencia, posprocesamiento y total del análisis local; sincronizar correctamente el trabajo de GPU al medirlo.
- Informar mediana y percentil 95, cantidad de imágenes, equipo y configuración. No extrapolar automáticamente ese tiempo a Railway ni al recorrido completo frontend–backend.

También comprobar archivo incompatible, resultado vacío y fallo de servicio, distinguiéndolos en el flujo. La revisión de permisos e integración pertenece a la etapa posterior.

## 8. Comparación mínima y criterios de presentación

Como referencia trivial, un detector que nunca emite marcas tiene recall y AP cero cuando existen positivos. Su precisión no está definida al no emitir predicciones; no se registra como 100 %. La referencia sirve para detectar un resultado vacío presentado erróneamente como bueno, no como competidor clínico.

Para presentar una demostración académica del experimento se requiere trazabilidad de datos, una inferencia real reproducible y métricas de ambas categorías con sus errores y límites. Si una categoría tiene AP/recall cero o no es evaluable, no se afirma que su detección esté demostrada. Una mejora sobre una referencia trivial no basta para afirmar utilidad clínica.

**No se fija un porcentaje mínimo clínico o una meta inventada.** El umbral de aceptación académica/uso previsto sigue pendiente de acuerdo con el docente y revisión odontológica. Si el rendimiento no sostiene la función propuesta, presentar el experimento con sus límites y aplazar o acotar la integración; no ocultar fallos con una interfaz.

## 9. Entregables y pendientes antes de ejecutar

Entregables de evaluación previstos: manifiestos del reparto, ficha de datos y decisiones, configuración y pesos identificados, predicciones completas, métricas por categoría, ejemplos de errores, medición de latencia y un informe del alcance realmente demostrado.

El [registro estructurado del diseño](CONFIG_EVALUACION_2026-10-25.json) conserva estas decisiones con estado de planificación, sin asignaciones ni resultados inventados.

Pendientes para ejecutarlos: decisiones sobre datos dudosos/negativos; imágenes de trabajo disponibles; duplicados y grupos revisados; particiones congeladas; entorno y modelo seleccionados/compatibles; entrenamiento autorizado y ejecutado. Ninguno de esos resultados se simula en este plan.

Las fuentes sobre separación y grupos son la [documentación de evaluación de scikit-learn](https://scikit-learn.org/stable/modules/cross_validation.html#cross-validation-iterators-for-grouped-data) y la [documentación de validación del detector](https://docs.ultralytics.com/modes/val/). No se instalaron bibliotecas para redactar este diseño.
