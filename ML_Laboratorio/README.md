# CORONYX — laboratorio ML independiente

> **Continuidad del 28/10/2026:** por elección del usuario, la versión compartida se incorpora a `ML_Laboratorio/` dentro de la rama `codex/ml-mvp` del backend. Se preparó una copia de 679 imágenes y se fijaron las particiones: 508 entrenamiento, 44 validación y 127 prueba interna. Se aplicaron exclusiones documentadas, preservando los originales. Todavía no se entrenó. Consultar [LAPTOP.md](LAPTOP.md) y [las decisiones del 28/10](CONTINUIDAD_MVP_2026-10-28.md). Las notas anteriores que siguen conservan el estado de cada fecha.

> **Continuidad del 27/10/2026:** este laboratorio ya es un repositorio Git independiente en `codex/ml-mvp`. Se crearon ramas locales para la futura integración en backend y frontend, conservando sus carpetas actuales en `feature/sebastian` y sus cambios. Se implementaron adquisición reanudable y auditoría por contenido, con 15 pruebas aprobadas. Ver [avance y ejecución de hoy](CONTINUIDAD_MVP_2026-10-27.md). El cierre del 25/10 que sigue es histórico; el recibo real de adquisición se guarda aparte en `data/adquisicion/recibo.json`.

**Colección original disponible:** 755 imágenes y dos JSON, aproximadamente 3,09 GB, con cero errores de adquisición. La copia depurada, las particiones y el modelo siguen pendientes.

**Control por contenido completado:** [resultado del 27/10](auditoria/INTEGRIDAD_IMAGENES_2026-10-27.md). Cero fallos de integridad/dimensiones, cero grupos de archivos o píxeles idénticos y ningún candidato con el filtro perceptual utilizado. Sigue sin comprobarse independencia por paciente; las etiquetas dudosas requieren tratamiento antes de preparar el experimento.

## Antecedentes del 25/10/2026

> **Continuidad actualizada, 25/10/2026:** leer [PLAN_DIA_2_2026-10-25.md](PLAN_DIA_2_2026-10-25.md) y [FICHA_DATOS_DENTEX_2026-10-25.md](FICHA_DATOS_DENTEX_2026-10-25.md). Se acordó DENTEX para un prototipo académico de caries y caries profunda; se recuperó el APF1, auditaron anotaciones/índices y revisaron ocho imágenes de entrenamiento más una de validación. Reglas, evaluación y adquisición están documentadas. La adquisición de trabajo y los controles de preparación siguen pendientes; no hay entrenamiento ni integración. El [plan del 23/10](PLAN_ML_2026-10-23.md) conserva el contexto previo.

**Revisión de etiquetas ejecutada:** [informe del 25/10](auditoria/REVISION_ETIQUETAS_2026-10-25.md). Se comprobaron los dos JSON diagnósticos y los índices de imágenes; un duplicado confirmado y 11 grupos con ambas categorías requieren tratamiento antes de la conversión. No se modificaron los datos originales ni se entrenó.

**Revisión visual ampliada:** tras autorización, se obtuvieron y mostraron tres ejemplos de entrenamiento con sus anotaciones originales. Las comparaciones están al final del mismo informe; no se generaron predicciones. Se suman a la muestra previa de validación y no constituyen una revisión visual completa del dataset.

**Reglas documentadas:** [preparación de etiquetas, versión 1](auditoria/REGLAS_PREPARACION_ETIQUETAS_2026-10-25.md) y [registro de casos](auditoria/REGISTRO_CASOS_2026-10-25.json). Un duplicado tiene acción futura definida y 15 grupos siguen pendientes. No se aplicaron depuraciones ni exclusiones y no se preparó una copia para entrenar.

**Muestra visual técnica mínima completada:** [cinco imágenes adicionales revisadas](auditoria/AMPLIACION_REVISION_VISUAL_2026-10-25.md), para un acumulado de ocho imágenes de entrenamiento más una inicial de validación. No hubo entrenamiento ni validación clínica.

**Evaluación definida:** [plan y métricas del 25/10](PLAN_EVALUACION_2026-10-25.md). El test publicado tiene un vocabulario diferente comprobado en sus 250 JSON; no se inventará su equivalencia. Se planifica una reserva interna previa al entrenamiento, con particiones aún sin generar y controles de calidad/grupos pendientes. No hay resultados de rendimiento.

**Adquisición y ficha cerradas como planificación:** [plan de descarga](PLAN_DESCARGA_2026-10-25.md), [ficha de datos](FICHA_DATOS_DENTEX_2026-10-25.md), [manifiesto de archivos](MANIFIESTO_ADQUISICION_2026-10-25.json) y [atribución](ATRIBUCION_DENTEX.md). Objetivo: 755 imágenes y dos JSON, unos 3,09 GB; nueve originales disponibles y 746 pendientes. La adquisición preferida es selectiva; no se inició la descarga de trabajo ni se aprobó la colección como lista para entrenar.

Preparación iniciada el 12/10/2026. Este directorio está fuera de `Backend_Dentista` y `Frontend_Dentista`. No contiene modelo entrenado, dataset de trabajo completo, entorno instalado ni integración funcional. Hay anotaciones, índices y ocho muestras originales de entrenamiento bajo `data/`, ignorado por Git. La muestra inicial de validación sigue en una carpeta temporal y se revalidará al reutilizarla.

## Primer objetivo

Definir una sola tarea radiográfica y demostrarla de forma reproducible antes de conectarla al sistema. Consultar `../Documentacion_CORONYX/Planificacion_CORONYX/PLAN_CONTINUIDAD_Y_ML_2026-10-12.md` y completar `DATASET_CHECKLIST.md`.

## Aislamiento previsto

- Entorno Python propio `.venv`, creado dentro de este directorio cuando se elija el framework y se comprueben los recursos. No utilizar dependencias del frontend/backend.
- Datos y resultados experimentales propios, sin conexión a PostgreSQL ni lectura automática del storage de pacientes.
- Empezar con datos públicos que permitan el uso previsto o material expresamente autorizado y desidentificado. Las imágenes ficticias de la demo no constituyen por sí mismas un dataset etiquetado de radiografías.
- Si se utiliza un notebook con GPU, transportar solo código y datos autorizados, sin claves/JWT/dumps del sistema. Guardar configuración y resultados para poder repetirlo.
- Servicio/puerto y credenciales propios cuando se construya inferencia. No cambiar los puertos ni las variables actuales de CORONYX.
- Integrar mediante contrato con Spring Boot después de evaluar el modelo, incluyendo validación humana y fallos controlados.

## Secuencia

1. Definir tipo de radiografía, hallazgo y salida; consultar al docente.
2. Revisar licencia, etiquetas, calidad, duplicados y grupos del dataset.
3. Guardar particiones de entrenamiento/validación/prueba y evitar fuga de información.
4. Elegir dependencias compatibles, fijar versiones y preparar el entorno separado.
5. Entrenar referencia, medir resultados y revisar errores.
6. Probar inferencia independiente; después preparar la integración.

La primera revisión necesita ficha de problema y datos candidatos, no una predicción simulada. No se generaron resultados ni porcentajes en esta preparación.
