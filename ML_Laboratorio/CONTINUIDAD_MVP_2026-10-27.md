# CORONYX — avance aislado hacia el MVP de ML

27/10/2026, hora de Perú. Este documento continúa el cierre de planificación del día 2. El objetivo sigue siendo proponer dientes con caries o caries profunda en panorámicas para revisión del odontólogo.

## Ramas y conservación del trabajo existente

El laboratorio se convirtió en un repositorio Git independiente, con rama local `codex/ml-mvp`. El commit inicial `4528edc` conserva la documentación y las auditorías que ya existían. Las imágenes, cachés, modelos, entornos y resultados siguen excluidos de Git. No se configuró un remoto ni se publicó código.

Se crearon también ramas locales `codex/ml-mvp` para la futura integración:

- Backend: base `0c2d73ff6b0d2710978b443422b84f99e299b194`.
- Frontend: base `99ca42e537d6885ad56ddb64ef2490904754e4de`.

Ambas parten de los commits que estaban abiertos al comenzar esta sesión. No se cambiaron las carpetas de trabajo a esas ramas: siguen en `feature/sebastian`. No apareció una rama local o remota ya conocida llamada `feature/develop`; sí existe `develop`. No se hizo fetch, merge, rebase, reset, stash ni push.

El backend tenía modificaciones locales en `docs/CONTINUIDAD_ANTIGRAVITY.md` y `docs/demo/MANIFIESTO_PRESENTACION.json`. Se registraron sus hashes y el estado inicial en `../tmp/estado_antes_ml_2026-10-27.json`, y se comprobó que se conservan. El frontend estaba limpio. Las nuevas ramas de integración apuntan a los commits originales, sin incluir ni mover esos cambios locales.

El código de ML se versiona dentro de este laboratorio. Una rama en backend o frontend no incluye archivos situados fuera de su repositorio; por eso se mantiene esta separación. Cuando llegue la integración, se usará una carpeta de trabajo aislada para cada rama, preservando las actuales.

## Preparación ejecutada en esta sesión

Se implementaron herramientas propias de adquisición, auditoría por contenido y consulta de estado. No dependen de Java, Node, PostgreSQL, credenciales ni almacenamiento de pacientes.

La descarga utiliza el manifiesto ya acordado, la revisión fijada de DENTEX y solicitudes parciales. Verifica los hashes de anotaciones e índices, cabecera y nombre de entrada ZIP, respuesta HTTP exacta, tamaño, CRC32, decodificación PNG y dimensiones. Guarda SHA-256 de cada original. Si un archivo existente difiere, lo conserva y detiene ese caso. No descarga el ZIP completo como alternativa automática.

El recibo real se guarda en `data/adquisicion/recibo.json`. El manifiesto del 25/10 conserva su estado histórico de planificación; las acciones reales se registran por separado. La ejecución puede reanudarse: se vuelven a verificar los originales ya disponibles.

La auditoría posterior vuelve a comprobar el contenido y busca grupos con igual archivo o píxeles decodificados. También hace un filtro perceptual con umbrales fijados antes de comparar. Sus candidatos requieren revisión: no certifican que dos imágenes sean del mismo paciente ni garantizan descubrir todas las variantes relacionadas. No se cambian etiquetas ni se asignan particiones con esta herramienta.

**Adquisición completada:** 755 originales, 705 de entrenamiento de origen y 50 de validación, más los dos JSON sin modificaciones. Total: 3 088 539 253 bytes, aproximadamente 3,09 GB. Cero errores. Se reutilizaron nueve muestras; se descargaron dos imágenes para la comprobación inicial y 744 en la ejecución principal. El recibo final registra las primeras dos como archivos existentes revalidados. No se adquirieron imágenes del test público ni otros subconjuntos.

**Auditoría por contenido completada:** [resumen técnico](auditoria/INTEGRIDAD_IMAGENES_2026-10-27.md) y [resultado completo](auditoria/INTEGRIDAD_IMAGENES_2026-10-27.json). Las 755 imágenes coinciden por integridad y dimensiones, sin grupos de archivos idénticos, grupos de píxeles decodificados idénticos ni parejas candidatas con el filtro perceptual fijado. No se observaron claves de metadatos PNG al abrir estos archivos. Esto no acredita ausencia de identificadores dentro de los píxeles ni independencia entre pacientes. El filtro perceptual puede omitir variantes relacionadas.

El duplicado confirmado anteriormente es una repetición de **anotación**, no un archivo de imagen repetido. Sigue presente en la fuente y no se ha depurado. Los 15 grupos de etiquetas continúan pendientes, igual que las decisiones sobre ausencia de etiquetas. Las fuentes están disponibles; no hay todavía una copia declarada lista para entrenar.

## Comprobaciones del código

Quince pruebas locales cubren respuestas que ignoran Range, rangos incorrectos, respuestas truncadas o excesivas, CRC y dimensiones incompatibles, descompresión acotada, rutas fuera de la carpeta prevista, conservación de originales en conflicto, selección de la entrada ZIP correcta y reanudación sin red. También comprueban igualdad de píxeles con metadatos diferentes, transparencia, detección de repeticiones entre particiones y distinción entre candidato perceptual y duplicado confirmado. Pasaron las quince.

Se utilizó Python 3.12.14 y Pillow 12.3.0 ya disponibles en el equipo. No se instalaron paquetes ni se creó un entorno. La GPU informa NVIDIA GeForce RTX 4060, 8188 MiB y controlador 617.42; esto no comprueba todavía PyTorch/CUDA ni la capacidad de cualquier configuración de entrenamiento.

## Lo que falta para una primera inferencia real

1. Documentar y aplicar a una copia el tratamiento del duplicado de anotación, los 15 grupos dudosos y las imágenes sin etiquetas objetivo. Para una primera referencia puede proponerse excluir imágenes completas cuyo caso siga sin resolver, registrando el cambio de distribución; no se han aplicado esas exclusiones.
2. Fijar las listas de entrenamiento, validación y prueba interna según el plan de evaluación, manteniendo fuera de la prueba las ocho imágenes ya revisadas y sus variantes relacionadas. Declarar la limitación por independencia de pacientes no verificable.
3. Elegir definitivamente el modelo, comprobar sus licencias y preparar el entorno separado con GPU.
4. Entrenar la referencia, evaluar por categoría y comprobar una inferencia real con imagen, cuadros y categorías. Después integrar en las ramas reservadas de backend y frontend.

No hubo instalación de modelo, descarga de pesos, entrenamiento, generación de predicciones ni cambios en el sistema desplegado. La revisión humana sigue siendo parte del alcance del prototipo.

## Cómo consultar o repetir el avance

Desde la carpeta del laboratorio, con Python y Pillow disponibles:

```powershell
python -m herramientas.estado_mvp
python -m unittest discover -s tests -v
```

El estado local de adquisición, sin red ni escritura de imágenes, se consulta con:

```powershell
python -m herramientas.adquirir_dentex
```

Para adquirir o reanudar exactamente la colección del manifiesto:

```powershell
python -m herramientas.adquirir_dentex --descargar --trabajadores 4
```

Una vez completo el recibo:

```powershell
python -m herramientas.auditar_imagenes --fecha 2026-10-27
```

En esta computadora se utilizó el intérprete existente `C:/Users/USER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; los comandos anteriores no requieren usar ese mismo directorio en otro equipo. Para reproducir la adquisición allí, primero habrá que disponer de los JSON e índices originales verificados y regenerar/revisar las rutas locales del manifiesto; no se cambia el manifiesto silenciosamente.
