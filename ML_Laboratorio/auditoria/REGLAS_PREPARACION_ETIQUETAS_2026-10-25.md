# CORONYX — reglas de preparación de etiquetas DENTEX

Versión 1, 25/10/2026. Alcance: documentación para el prototipo académico de caries y caries profunda. **Acciones documentadas, todavía no aplicadas a los datos.** No se generó una copia de entrenamiento, no se convirtió ningún archivo y no se entrenó.

## Regla 1 — preservar la fuente y poder rastrear cada decisión

Los JSON y las radiografías originales se conservan intactos. Toda depuración futura se realiza en una copia derivada identificada, con mapeo de categorías y particiones registrados.

Fuente fijada: revisión `7b27ccc8e342dcb774f69adc6ca5e6c09fefce93` de [DENTEX](https://huggingface.co/datasets/ibrahimhamamci/DENTEX).

Cada decisión debe registrar: partición, nombre e ID de imagen, IDs de anotación originales, motivo, evidencia, responsable, fecha, acción sobre la copia y efecto en los conteos. El [registro de casos](REGISTRO_CASOS_2026-10-25.json) ya identifica los casos actuales y contiene hashes de sus anotaciones de origen. Los campos de resolución que todavía no existen permanecen vacíos; no se inventa revisión profesional.

## Regla 2 — duplicado exacto: conservar una sola anotación en la futura copia

Una repetición solo se considera duplicado exacto si **todos los campos coinciden salvo `id`**, incluidos imagen, cuadro, polígono, área, cuadrante, número de diente y diagnóstico. Compartir cuadro, categoría o número de diente por sí solo no basta.

Regla determinista para la copia: conservar el ID numérico menor y registrar los demás como omitidos por duplicación exacta.

Caso confirmado:

- Partición: entrenamiento.
- Imagen: `train_377.png`, ID 447.
- Conservar: anotación **2335**.
- Omitir de la copia: anotación **2337**.
- Motivo: contenido idéntico excepto ID, verificado en la auditoría.
- Efecto previsto si se aplica únicamente esta regla: una anotación de caries menos, sin cambiar el número de imágenes. No se presentan estos conteos previstos como una copia ya generada.

Esta es una depuración técnica, sin elección entre diagnósticos. Los IDs originales y los dos registros seguirán disponibles en la fuente. Antes de aplicar la regla a otra revisión del dataset, repetir la comprobación; no ejecutar una eliminación solo porque coincida el ID.

## Regla 3 — diagnósticos diferentes: no deduplicar ni elegir uno automáticamente

Los **11 grupos** que registran caries y caries profunda bajo una misma imagen, cuadrante y número de diente quedan como `pendiente_revision`. Ese estado describe una duda de preparación y no confirma un error clínico.

- Conservar ambos registros originales y sus cuadros.
- No sustituir caries por caries profunda por una supuesta prioridad de gravedad.
- No fusionar cuadros ni borrar una categoría solo por solapamiento.
- No usar el código de diente como prueba suficiente de identidad física: `train_440.png` muestra cuadros separados con el mismo código.
- Revisar por separado el significado de las categorías y la correspondencia de la numeración con la imagen.

Si la documentación del protocolo o una revisión profesional sustentan ambas categorías, conservarlas con una representación de entrenamiento/evaluación que mantenga esa información. Si se sustenta una corrección clínica o de numeración, registrar quién la indicó y su evidencia antes de aplicarla a la copia. La asistencia técnica puede verificar coordenadas y registros; no decide un diagnóstico por apariencia.

La [consulta documental realizada](PROTOCOLO_ETIQUETAS_MULTIPLES_2026-10-25.md) no encontró una regla específica suficiente para resolver estos 11 grupos. No se considera cerrada esa duda por haber leído el artículo.

## Regla 4 — misma categoría repetida con cuadros distintos: revisión técnica adicional

Hay **cuatro grupos adicionales**, distintos del duplicado exacto, que repiten categoría, cuadrante y número de diente pero tienen cuadros diferentes: `train_467.png`, `train_85.png`, `train_523.png` y `train_644.png`.

Conservar los registros y marcar `pendiente_revision`. La revisión debe determinar si los cuadros señalan regiones distintas, si hay un problema de numeración o si representan la misma instancia. La coincidencia de categoría o un IoU alto no autoriza deduplicarlos.

Los otros grupos con varios diagnósticos y los pares muy solapados se mantienen como observaciones de auditoría. **No se elimina todo grupo con varias condiciones**, ni se suman avisos solapados como si fueran errores independientes.

## Regla 5 — un caso pendiente no puede perderse al exportar

El registro actual contiene un duplicado técnico con acción futura definida y 15 grupos pendientes de interpretación o correspondencia. Las reglas anteriores no excluyen hoy ninguna imagen.

Antes de declarar una exportación lista para entrenar, todo caso pendiente que afecte a esa exportación debe tener una resolución trazable:

1. **Conservar la información:** existe evidencia suficiente y la representación elegida admite las etiquetas correspondientes.
2. **Corregir una copia:** existe una corrección documentada y atribuible; conservar los valores de origen y su diferencia con la copia.
3. **Excluir del experimento por duda sin resolver:** si se adopta esa opción, excluir la imagen completa de la copia de trabajo y registrar el motivo y el cambio de distribución. No eliminar solamente la caja dudosa dejando la región como si fuera fondo sin caries. Esta exclusión no implica que el diagnóstico original sea incorrecto y no se ha ejecutado.

Una imagen que tenga varios avisos recibe una sola decisión coherente. Los grupos de paciente/estudio, si llegan a identificarse, deben respetarse al definir las particiones. La exclusión o corrección se fija antes del entrenamiento; no se decide después de ver qué opción mejora resultados ni se trasladan imágenes dudosas a la prueba final.

Si ninguna de las tres opciones puede justificarse, la exportación afectada continúa pendiente. Eso permite seguir con otras revisiones técnicas sin tratar datos incompletos como preparados.

## Regla 6 — ausencia de etiqueta no equivale a diagnóstico de salud

Las 27 imágenes de entrenamiento y cuatro de validación sin anotaciones permanecen identificadas como tales. No se crea una clase `sano` ni se declaran negativos fiables hasta aclarar el protocolo de anotación. Las imágenes con otras condiciones pero sin nuestras categorías tampoco se convierten en ejemplos de salud general.

Este control permanece separado de los 16 casos registrados: no es una lista de pacientes enfermos o sanos. No hay una política de eliminación masiva de imágenes sin etiquetas.

## Comprobaciones de una futura copia

Cuando se autorice preparar la copia, verificar:

- Que los hashes de la fuente no hayan cambiado.
- Que el duplicado confirmado aparezca una sola vez y se registre el ID omitido.
- Que ningún caso pendiente quede convertido silenciosamente en una sola categoría o en fondo.
- Que el mapeo se conserve: diagnóstico original 1 = caries y 3 = caries profunda; cualquier nuevo ID de exportación debe tener correspondencia documentada.
- Que las particiones y los conteos antes/después sean reproducibles, con cada modificación explicada.
- Que una muestra superpuesta de la copia conserve la localización y las etiquetas previstas.
- Que las reglas no se apliquen a validación o prueba para mejorar artificialmente los resultados. Toda revisión de evaluación debe estar definida y reportada antes de medir rendimiento.

## Estado de esta tarea del día 2

**Documentación de reglas: completada.** El registro lista el duplicado confirmado y los casos pendientes. Su aplicación, la aclaración de etiquetas clínicas/numeración, la revisión de negativos y la preparación final de datos continúan pendientes. No hay conversión, corrección de originales, exclusión de imágenes ni entrenamiento ejecutados.
