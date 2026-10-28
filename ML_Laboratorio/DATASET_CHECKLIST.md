# Revisión de dataset — completar antes de entrenar

Estado al 25/10/2026: DENTEX elegido para evaluación inicial de un prototipo académico de caries y caries profunda. Auditoría incompleta; ningún control amplio se considera aprobado por una sola muestra. Ver [detalle y evidencias del día 2](PLAN_DIA_2_2026-10-25.md).

Antecedente del 23/10/2026: [el plan inicial](PLAN_ML_2026-10-23.md) registró candidatos a nivel documental. Posteriormente se leyeron anotaciones de entrenamiento/validación y se obtuvo una muestra temporal autorizada. No se descargó el dataset completo ni se entrenó.

- [x] Objetivo del prototipo acordado y relación documental con RF-15/RF-16 establecida; aceptación académica final pendiente.
- [x] Modalidad seleccionada: panorámicas; muestra PNG comprobada. Verificación de todos los archivos pendiente.
- [x] Fuente, revisión y licencia declarada documentadas para el propósito académico; atribución y condiciones registradas. Discrepancia de licencia en artículos y permisos del futuro modelo siguen delimitados en la ficha; no se declara permiso comercial.
- [ ] Permisos de tratamiento/traslado y ausencia de información identificable en imagen/metadatos.
- [x] Cantidades de imágenes/anotaciones y conteos por clase registrados. Número de pacientes/estudios únicos desconocido y declarado; no se equiparan imágenes con pacientes.
- [ ] Etiquetas disponibles: imagen, cajas o máscaras; autoría y calidad del etiquetado.
- [x] Muestra visual técnica revisada y limitaciones registradas: ocho imágenes de entrenamiento y una inicial de validación. No acredita validación clínica ni revisión de toda la colección.
- [ ] Duplicados y variantes identificados; reglas de exclusión registradas.
- [x] Limitación por ausencia de IDs explícitos de paciente/estudio documentada. No demuestra independencia entre particiones.
- [ ] Particiones fijas sin cruce de paciente/estudio o duplicados relacionados.
- [ ] Transformaciones ajustadas solo con entrenamiento; variantes generadas después de separar grupos.
- [ ] Métricas, referencia trivial y criterio de aceptación acordados según la tarea.
- [x] Diseño técnico de evaluación documentado: métricas por categoría, referencia sin detecciones, selección solo con validación y medición de latencia. [Plan](PLAN_EVALUACION_2026-10-25.md). El criterio numérico académico/clínico sigue sin acordarse.
- [ ] Recursos y tiempo disponibles para entrenar.
- [ ] Feedback docente y decisión registrada: usar, buscar alternativa o reducir alcance.

Registrar los datos reales al seleccionar el dataset. No llenar estos apartados con cifras de ejemplo.

## Evidencia parcial disponible

- APF1 recuperado: RF-15 y RF-16 describen inferencia y revisión profesional, sin listar caries o malformaciones. Se acordó conservar caries y caries profunda para el primer experimento.
- Entrenamiento diagnóstico: 705 imágenes; 2 189 anotaciones de caries y 578 de caries profunda. Validación: 50 imágenes; 101 y 32 anotaciones, respectivamente. Son marcas, no pacientes ni imágenes independientes por categoría. Test no auditado.
- Una imagen de validación revisada: `val_33.png`, cinco cuadros de caries y uno de caries profunda; dimensiones y límites comprobados, sin validación clínica.
- Licencia declarada CC BY-NC-SA 4.0 y propósito académico confirmado. Falta completar procedencia reproducible y aplicación de condiciones al material que se comparta; esto no aprueba código, pesos ni uso comercial.
- Duplicados, identificadores de pacientes/estudios, independencia de particiones y etiquetas múltiples siguen pendientes. No se han generado aumentos ni realizado conversiones.

## Revisión estructural ejecutada el 25/10/2026

Ver [informe de revisión de etiquetas](auditoria/REVISION_ETIQUETAS_2026-10-25.md) y [resultado detallado](auditoria/resultado_etiquetas_2026-10-25.json).

