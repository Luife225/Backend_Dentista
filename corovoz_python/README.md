# CoroVoz en Python

## Conversación, datos clínicos y dictado

El asistente conserva en memoria de la sesión el paciente elegido, las consultas
pendientes y las últimas ocho respuestas. Cerrar y reabrir el panel conserva esa
memoria; salir de la sesión, cambiar de usuario/clínica/rol o recargar la página
la descarta. «Olvidar paciente y conversación» permite borrarla expresamente.
La memoria clínica no se escribe en localStorage ni se envía a Luna.

Los apellidos incompletos, coincidencias parciales y errores de una letra ofrecen
candidatos antes de leer sus datos. «Sí» solo elige cuando hay un candidato; con
varios se requiere nombre u opción. La consulta original se retoma al elegir.

El odontólogo consulta alergias, antecedentes, medicamentos y otros campos de la
ficha; historia y odontograma confirmados; presencia y metadatos de adjuntos.
Cada lectura revalida la clínica y usa los mismos servicios autorizados que las
pantallas. Los borradores no se presentan como hechos confirmados. Un campo vacío
significa «sin información registrada». Listar una radiografía confirma su registro,
sin interpretar su imagen. Recepción conserva únicamente el acceso administrativo.

«Agenda una cita para [paciente] mañana a las 15:00 por limpieza» prepara el
formulario. Si falta fecha/hora o es ambigua, pide aclararla. «Redirigir a la cita»
abre Agenda en esa fecha y prellena paciente, hora, motivo y modalidad. La
disponibilidad se valida en el formulario antes de confirmar y guardar la cita.

El frontend ejecuta Silero VAD localmente para detectar voz. A los 3,5 segundos sin
voz, el modo manual apaga el micrófono y ofrece el texto para revisión; el modo
automático envía la consulta. Tras 15 segundos sin voz se apaga la escucha salvo
que el odontograma automático esté esperando una respuesta a su propuesta,
una aclaración o un campo de edición. En ese diálogo acepta palabras cortas
con el umbral específico de respuestas. Se puede detener con el botón o por voz.
Whisper transcribe localmente. Estos pasos no consumen tokens de Luna.
El modo manual conserva más audio antes del inicio de voz y acepta palabras
cortas con un umbral propio. El audio enviado conserva 400 ms después del habla
y amplifica de forma limitada las grabaciones bajas. Los comandos explícitos
«confirmar», «editar», «eliminar» y «repetir propuesta» se ejecutan por voz con
envío al pausar 3,5 segundos. En modo manual el texto queda para revisar y enviar.

En odontograma se conservan pieza, cara, observación, propuesta y el campo
pendiente de edición para el mismo paciente y versión. Cerrar el panel
conserva también la propuesta incompleta. Decir únicamente «pieza 15» ya devuelve
un borrador estructurado con condición pendiente, observación vacía y cara
general. El mismo formulario se completa en cualquier orden con frases sucesivas;
no se aplica hasta tener pieza y condición válidas y una confirmación explícita.
La observación es opcional y la cara general indica el diente completo. Cerrar el panel
conserva ese contexto; cambiar de paciente o versión lo reinicia. «Editar» pregunta
qué campo cambiar; «editar diente», «editar condición», «editar observación» y
«editar cara» piden solo el nuevo valor. Python valida la edición en
`/odontograma/editar` y conserva los demás campos. Los errores parecidos a las ocho
condiciones ofrecen opciones sin registrar una condición por suposición.
«Repetir propuesta» lee el posible hallazgo que se agregará: pieza, condición,
cara y observación. «Eliminar» pregunta qué parte quitar; quitar pieza o condición
requiere completar ese dato antes de confirmar. «Eliminar todo» descarta la
propuesta pendiente. Estas operaciones no eliminan hallazgos ya guardados.
En modo automático «confirmar» aplica y guarda el hallazgo en el borrador mediante
los servicios autorizados del odontograma. Si falla el guardado, lo comunica y
permite reintentar diciendo «guardar borrador». En modo manual se conserva el
flujo de revisar, enviar y guardar con los botones. La versión clínica definitiva
mantiene su confirmación propia en la pantalla del odontograma.
El runtime ONNX se carga mediante URLs de recursos gestionadas por Vite; no se
importa JavaScript desde public. `npm run dev` y `npm run build` preparan el modelo
y el worklet de voz automáticamente.

Luna sigue siendo opcional para ayuda general. Este cambio no habilita el envío
de expedientes o radiografías al proveedor. Antes de incorporar datos privados a
una API externa debe revisarse el tratamiento aplicable a la clínica y la
configuración de retención: `store=false` no elimina por sí solo todos los registros
de abuso. Referencia: https://developers.openai.com/api/docs/guides/your-data

La comprensión local sigue siendo un clasificador y un intérprete de campos;
no equivale a un modelo generativo capaz de razonar sobre cualquier historia.

CoroVoz usa Python para transcribir con Whisper local, clasificar preguntas, interpretar
órdenes del odontograma y analizar consultas administrativas de pacientes. El backend
Spring Boot conserva autenticación, permisos, acceso a datos reales y guardado. El
frontend sigue utilizando `/api/v1/corovoz/*`; no llama a Python directamente.

