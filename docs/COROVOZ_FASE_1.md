# CoroVoz: conocimiento, pacientes y voz local

## Cambios y finalidad

- `IntentClassifier`: combina el ejemplo más cercano de cada intención (80%) y su centroide TF-IDF (20%). Normaliza acentos, plurales, variantes de verbos y sinónimos del dominio. Tolera una errata solo cuando existe una corrección inequívoca. Se entrena al iniciar, sin enviar datos a una API ni aprender automáticamente de pacientes.
- `training.tsv`: 375 ejemplos de preguntas etiquetadas para 45 temas. Incluye definiciones de Coronyx/CoroVoz, cuentas inactivas, citas, pacientes, documentos, versiones, permisos, acceso, micrófono y problemas comunes.
- `knowledge.tsv`: respuestas revisadas y permisos de cada tema. Se separó del código Java para poder ampliar contenido y entrenamiento. Las pantallas actuales de Inventario, Caja y Teleodontología usan demostración; las respuestas lo explican y no presentan sus datos como registros reales.
- `CoroVozHelp`: elige una respuesta solo si supera mínimos de similitud, cobertura y separación entre categorías. La similitud no es una probabilidad de acierto. Cuando faltan datos o hay ambigüedad, pide aclaración. No es un modelo generativo de conocimiento universal.
- `CoroVozPatientQueries`: distingue preguntas administrativas de instrucciones. Consulta la ficha mediante el servicio existente y citas filtradas por paciente y clínica. No realiza escrituras, SQL generado por IA ni consultas clínicas. Los datos personales no forman parte del corpus.
- `DentalCommandParser`: gramática validada; transforma números dictados a FDI, normaliza errores conocidos del reconocimiento y permite completar fragmentos como «pieza catorce» y luego «corona». No diagnostica ni registra sin revisión.

## Consultas administrativas de pacientes

`POST /api/v1/corovoz/general` recibe `{ "text": "Próxima cita del paciente Ana Pérez", "patientId": null }` y devuelve `intent`, `answer`, `route`, `targetLabel`, `understood`, `patients`, `patientId` y `patientName`. El nombre permanece visible para identificar a quién se refieren las preguntas posteriores.

Puede consultar resumen, teléfono, correo, contacto, estado de ficha, próxima cita activa y última cita registrada. Los resultados se obtienen del backend en cada pregunta; no se inventan desde ejemplos. Las fechas se presentan en hora de Perú, igual que el módulo de citas. La última cita registrada no acredita asistencia.

La identidad se busca por términos completos del nombre, sin acentos, o documento exacto dentro de la clínica activa. Con dos a seis coincidencias devuelve opciones con ID, nombre y últimos cuatro caracteres del documento. Con más coincidencias pide precisar; sin identidad no lista todos los pacientes. Una consulta posterior puede enviar el ID seleccionado y preguntar «¿Y su teléfono?». Se vuelve a validar ese ID; un nombre nuevo hace una nueva búsqueda. «Olvidar paciente» limpia el contexto en el navegador.

El acceso exige rol ODONTOLOGO o RECEPCIONISTA y clínica operativa. Incluso un ID proporcionado directamente se valida mediante `PatientService.getPatientById`. Las citas se consultan con `findByPacienteIdAndClinicaIdOrderByInicioEnDesc`; se excluyen cancelaciones y reprogramaciones. Próxima cita exige fecha futura y estado PROGRAMADA, CONFIRMADA o EN_CURSO. No se devuelve historia clínica ni se usa un proveedor externo.

La búsqueda reutiliza el listado de pacientes de la clínica; para clínicas con muchos expedientes se debe pasar a búsqueda paginada en base de datos y medir concurrencia antes de producción.

## Odontograma y guardado

`POST /api/v1/corovoz/odontograma/interpretar` recibe texto y devuelve `understood`, `message`, `proposal`, `incomplete` y `fragment`.

- «Pieza catorce» devuelve `incomplete=true`, fragmento «pieza 14» y pide la condición. El frontend conserva ese fragmento para la siguiente frase del mismo paciente y versión.
- «Corona en pieza catorce» devuelve propuesta con pieza numérica 14. Revisa paciente, pieza, condición, cara y observación antes de confirmar.
- Piezas permanentes admitidas: 11–18, 21–28, 31–38, 41–48. No hay dentición temporal en la interfaz actual.
- Condiciones: CARIES, RESTAURADO, CORONA, EXTRAIDO, ENDODONCIA, IMPLANTE, FRACTURA. Caras: GENERAL, OCLUSAL, VESTIBULAR, LINGUAL, MESIAL, DISTAL; palatina se convierte al código LINGUAL que admite la interfaz.
- Se rechazan negaciones, incertidumbre, tratamientos pendientes, varias piezas/condiciones/caras y palabras no interpretadas. Notas solo después de «observación» o «nota», con máximo 300 caracteres; texto completo máximo 600.