- [x] Fijar revisión de fuente y hashes de los JSON diagnósticos consultados.
- [x] Comprobar IDs, categorías, referencias y geometría declarada en entrenamiento/validación; no se detectaron errores en esos controles.
- [x] Verificar nombres de las 755 imágenes en los índices oficiales, sin obtener nuevas radiografías.
- [x] Buscar anotaciones repetidas: un registro idéntico excepto ID confirmado en entrenamiento.
- [x] Identificar etiquetas múltiples: 11 grupos en entrenamiento con ambas categorías objetivo bajo la misma identidad dental registrada; significado físico/clínico pendiente.
- [x] Contar imágenes sin anotaciones: 27 en entrenamiento y cuatro en validación; significado como negativos pendiente.
- [x] Revisar campos de agrupación: los registros de imagen no contienen identificadores explícitos de paciente/estudio; independencia no demostrada.
- [ ] Resolver y documentar tratamiento de registros duplicados y grupos señalados en una copia derivada.
- [x] Documentar [reglas de preparación, versión 1](auditoria/REGLAS_PREPARACION_ETIQUETAS_2026-10-25.md): duplicado exacto, etiquetas diferentes, identidad repetida y condiciones de una futura exportación.
- [x] Crear [registro de casos](auditoria/REGISTRO_CASOS_2026-10-25.json): un duplicado con acción futura definida y 15 grupos pendientes, sin aplicar cambios.
- [ ] Aplicar y verificar las reglas en una copia derivada cuando se avance a preparación; resolver o documentar el tratamiento de sus casos pendientes antes de declararla lista para entrenar.
- [x] Revisar visualmente los tres casos prioritarios (`train_377.png`, `train_481.png`, `train_440.png`), con dimensiones reales comprobadas y comparaciones guardadas. Interpretación clínica y resolución de etiquetas pendientes.
- [x] Completar la muestra visual técnica mínima con cinco imágenes adicionales seleccionadas antes de verlas. Ver [ampliación y figuras](auditoria/AMPLIACION_REVISION_VISUAL_2026-10-25.md).
- [x] Consultar artículos y documentación accesible de los autores sobre etiquetas múltiples; ver [resultado](auditoria/PROTOCOLO_ETIQUETAS_MULTIPLES_2026-10-25.md).
- [ ] Resolver la regla específica para caries/caries profunda simultáneas y su representación en la copia derivada. La consulta documental no certificó los 11 casos ni autorizó eliminar una categoría.
- [ ] Comprobar duplicados por contenido y variantes entre particiones; el filtro por nombres/CRC/tamaño no demuestra su ausencia.
- [x] Inspeccionar el esquema del test público: 250 JSON LabelMe y vocabulario distinto de entrenamiento; [resultado](auditoria/ESQUEMA_TEST_2026-10-25.json). No hay mapeo clínico verificado para ambas categorías ni se obtuvieron sus radiografías.
- [x] Definir la ruta inicial de prueba interna reservada antes de entrenar, con justificación del cambio y límites. [Plan de evaluación](PLAN_EVALUACION_2026-10-25.md).
- [ ] Resolver calidad/grupos, generar y congelar manifiestos de las particiones; mantener imágenes de depuración fuera de la reserva final.

## Adquisición y ficha documentadas

- [x] Completar [ficha de datos](FICHA_DATOS_DENTEX_2026-10-25.md) con propósito, procedencia, conteos, etiquetas, evidencia, licencia, evaluación y pendientes.
- [x] Completar [plan de descarga](PLAN_DESCARGA_2026-10-25.md) y [manifiesto de adquisición](MANIFIESTO_ADQUISICION_2026-10-25.json): 755 imágenes y dos JSON; nueve originales reutilizables y 746 pendientes en la medición actual.
- [x] Medir espacio actual y presupuestar datos, posible copia, entorno, resultados y margen libre. Es una previsión de almacenamiento, no una prueba de recursos de entrenamiento.
- [x] Preparar [atribución de DENTEX](ATRIBUCION_DENTEX.md) para acompañar el material.
- [x] Obtener/verificar la colección original de trabajo y registrar el recibo real: completado el 27/10/2026, 755 imágenes y dos JSON, cero errores de adquisición. El manifiesto del 25/10 conserva las acciones previstas; el recibo real está en `data/adquisicion/recibo.json`. No equivale a datos depurados ni particiones fijadas.

Ficha y planes completos no equivalen a datos depurados, particiones congeladas, entorno comprobado o modelo entrenado.

## Controles ejecutados el 27/10/2026

- [x] Completar la adquisición de los 755 originales y dos JSON con recibo real; aproximadamente 3,09 GB y cero errores.
- [x] Verificar tamaño, CRC32, SHA-256, decodificación y dimensiones reales de todas las imágenes.
- [x] Comparar SHA-256 de archivos y píxeles decodificados entre los 755 originales: cero grupos repetidos encontrados. Este control es independiente del duplicado de anotación ya confirmado.
- [x] Ejecutar el filtro perceptual prefijado: cero candidatos; no garantiza detectar todas las variantes relacionadas ni demuestra independencia por paciente.
- [ ] Revisar información identificable dentro de los píxeles a escala de toda la colección; no aparecieron claves de metadatos PNG, pero eso no sustituye esta revisión.
- [ ] Aplicar las decisiones de etiquetas y congelar particiones antes de entrenar.

Ver [informe técnico](auditoria/INTEGRIDAD_IMAGENES_2026-10-27.md) y [continuidad del MVP](CONTINUIDAD_MVP_2026-10-27.md).

Estos subcontroles no marcan como completada la auditoría clínica, visual ni de particiones de la lista principal. Los originales se conservaron sin cambios.
