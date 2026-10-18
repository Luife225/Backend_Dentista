# CoroVoz — primera versión sin API de pago

## Qué hace cada parte

- `IntentClassifier`: modelo supervisado de centroides TF-IDF. Se entrena al iniciar el backend con las preguntas etiquetadas de `src/main/resources/corovoz/training.tsv`. Calcula la similitud entre la pregunta y cada categoría. No es un modelo generativo y no se reentrena con consultas de pacientes.
- `CoroVozHelp`: respuestas revisadas y destinos permitidos para trece categorías: citas nuevas, cambios de citas, nuevos pacientes, búsqueda de pacientes, odontograma, historia clínica, documentos, notificaciones, uso del asistente, dashboard, inventario, caja y teleodontología. Similitud, diferencia entre las dos mejores categorías y cobertura del vocabulario determinan cuándo pedir aclaración. La similitud no es una probabilidad de acierto.
- `DentalCommandParser`: gramática de comandos, no ML. Reconoce una pieza permanente FDI, una condición y una cara. No hace diagnósticos. Si omites la cara propone GENERAL, visible en la confirmación. Observaciones solo después de «observación» o «nota».
- `CoroVozController`: recibe texto con la sesión JWT existente. General se permite a ODONTOLOGO y RECEPCIONISTA; interpretar odontograma solo a ODONTOLOGO con clínica activa. Los endpoints no escriben en BD ni llaman proveedores externos.

## Contrato

`POST /api/v1/corovoz/general` con `{ "text": "¿Cómo registro un paciente?" }` devuelve `intent`, `answer`, `route`, `targetLabel`, `understood`. Una consulta desconocida devuelve `understood=false` y ningún enlace. Recepción no recibe instrucciones clínicas ni enlace al odontograma/historia.

`POST /api/v1/corovoz/odontograma/interpretar` con `{ "text": "Caries en pieza treinta y seis cara oclusal. Nota: control." }` devuelve `understood`, `message` y `proposal` (`piece`, `surface`, `condition`, `note`). Si es ambiguo, `proposal=null`. Máximo de entrada: 600 caracteres; observación: 300.

Condiciones: CARIES, RESTAURADO, CORONA, EXTRAIDO, ENDODONCIA, IMPLANTE, FRACTURA.
Caras: GENERAL, OCLUSAL, VESTIBULAR, LINGUAL, MESIAL, DISTAL. «Palatina» se normaliza al código LINGUAL que admite la interfaz actual.
Piezas: 11–18, 21–28, 31–38, 41–48. La interfaz actual no incluye dentición temporal.
Se rechazan múltiples piezas, condiciones o caras por frase, números inválidos y expresiones de negación, incertidumbre o eliminación. Los comandos de control se procesan en la interfaz antes de llamar al parser.

## Guardado y paciente

La propuesta se revisa en el frontend. «Correcto» agrega el hallazgo al borrador local del odontograma abierto. «Guardar borrador», en el asistente o en el odontograma, usa el guardado existente y conserva el resto del mapa de hallazgos local. Incluye también cambios manuales pendientes del mismo borrador.

Una condición GENERAL reemplaza las marcas por caras de esa pieza. Una condición en una cara reemplaza esa marca y retira una marca GENERAL previa. El asistente advierte si hay marcas afectadas antes de aceptar. «Eliminar propuesta» descarta solo la propuesta pendiente, no borra hallazgos ya aplicados.

Cambiar de paciente, versión o pestaña descarta propuestas pendientes. Un odontograma confirmado, en carga, con error o guardándose no admite dictado. La confirmación definitiva de una versión sigue en el flujo original.

## Voz y costos

La voz se implementa con SpeechRecognition/webkitSpeechRecognition y speechSynthesis en el navegador. La primera activación requiere pulsar «Iniciar escucha». Decir «Prender CoroVoz» sirve dentro de una sesión ya iniciada; no hay escucha oculta con el micrófono apagado. Mientras CoroVoz habla o procesa se pausa la captura para evitar que se reconozca su propia respuesta. «CoroVoz terminado», cerrar el panel o cambiar de contexto detiene la escucha.

Sin clave OpenAI, modelos descargados ni GPU. El clasificador se aloja en el backend Java existente. Algunos navegadores usan un servicio externo para reconocer voz y requieren Internet; su disponibilidad depende del navegador, idioma y permisos. La aplicación no almacena audio. Texto siempre disponible.

## Pruebas y mejora

Ejecutar `./mvnw.cmd "-Dtest=CoroVozTest,CoroVozHttpTest" test` en Windows. No abre una conexión a la BD. Incluye frases de evaluación distintas del corpus, validación dental, límites de entrada, JSON HTTP y rechazo por rol.

Para mejorar el modelo, agregar preguntas etiquetadas al corpus y reiniciar backend. Mantener además un conjunto independiente de preguntas y audios reales anonimizados. Medir acierto por categoría, rechazos correctos, errores de pieza/cara/condición, latencia y fallos del micrófono. El conjunto inicial es pequeño: no acredita precisión clínica ni cobertura de todas las preguntas del sistema.

Las pruebas de interfaz usaron respuestas de API simuladas y datos ficticios para comprobar navegación, revisión, edición, descarte, guardado y cambio de paciente sin modificar expedientes reales. La precisión del micrófono debe probarse en el equipo y navegador de la clínica. No se desplegó esta versión.