Después de cambiar los archivos Python o el contrato Java, reinicia el backend
Java y su proceso Python supervisado para cargar el código nuevo. Recarga el
frontend; cerrar y reabrir solo el panel no actualiza un servicio Python antiguo.

Para describirlo con precisión: la aplicación no entrena desde cero un modelo
fundacional. Integra Whisper preentrenado para reconocer voz y desarrolla en Python
la interpretación local, el catálogo de conocimiento, la desambiguación, el control
de alcance y la conexión opcional a Luna. Spring Boot aporta seguridad y acceso a
datos; la interfaz captura audio y muestra confirmaciones. Una API externa sería un
componente de la solución, no reemplazaría el trabajo propio de CoroVoz.

## Arranque local

Inicia el backend Java como antes. Al arrancar, el backend inicia y supervisa el
servicio Python local automáticamente; al cerrarse, también cierra el proceso Python
que inició. El frontend sigue funcionando sin comandos adicionales.

Si el modelo Whisper aún no está instalado, desde `Backend_Dentista` ejecuta una vez:

```powershell
python corovoz_python/install_model.py
```

El servicio Python escucha únicamente en `127.0.0.1:8787`. La instalación verifica SHA-256 y reutiliza el modelo
y el ejecutable ya presentes en `.corovoz/whisper/`. No se paga por audio al ejecutar
el modelo local; la máquina donde corre el servicio sí necesita CPU y memoria.

Para diagnóstico se puede iniciar manualmente con `python corovoz_python/server.py`.
El backend reutiliza un servicio local que ya esté activo. Si Python no está en PATH,
configura `COROVOZ_PYTHON_COMMAND` con la ruta del ejecutable. En un despliegue que
ejecute solo el JAR, configura `COROVOZ_PYTHON_SCRIPT` con la ruta absoluta de
`server.py` si no se conserva la carpeta `corovoz_python` junto al backend.

Para otra ubicación se pueden configurar `COROVOZ_PYTHON_URL` en Java y
`COROVOZ_PYTHON_HOST`/`COROVOZ_PYTHON_PORT` en Python. Si no es localhost, se exige
`COROVOZ_SERVICE_TOKEN` en ambos procesos y una URL HTTPS para Java. El servicio no
debe exponerse públicamente sin un proxy HTTPS y controles de red.
En ese caso, el proceso Python lo administra el despliegue externo. También se puede
desactivar el inicio automático con `COROVOZ_PYTHON_AUTOSTART=false`.

El modelo puede cambiarse con `COROVOZ_MODEL_PATH`; el ejecutable con
`COROVOZ_WHISPER_COMMAND`. El instalador Windows usa el modelo base multilingüe Q5_1
y el runtime whisper.cpp fijados en `install_model.py`. En Linux se instala
`whisper-cli` por separado y se configura su ruta absoluta.

## Despliegue de CoroVoz en Railway

Tu equipo puede usar Windows. Railway compila y ejecuta `Dockerfile.corovoz` en
Linux; el Dockerfile instala el modelo verificado y compila `whisper-cli` para ese
entorno. No subas `.corovoz/` ni secretos al repositorio.

1. Crea un servicio Railway desde `Luife225/Backend_Dentista`, rama
   `feature/AlanAguilera`, con la raíz del repositorio. Configura
   `RAILWAY_DOCKERFILE_PATH=Dockerfile.corovoz`.
2. En el servicio Python define `COROVOZ_SERVICE_TOKEN` con un valor aleatorio
   largo. Deja `COROVOZ_LUNA_ENABLED=false` salvo que hayas preparado su
   facturación y almacenamiento persistente. Railway proporciona `PORT` y el
   comando del Dockerfile lo pasa a `COROVOZ_PYTHON_PORT`.
3. Configura `/health` como ruta de salud y genera un dominio HTTPS en Railway.
   La ruta solo indica si el modelo y el ejecutable están disponibles; las
   rutas funcionales siguen exigiendo `X-CoroVoz-Token`.
4. En el servicio Java define `COROVOZ_PYTHON_URL=https://<dominio>` y el mismo
   `COROVOZ_SERVICE_TOKEN`; define también
   `COROVOZ_PYTHON_AUTOSTART=false`. Despliega primero Python y después Java.
5. Comprueba `/health` (debe devolver `ready: true`) y `/status` con el token
   (debe devolver `speechReady: true`). Finalmente prueba una transcripción
   desde la interfaz autenticada.

El modelo queda dentro de la imagen, así que no necesita un volumen. Si activas
Luna, configura `COROVOZ_LUNA_USAGE_DB` sobre un volumen para conservar sus cuotas
SQLite entre despliegues. La transcripción admite una solicitud a la vez por
réplica; dimensiona CPU y memoria según el uso real.

## Verificación

```powershell
python -m unittest discover -s corovoz_python -p 'test_*.py'
```

Las pruebas Java enfocadas comprueban el contrato HTTP, permisos, consultas reales
simuladas y la equivalencia de las frases clínicas y preguntas existentes. Requieren
Python disponible en PATH, o `COROVOZ_TEST_PYTHON` con su ejecutable. No usan la base
de datos real. Se mantiene la evaluación independiente en `data/evaluation.tsv`.

