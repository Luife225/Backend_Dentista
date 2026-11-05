# Preparación del MVP y continuidad entre computadoras

28/10/2026, hora de Perú. Se ejecutó el siguiente paso autorizado: copia para un primer experimento y reserva interna antes de entrenar. El usuario eligió publicar el ML **dentro del backend**. La única rama nueva para este avance es `mvp-ml`.

## Organización elegida

En GitHub, el código está bajo `Backend_Dentista/ML_Laboratorio/` en `mvp-ml`. El backend normal y el frontend conservan sus carpetas y ramas originales. Se preparó una carpeta de trabajo separada en `../tmp/Backend_ML` para publicar la rama del backend sin cambiar la carpeta donde se venía trabajando.

El repositorio local independiente del laboratorio se conserva como respaldo del trabajo inicial. El punto de continuidad compartido será la rama del backend. Las notas del 27/10 describen la organización anterior, antes de esta decisión.

## Reglas aplicadas a este primer experimento

La [política v1](preparacion/POLITICA_MVP_V1.json) se fijó antes de entrenar y sin usar resultados de un modelo.

- Se conservó la anotación 2335 y se omitió 2337 únicamente en la copia, tras repetir la comprobación de igualdad de todos los campos salvo ID.
- Se excluyeron del experimento las 15 imágenes con grupos de etiquetas pendientes. Se excluyó cada imagen completa; no se borraron solo cajas dudosas ni se eligió un diagnóstico por intuición. Los casos siguen sin resolución clínica en la fuente.
- Se excluyeron 55 imágenes de entrenamiento de origen y seis de validación sin etiquetas objetivo. No se declararon sanas ni negativos fiables. No se crearon imágenes sin etiquetas en esta copia.
- Se conservaron separadas caries y caries profunda; sus IDs de exportación son 0 y 1. Las otras condiciones permanecen en la fuente, fuera de las clases de este experimento.
- Se excluyen 76 imágenes en total y se conservan 679 en la copia. Cada exclusión registra su motivo y las anotaciones originales en el manifiesto.

**Limitación del experimento:** se evalúa una selección con etiquetas objetivo. Sus resultados no establecerán rendimiento sobre radiografías no seleccionadas o sanas. Esta restricción afecta también a validación y prueba y deberá acompañar cualquier métrica. No representa una corrección clínica de DENTEX.

## Particiones fijadas

El [manifiesto de particiones](preparacion/PARTICIONES_DENTEX_V1.json) conserva nombres, hashes, IDs, exclusiones, semilla y mapeo:

- Entrenamiento: **508 imágenes**, 1 657 anotaciones de caries y 445 de caries profunda.
- Validación: **44 imágenes**, 101 anotaciones de caries y 32 de caries profunda.
- Prueba interna: **127 imágenes**, 463 anotaciones de caries y 106 de caries profunda.

La prueba se obtuvo del conjunto original de entrenamiento, con semilla `20261025`, estratificación por presencia de clases y un orden determinista mediante SHA-256. Se mantuvieron fuera de la reserva las imágenes inspeccionadas previamente. La validación proviene exclusivamente de la validación oficial elegible.

La unidad de reparto es la imagen ante la ausencia de grupos relacionados identificados. Los controles anteriores no encontraron archivos/píxeles idénticos ni candidatos perceptuales dentro de los umbrales fijados; no demuestran independencia por paciente. No se dispone de IDs de paciente/estudio. El test público con taxonomía distinta sigue fuera del experimento.

## Copia y conversión

`datasets/dentex_mvp_v1/`, excluido de Git, contiene copias de las 679 imágenes, etiquetas YOLO, una vista COCO por partición, configuración de rutas local y recibo de preparación. No se usan enlaces que permitan modificar accidentalmente un original desde la copia.

Las cajas conservan su geometría, convirtiendo únicamente de píxeles a centro/ancho/alto normalizados. La verificación exige recuperar el cuadro original con error máximo de 0,00001 píxeles, además de comprobar hashes, categorías, conteos y pertenencia de cada imagen a una sola partición. Los JSON y las radiografías de origen permanecen intactos.

**Verificación ejecutada:** las 679 imágenes preparadas y sus originales coinciden por SHA-256, todas las cajas cumplen la tolerancia, los IDs de las vistas YOLO y COCO concuerdan y los conteos de las tres particiones coinciden con el manifiesto. Pasaron 19 pruebas locales, incluidas independencia de las listas, exclusión de imágenes inspeccionadas y reconstrucción de destinos en otra carpeta de clon.

Las particiones y la política sí se publican en Git; las radiografías, la copia grande, cachés, entornos, resultados y credenciales quedan excluidos. Un clon en la laptop recibe las reglas y el código, pero no descarga automáticamente los aproximadamente 3,09 GB de imágenes.

Se incluyeron aproximadamente 2,06 MB de metadatos públicos originales e índices bajo `fuentes/`, con atribución y conservación de bytes. Así la descarga selectiva no depende de una caché privada de esta computadora. Los destinos se recalculan dentro de la carpeta del clon; no se escribirá en las rutas antiguas del manifiesto histórico.

## Continuación

Consultar [LAPTOP.md](LAPTOP.md) para cambiar de rama y recuperar/preparar los datos en otro equipo. Después corresponde seleccionar definitivamente el modelo, comprobar licencias y entorno GPU, entrenar una referencia y evaluarla con el protocolo fijado. Esta preparación no autoriza ni ejecuta entrenamiento, descarga de pesos, inferencia, integración o despliegue.
