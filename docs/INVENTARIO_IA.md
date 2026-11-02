# Archivos de la IA de CoroVoz

Inventario de los archivos creados o modificados para esta implementación. Algunos
ya existían y fueron adaptados: no todos son archivos nuevos. Las rutas de las
tablas son relativas al repositorio indicado.

## Qué utiliza realmente la aplicación

- **Python:** servidor de IA, interpretación del odontograma, clasificación de
  ayuda, preparación de citas, análisis de consultas y conexión opcional con Luna.
- **Whisper preentrenado mediante whisper.cpp:** convierte audio a texto. Python
  valida el WAV e invoca el ejecutable; el motor whisper.cpp está desarrollado en C++.
- **Silero VAD mediante ONNX Runtime Web:** detecta habla en el navegador y cuenta
  las pausas. Se ejecuta desde JavaScript/TypeScript.
- **Java/Spring Boot:** autentica, controla permisos, consulta datos y guarda cambios.
- **React/TypeScript:** interfaz, micrófono, conversación y formulario de propuesta.
- **Luna:** conexión externa opcional para clasificar preguntas de ayuda general
  que no resuelve el clasificador local. El odontograma no se interpreta con Luna.

La implementación integra modelos preentrenados y lógica propia. No entrena un
modelo fundacional desde cero. `training.tsv` contiene ejemplos para el
clasificador local; no es entrenamiento de Luna ni de Whisper.

## Backend_Dentista: código Python

| Archivo | Función | Cuándo se utiliza |
| --- | --- | --- |
| `corovoz_python/server.py` | Publica las rutas internas de voz, ayuda, pacientes e interpretación/edición dental; conecta los módulos. | Funcionamiento habitual. |
| `corovoz_python/speech.py` | Valida audio, limita duración/concurrencia, llama a Whisper local y obtiene la transcripción. | Al enviar voz. |
| `corovoz_python/dental.py` | Reconoce piezas FDI, condiciones, caras y notas; construye propuestas parciales/completas y valida ediciones por campo. | Odontograma con IA. |
| `corovoz_python/knowledge.py` | Normaliza lenguaje y clasifica preguntas con ejemplos TF-IDF y similitud; elige respuestas del catálogo. | Ayuda general local. |
| `corovoz_python/patient.py` | Identifica qué dato se solicita y qué nombre buscar. No lee directamente la base clínica. | Consultas sobre pacientes. |
| `corovoz_python/appointment.py` | Extrae fecha, hora, paciente y motivo; pide aclaración cuando falta información. | Preparación de citas. |
| `corovoz_python/luna.py` | Llama a Luna cuando corresponde, filtra consultas, controla cuotas y registra consumo. | Solo si Luna está habilitado y la pregunta es apta. |
| `corovoz_python/install_model.py` | Descarga/verifica los pesos Whisper y el runtime Windows mediante SHA-256. | Instalación; no se ejecuta para cada pregunta. |
| `corovoz_python/data/knowledge.tsv` | Catálogo de respuestas y rutas de módulos por rol. | Ayuda general; también suministra el catálogo permitido para Luna. |
| `corovoz_python/data/training.tsv` | Ejemplos etiquetados para el clasificador local. | Carga del clasificador. |
| `corovoz_python/data/evaluation.tsv` | Preguntas independientes para evaluar el comportamiento. | Pruebas, no funcionamiento del usuario. |
| `corovoz_python/test_corovoz.py` | Regresiones de interpretación, propuestas, privacidad de consultas, audio y cuotas de Luna. | Pruebas. |
| `corovoz_python/README.md` | Instalación, arquitectura, funcionamiento, configuración y límites. | Documentación. |

## Backend_Dentista: integración Java

Prefijo de controladores: `src/main/java/pe/edu/utp/coronyx/backend/controller/`.

| Archivo | Función |
| --- | --- |
| `CoroVozController.java` | Endpoints autenticados para preguntas generales y para interpretar/editar propuestas del odontograma. |
| `CoroVozSpeechController.java` | Recibe audio y consulta disponibilidad de voz; comprueba permisos antes de enviarlo a Python. |

Prefijo de servicios: `src/main/java/pe/edu/utp/coronyx/backend/service/corovoz/`.