## Alcance de la comprensión

El clasificador actual usa TF-IDF y ejemplos revisados. Python facilita incorporar
un modelo semántico local más adelante, pero cambiar de lenguaje no convierte el
clasificador en un modelo de razonamiento. Las respuestas sobre pacientes se obtienen
en cada consulta del backend autorizado. Las órdenes clínicas siguen siendo una
gramática conservadora con confirmación antes de agregarlas al borrador.

En el odontograma se puede dictar de forma natural, por ejemplo «el diente número
treinta y seis está extraído» o «la pieza catorce está sana». Se reconocen las ocho
condiciones que ofrece la interfaz: sano, caries, restauración, corona, extraído,
endodoncia, implante y fractura. CoroVoz lee la pieza, condición y cara interpretadas;
el odontólogo dice «confirmar», «editar» o «borrar» tras la propuesta. Con el envío por
silencio de 3,5 segundos, esos comandos se envían sin pulsar un botón. «Confirmar»
aplica el cambio solo al borrador en pantalla; «guardar borrador» lo persiste en la
base de datos. Si Whisper transcribe mal un número o una condición, no se aplica
nada hasta que el odontólogo confirme la lectura de CoroVoz.

Cuando una pregunta corta admite varias tareas, `/general` responde con hasta tres
`suggestions` (identificador y título) en vez de elegir una al azar. La interfaz permite
seleccionarlas por botón o por voz («la primera», «opción dos», etc.). La selección se
envía como `selectedIntent`, se valida en Python y mantiene los permisos del rol.
Una frase sin relación con el sistema sigue recibiendo una respuesta de alcance, sin
proponer acciones clínicas.

En despliegue, Whisper y este clasificador usan CPU y memoria del servidor de CoroVoz.
Agregar un modelo de lenguaje local aumentaría ese consumo y exigiría medir latencia,
RAM y concurrencia en el servidor real.

## Luna opcional y límites de gasto

Luna está **apagado por defecto**. Para prepararlo en un entorno propio, configura en
el servidor `COROVOZ_LUNA_ENABLED=true`, `OPENAI_API_KEY`, un secreto compartido
`COROVOZ_SERVICE_TOKEN` y facturación o crédito API disponible. La clave
API nunca va al frontend. No hace falta ChatGPT Pro: la facturación de API es aparte.
Una clave de proyecto puede servir a la aplicación para varios odontólogos; el backend
envía a Python la identidad autenticada para aplicar la cuota diaria por usuario y
el presupuesto mensual por clínica.
Estas son variables de entorno del proceso: copiar `.env.example` a `.env` no las
carga automáticamente al iniciar Spring Boot desde la terminal.

Luna solo interviene cuando una pregunta de ayuda general no se pudo resolver de forma
clara con el clasificador local. Selecciona un ID de artículo autorizado mediante una
salida JSON limitada; Python devuelve la respuesta revisada para ese artículo. No se
envían consultas de pacientes procesadas por `CoroVozPatientQueries`, órdenes del
odontograma, texto con indicios de información clínica o identificadores evidentes.
Este filtro es conservador y no sustituye una revisión de privacidad antes de activar
la API en producción. Se usa `store=false`; ante error, falta de clave o cuota agotada,
vuelve la respuesta local.

Por defecto se permiten 50 llamadas a Luna por usuario y día de Bogotá y un máximo
estimado de US$1 por clínica al mes, compartido por sus odontólogos y demás usuarios.
El presupuesto total de la aplicación es de US$3 al mes entre todas las clínicas.
`COROVOZ_LUNA_DAILY_LIMIT`, `COROVOZ_LUNA_MONTHLY_USD` (clínica) y
`COROVOZ_LUNA_GLOBAL_MONTHLY_USD` (toda la aplicación) cambian esos límites.
SQLite conserva reservas y consumo en
`.corovoz/luna_usage.sqlite` entre reinicios; se puede ubicar en otro volumen mediante
`COROVOZ_LUNA_USAGE_DB`. Las llamadas concurrentes reservan presupuesto antes de
enviar la solicitud. Se reserva un máximo conservador de tokens y se ajusta con el
consumo devuelto por la API. Un fallo sin cifras de uso conserva la reserva para no
subestimar el gasto.
Si despliegas varias instancias de Python, el archivo SQLite no sirve como cuota
compartida; habrá que mover este contador a una base de datos central antes de escalar.

El cálculo local es aproximado porque tarifas y condiciones de la API pueden cambiar.
Para que US$3 sea un límite de facturación del proveedor, configura además un
**límite duro de gasto de US$3** en el proyecto API de OpenAI y revisa los
cargos en su panel. OpenAI advierte que la aplicación de ese límite puede tardar y
que el consumo registrado podría superar ligeramente el importe configurado. Las
pruebas automáticas de Luna usan una respuesta simulada, sin
clave real ni cargos. Para probar Luna real se requiere activar la API y contar con
facturación o crédito disponible; no se necesita una suscripción ChatGPT Pro.