«CoroVoz correcto» agrega solo al borrador visible. «CoroVoz edita» descarta la propuesta y permite corregir el texto. «CoroVoz elimina» descarta la propuesta pendiente; no elimina hallazgos guardados. «Guardar borrador» utiliza la operación existente del odontograma y conserva los cambios manuales del mismo borrador. Confirmar versión sigue siendo una acción del odontograma, no un comando de voz.

Una condición GENERAL reemplaza marcas por caras de la pieza; una marca de cara reemplaza esa cara y retira la GENERAL previa. La interfaz advierte los cambios antes de aceptar. Cambiar de paciente, versión o pestaña descarta propuesta, fragmento y captura. Las versiones confirmadas, en carga, con error o guardándose no admiten dictado.

## Voz y pausas

El navegador captura audio con getUserMedia y AudioWorklet y lo convierte a WAV PCM16 mono a 16 kHz. Brave no necesita el servicio SpeechRecognition. El modo inicial es manual: espera a «Terminé: enviar». La opción automática espera 3,5 segundos de silencio. Al llegar a 25 segundos, el audio se transcribe a texto para revisión y no ejecuta una frase posiblemente cortada. El servidor limita el audio a 30 segundos.

Hay indicador de volumen, botón para interrumpir la lectura y opción Leer respuestas. Los errores recuperables de transcripción no apagan el micrófono. Mientras habla o procesa no envía audio para evitar reconocer su propia voz; al terminar vuelve a escuchar. Los comandos se procesan tras transcribir: correcto, edita, elimina, terminado, iniciar, y sus formas con CoroVoz. En modo manual también hay que pulsar Terminé para enviar un comando hablado. No hay activación si el micrófono está apagado.

`GET /api/v1/corovoz/status` devuelve versión 4 y `speechReady`. `POST /api/v1/corovoz/transcribir` recibe `audio/wav` y devuelve `{ "text": "..." }`. Un backend que ya estaba abierto antes de cambiar controladores o corpus requiere reinicio. El frontend muestra un mensaje específico si recibe 404 o detecta una versión anterior.

## Instalación y recursos

En Windows x64 ejecutar una vez `java tools/InstallCoroVozModel.java` desde Backend_Dentista. Descarga whisper.cpp CPU oficial b5130 y el modelo multilingüe base Q5_1 de unos 57 MB. Verifica SHA256 y guarda pesos y ejecutables en `.corovoz/`, ignorado por Git. No usa una API de GPT ni paga por audio.

En Linux instalar whisper-cli y definir `COROVOZ_WHISPER_COMMAND` con ruta absoluta. `COROVOZ_MODEL_PATH` cambia la ruta de pesos GGML. Se procesan cuatro hilos y un audio por vez, con tiempo máximo de 60 segundos por transcripción. El proceso consume CPU y memoria al trabajar; no requiere GPU ni un tercer servidor permanente. El audio temporal se elimina al terminar, incluso al fallar. La lectura utiliza speechSynthesis del navegador.

## Verificaciones y límites

Pruebas: `./mvnw.cmd "-Dtest=CoroVozTest,CoroVozHttpTest,CoroVozSpeechTest,CoroVozKnowledgeTest,CoroVozPatientTest" test`.

El conjunto `src/test/resources/corovoz/evaluation.tsv` tiene 56 preguntas de regresión separadas del corpus: incluye definiciones, tareas, restricciones y consultas desconocidas. Se usa para desarrollar y comprobar cambios; no acredita precisión en preguntas de usuarios nuevos. También se verifican las 32 piezas permanentes dictadas, fragmentos, aislamiento por clínica, coincidencias, revalidación de identidad, datos administrativos y límites de audio sin conectarse a BD en los tests.

Además se verifican autenticación y peticiones contra el backend local, comparando consultas administrativas con la ficha sin imprimir datos personales ni guardar hallazgos. Las pruebas de voz usan audio sintético; faltan mediciones de precisión y latencia con voces humanas y ruido real. Para producción: corpus más amplio y evaluaciones independientes, búsqueda paginada, carga concurrente, despliegue del binario compatible con el servidor y revisión de los flujos con profesionales. Esta versión no se desplegó.