| Archivo | Función |
| --- | --- |
| `PythonCoroVozClient.java` | Comunicación HTTP entre Spring y el servicio Python; no es el conector directo con Luna. |
| `PythonCoroVozLifecycle.java` | Inicia y supervisa Python junto con Java; reutiliza un servicio local si ya está disponible. |
| `CoroVozHelp.java` | Contratos de respuesta y puente hacia la ayuda general de Python. |
| `DentalCommandParser.java` | Puente tipado hacia la interpretación y edición dental de Python. La interpretación ya no se realiza aquí. |
| `CoroVozPatientQueries.java` | Resuelve pacientes, pide elegir coincidencias, revalida clínica y obtiene datos administrativos/citas. |
| `CoroVozClinicalQueries.java` | Lee fuentes clínicas autorizadas y construye respuestas de registros, adjuntos e historia confirmada. |

Los servicios existentes de pacientes, adjuntos, atenciones, odontograma y agenda
son dependencias del sistema. No fueron creados de nuevo como otro motor de IA.

## Frontend-Dentista/Frontend_Dentista: interfaz y voz

| Archivo | Función |
| --- | --- |
| `src/components/features/AIAssistant.tsx` | Panel CoroVoz, conversación, propuesta progresiva y órdenes confirmar/editar/eliminar/repetir. |
| `src/hooks/useCoroVozSpeech.ts` | Abre/cierra micrófono, ejecuta VAD, envía audio, recibe texto y lee respuestas. Mantiene escucha mientras espera una respuesta dental. |
| `src/services/coroVozService.ts` | Cliente de los endpoints Java y tipos de respuestas/propuestas. |
| `src/contexts/CoroVozContext.tsx` | Memoria de la sesión y enlace con el odontograma abierto. |
| `src/utils/coroVozActivity.ts` | Detecta actividad de voz, pausa de 3,5 segundos e inactividad. |
| `src/utils/coroVozAudio.ts` | Prepara audio y codifica WAV PCM16 mono a 16 kHz. |
| `src/utils/coroVozConversation.ts` | Normaliza frases, reconoce comandos/opciones y combina fragmentos; resuelve respuestas a coincidencias de pacientes. |
| `src/utils/coroVozDentalDialog.ts` | Tipos y funciones del diálogo por campos, texto de la propuesta y selección de qué editar/eliminar. |
| `src/utils/coroVozDental.ts` | Aplica un hallazgo al mapa del odontograma sin borrar otras piezas/caras. |
| `src/utils/coroVozAppointment.ts` | Valida el borrador de cita y combina aclaraciones de fecha/hora. |
| `src/components/features/Odontogram.tsx` | Componente existente adaptado para seleccionar piezas, aplicar propuestas y guardar el borrador autorizado. |
| `src/layouts/DashboardLayout.tsx` | Componente existente adaptado para montar CoroVoz y aislar su memoria por usuario, clínica y rol. |
| `src/pages/Agenda.tsx` | Página existente adaptada para abrir la fecha y el formulario preparados por CoroVoz. |
| `src/components/agenda/AgendarCitaModal.tsx` | Formulario existente adaptado para recibir paciente/hora/motivo y validar disponibilidad. |
| `scripts/prepare-corovoz-vad.mjs` | Copia modelo y worklet Silero para servirlos localmente; retira recursos ONNX generados con la configuración antigua. |
| `package.json` | Dependencias de VAD/ONNX y comandos de preparación/verificación. |
| `pnpm-lock.yaml` | Versiones resueltas de las dependencias. |
| `pnpm-workspace.yaml` | Configuración de instalación de pnpm, no lógica de IA. |
| `.gitignore` | Excluye los recursos generados de VAD. |

## Archivos de pruebas conservados

Estos archivos permiten reproducir los fallos y verificar futuras correcciones.
La aplicación normal no los importa ni los necesita para atender a un usuario.

### Frontend: carpeta verification/

| Archivo | Prueba |
| --- | --- |
| `corovoz-audio.mjs` | WAV, frecuencia, amplitud y preparación del audio. |
| `corovoz-activity.mjs` | Ruido, voz corta, pausas e inactividad con diálogo pendiente. |
| `corovoz-conversation.mjs` | Fragmentos, selección de pacientes, comandos y edición por campos. |
| `corovoz-dental.mjs` | Aplicación de condiciones y limpieza por cara/pieza. |
| `corovoz-appointment.mjs` | Validación de citas preparadas y aclaraciones. |
| `corovoz-browser.html` | Entrada de la página aislada de comprobación. |
| `corovoz-browser.tsx` | Pacientes/audio sintéticos y conexiones de prueba con el intérprete Python. |
| `vite.browser.config.ts` | Configuración de esa página aislada. |
| `corovoz-synthetic.wav` | Frase española sintética para probar transcripción y VAD. |
| `corovoz-short.wav` | Palabra sintética «confirmar» para comprobar respuestas cortas. |
| `README.md` | Instrucciones para reproducir las comprobaciones. |

