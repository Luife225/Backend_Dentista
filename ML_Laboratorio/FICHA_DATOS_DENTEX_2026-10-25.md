# CORONYX — ficha de datos DENTEX

> **Actualización de disponibilidad, 27/10/2026:** ya se obtuvieron y verificaron las 755 imágenes y los dos JSON originales. Los conteos e inventarios del 25/10 que siguen son históricos. Ver [ejecución y controles posteriores](CONTINUIDAD_MVP_2026-10-27.md). Las decisiones de etiquetas, copia derivada y particiones continúan pendientes.

Versión 1, 25/10/2026. **Ficha documentada con evidencias y pendientes. No certifica datos listos para entrenar ni validación clínica.**

## Identificación y procedencia

- Dataset: DENTEX, publicado por Ibrahim Ethem Hamamci y colaboradores para el reto MICCAI 2023.
- Fuente: [distribución del autor en Hugging Face](https://huggingface.co/datasets/ibrahimhamamci/DENTEX).
- Revisión fijada: `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93`.
- [Ficha de la revisión utilizada](https://huggingface.co/datasets/ibrahimhamamci/DENTEX/blob/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/README.md), [repositorio de los organizadores](https://github.com/ibrahimethemhamamci/DENTEX) y [artículo](https://arxiv.org/html/2305.19112v2).
- Población descrita por la ficha del autor: pacientes de 12 años o más, procedentes de tres instituciones. No se dispone aquí de una distribución completa de edades/sexo ni de cantidad verificable de pacientes únicos; no se extrapola a menores de 12 años ni a la población de CORONYX.
- Modalidad seleccionada: radiografía panorámica. Los archivos de imagen seleccionados del índice son PNG. No se mezclan bitewings, fotografías ni DICOM.

## Propósito dentro de CORONYX

Experimento académico para localizar dientes con dos categorías separadas, caries y caries profunda, y mostrarlas juntas como propuestas pendientes de revisión odontológica. Las cajas señalan el diente anotado; no se presentan como segmentación ni contorno exacto de una lesión.

El APF1 recuperado describe inferencia con marcas y confianza y revisión profesional (RF-15/RF-16), pero no especifica esas patologías. Las dos categorías son el alcance del prototipo acordado con el usuario. No se incluye una tarea de malformaciones ni recomendaciones automáticas de tratamiento.

## Composición comprobada

Subconjunto diagnóstico de entrenamiento de origen:

- 705 imágenes y 3 529 anotaciones originales.
- Caries: 2 189 anotaciones en 623 imágenes.
- Caries profunda: 578 anotaciones en 321 imágenes.
- Dientes impactados: 604 anotaciones; lesión periapical: 158.
- 27 imágenes sin anotaciones; 55 sin las dos categorías objetivo, incluidas esas 27. No se declaran sanas por ese motivo.

Validación de origen:

- 50 imágenes y 182 anotaciones originales.
- Caries: 101 anotaciones en 37 imágenes.
- Caries profunda: 32 anotaciones en 20 imágenes.
- Dientes impactados: 40 anotaciones; lesión periapical: nueve.
- Cuatro imágenes sin anotaciones; seis sin las categorías objetivo, incluidas esas cuatro.

Una radiografía puede contener varias marcas y ambas categorías. Los conteos son anotaciones e imágenes, no pacientes independientes. Los valores corresponden a las fuentes originales, antes de deduplicar o excluir cualquier dato.

## Anotaciones y mapeo

Los JSON diagnósticos contienen `images`, `annotations` y tres listas de categorías. Se relacionan por ID de imagen. Cada anotación tiene un cuadro `bbox` en píxeles, en el orden x, y, ancho, alto, además de polígono/área y categorías de cuadrante, numeración y diagnóstico.

Diagnósticos originales: 0 = Impacted, 1 = Caries, 2 = Periapical Lesion, 3 = Deep Caries. Para una futura exportación de dos clases se propuso 0 = caries y 1 = caries profunda; ese nuevo mapeo no se ha aplicado. Las otras condiciones se conservan en la fuente y no equivalen a salud.

Los artículos describen anotación inicial y revisión por profesionales. Esa procedencia no sustituye nuestra comprobación de formato ni resuelve automáticamente los casos detectados. La interpretación de etiquetas múltiples sigue el [resultado de la consulta del protocolo](auditoria/PROTOCOLO_ETIQUETAS_MULTIPLES_2026-10-25.md).

## Calidad y revisión realizadas

- IDs, categorías, referencias y geometría contra dimensiones declaradas revisados en los dos JSON; sin errores detectados en esos controles.
- Las 755 imágenes figuran de manera no ambigua en sus índices de archivo. Falta obtener/decodificar las 746 originales que aún no están disponibles localmente.
- Un duplicado de anotación exacto salvo ID: 2335/2337 en `train_377.png`. La regla futura conserva 2335; todavía no se aplicó.
- Once grupos con caries y caries profunda bajo la misma identidad registrada y cuatro repeticiones adicionales con cuadros distintos: pendientes, sin diagnóstico de error clínico confirmado.
- Ocho imágenes de entrenamiento y una inicial de validación inspeccionadas técnicamente. No hubo validación clínica. Se observó variación de resolución y márgenes negros que deberá respetarse al transformar coordenadas.
- No se observaron identificadores personales legibles en las nueve muestras revisadas; no se certifica anonimización de todas las imágenes/metadatos. La fuente declara medidas de privacidad; cualquier identificador encontrado en la revisión posterior debe bloquear su uso/compartición hasta resolverlo.

Evidencia: [auditoría estructural](auditoria/REVISION_ETIQUETAS_2026-10-25.md), [ampliación visual](auditoria/AMPLIACION_REVISION_VISUAL_2026-10-25.md), [reglas](auditoria/REGLAS_PREPARACION_ETIQUETAS_2026-10-25.md) y [registro de casos](auditoria/REGISTRO_CASOS_2026-10-25.json).

## Grupos, duplicados y separación

Los registros de imagen solo incluyen ID de imagen, nombre, ancho y alto. No contienen identificadores explícitos de paciente/estudio. Su cantidad y distribución entre particiones siguen desconocidas. Un ID de imagen no es un ID de paciente.

El filtro de nombres y CRC32/tamaño entre entrenamiento/validación no encontró candidatos iguales; no demuestra ausencia de duplicados por contenido, variantes o pacientes compartidos. Estos controles se completarán al disponer de la colección de trabajo.

El [plan de evaluación](PLAN_EVALUACION_2026-10-25.md) reserva una prueba interna antes de entrenar, aproximadamente el 20 % de las imágenes elegibles de entrenamiento de origen, respetando grupos y dejando fuera las ocho imágenes de depuración. Se mantiene validación para ajustes. No existen aún listas finales ni aumentos. Si no se pueden identificar pacientes, esa limitación debe acompañar la evaluación.

## Test publicado: compatibilidad sin resolver

Se inspeccionaron su índice y 250 JSON LabelMe con 1 600 polígonos, sin obtener ni visualizar radiografías de test. Su vocabulario difiere del entrenamiento y no se dispone de equivalencia verificada para ambas categorías. Los códigos no se reinterpretan por similitud numérica o traducción intuitiva.

El test oficial no se utiliza en la evaluación principal, entrenamiento o ajustes previstos. La prueba interna no se presentará como benchmark oficial ni como validación externa. Ver [comprobación de esquema](auditoria/ESQUEMA_TEST_2026-10-25.json).

## Licencia, atribución y uso

La distribución fijada declara **CC BY-NC-SA 4.0**. El usuario indicó que el propósito es una demostración académica. El uso se planifica con crédito a los autores, fuente y licencia, indicación de cambios y cumplimiento de NoComercial y CompartirIgual cuando corresponda a material adaptado que se distribuya. [Condiciones oficiales](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.es).

Existe una discrepancia: los artículos mencionan CC BY, mientras la distribución y el README declaran CC BY-NC-SA. Se conserva para estos archivos la condición declarada por la distribución; no se concluye permiso comercial ni se alteran condiciones por la licencia de un artículo. Otros derechos, incluida privacidad, no quedan automáticamente resueltos por esa declaración.

La [atribución preparada](ATRIBUCION_DENTEX.md) acompaña el material. La licencia del dataset es distinta de las del código y pesos del modelo, aún sin selección definitiva. Los datos no se incorporarán al repositorio de aplicación ni al despliegue de inferencia como requisito operativo.

## Disponibilidad y espacio

Colección original necesaria: 755 PNG y dos JSON, **3 088 539 253 bytes (aproximadamente 3,09 GB)**. Nueve originales ya están disponibles y verificados, uno en una carpeta temporal; faltan 746. No se descargó el dataset de trabajo completo.

Las posiciones y tamaños publicados permiten planificar adquisición por registros comprimidos seleccionados. La transferencia pendiente calculada es aproximadamente 2,27 GB más gastos auxiliares/reintentos. Ver [plan de adquisición](PLAN_DESCARGA_2026-10-25.md) y [manifiesto](MANIFIESTO_ADQUISICION_2026-10-25.json).

La medición de espacio y los márgenes son una comprobación de almacenamiento, no una prueba del entorno de entrenamiento. La RTX 4060 detectada tiene unos 8 GB de VRAM; PyTorch/CUDA y un entrenamiento real siguen sin comprobarse.

## Decisión y pendientes

**Decisión:** continuar con DENTEX para preparar el experimento académico. La ficha y los planes están documentados; todavía no se declara la colección lista para entrenar.

Antes de entrenar: adquirir y verificar originales; aplicar la depuración técnica en una copia; resolver o documentar el tratamiento de los casos y negativos dudosos; revisar duplicados/relaciones; fijar particiones; verificar representación de etiquetas múltiples, entorno, modelo y licencias correspondientes. Métricas y procedimiento están definidos, pero no se ha acordado una meta numérica clínica o académica ni medido rendimiento.

No se prometen porcentajes, generalización clínica, independencia por paciente o disponibilidad comercial a partir de esta ficha.