### Backend: src/test/java/pe/edu/utp/coronyx/backend/

| Archivo | Prueba |
| --- | --- |
| `CoroVozTest.java` | Interpretación, propuestas incompletas, ediciones y roles. |
| `CoroVozHttpTest.java` | Contrato HTTP, validación y autorización. |
| `CoroVozSpeechTest.java` | Estado del servicio y contrato de audio. |
| `CoroVozKnowledgeTest.java` | Conocimiento, restricciones y piezas FDI. |
| `CoroVozPatientTest.java` | Búsqueda, selección y consultas administrativas. |
| `CoroVozClinicalTest.java` | Lecturas clínicas autorizadas, pronombres y aislamiento. |
| `CoroVozLifecycleTest.java` | Arranque del servicio Python. |
| `CoroVozPythonTestServer.java` | Servicio Python aislado para las pruebas Java. |

También se utilizan `corovoz_python/test_corovoz.py` y
`corovoz_python/data/evaluation.tsv`, descritos arriba.

## Configuración y documentación del backend

| Archivo | Función |
| --- | --- |
| `.env.example` | Variables de ejemplo para Python, Whisper y Luna; no contiene la clave real. |
| `src/main/resources/application.yml` | Configuración existente adaptada al servicio de voz Python. |
| `.gitignore` | Excluye modelos, binarios y temporales locales. |
| `README.md` | Documentación general actualizada con la integración. |
| `docs/COROVOZ_FASE_1.md` | Antecedentes de la implementación anterior; contiene referencias históricas a Java. |
| `docs/COROVOZ_FASE_2.md` | Antecedentes de mejoras anteriores. |
| `docs/INVENTARIO_IA.md` | Este inventario. |

El archivo real `.env` pertenece a la configuración del entorno. No se ha
reproducido aquí su contenido ni sus claves.

## Recursos generados o descargados

| Ruta | Contenido | Necesidad |
| --- | --- | --- |
| Backend `.corovoz/whisper/ggml-base-q5_1.bin` | Pesos Whisper preentrenados descargados. | Voz local. |
| Backend `.corovoz/whisper/runtime/` | Ejecutable whisper.cpp y dependencias del proveedor. | Voz local en Windows. |
| Backend `.corovoz/luna_usage.sqlite` | Consumo/cuotas, creado cuando se usa Luna. | Control de consumo si Luna se habilita. |
| Backend `.corovoz/python.log` | Diagnóstico del servicio supervisado. | Diagnóstico; no código fuente. |
| Frontend `public/corovoz-vad/` | Modelo Silero y worklet copiados desde la dependencia. | Detección de voz local. |
| `dist/`, `target/`, `__pycache__/` | Compilaciones, informes de pruebas y cachés. | Generados, no archivos de IA escritos manualmente. |

Los modelos/binarios del proveedor no son código propio ni una segunda
implementación escrita para el proyecto. No deben eliminarse si se conserva
la voz local.

## Qué se retiró

- Implementaciones Java anteriores `IntentClassifier.java` y `LocalSpeechService.java`.
- Instalador anterior `tools/InstallCoroVozModel.java`.
- Copias anteriores `src/main/resources/corovoz/knowledge.tsv`, `training.tsv`
  y `src/test/resources/corovoz/evaluation.tsv`: se usan ahora las versiones Python.
- Grabador antiguo `public/corovoz-recorder.js`, sin referencias después de Silero.
- Compilación aislada `.tmp-corovoz-browser-dist/`.
- Informe de resultados antiguo `verification/COROVOZ_RESULTADOS.md`.
- Capturas, WAV y logs temporales de comprobaciones en `Backend_Dentista/tmp/`.

Las eliminaciones del código anterior son parte del cambio acumulado de esta
rama; no todas se realizaron en la última revisión. Se conservan las pruebas
reutilizables de las tablas anteriores y los archivos necesarios para el producto.

## Alcance actual de Luna

El conector directo es **`corovoz_python/luna.py`**. Lo llama `server.py` cuando
la ayuda local no resuelve una pregunta y la configuración/cuotas lo permiten.
El conector usa el catálogo de `knowledge.py` y `data/knowledge.tsv` para elegir
una respuesta permitida. No controla las escrituras del odontograma ni recibe
expedientes desde los módulos clínicos implementados.

Por tanto, el sistema actual conserva interpretación y voz locales junto con
la conexión opcional a Luna. Cambiar para que Luna interprete el odontograma y
todas las consultas sería otro cambio de arquitectura; eliminar los módulos
locales ahora quitaría funciones que la aplicación utiliza.
