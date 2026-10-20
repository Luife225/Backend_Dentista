# CoroVoz: mejora de dictado y consultas (fase 2)

## Diagnóstico

- El reconocimiento de audio ya usa Whisper, pero la detección de fin de frase del navegador dependía de un umbral RMS fijo de 0,008. Una voz baja o ruido constante podía impedir iniciar o completar el contador de 3,5 segundos.
- La ayuda general utiliza TF-IDF con ejemplos revisados; no es un LLM. Las consultas de pacientes usan servicios reales y controles de clínica, pero expresiones como «¿Cuándo viene otra vez Juan?» no llegaban a esa ruta.
- El intérprete clínico normaliza números FDI y exige confirmación, pero una orden de selección sin hallazgo no tenía una acción propia. El odontograma actual admite únicamente piezas permanentes FDI 11–18, 21–28, 31–38 y 41–48; «diente 2» y «diente 3» siguen siendo ambiguos o inválidos para este esquema.
- La interfaz mostraba detalles de infraestructura que no ayudan al usuario final.

## Implementación

- El detector de actividad ajusta el umbral al ruido de fondo y a la intensidad de la frase. El modo automático espera 3,5 segundos desde la última actividad vocal detectada; el modo manual solo envía al pulsar «Terminé». Durante transcripción y respuesta hablada se descarta la captura para evitar eco y duplicados.
- El servicio de pacientes reconoce más formas de preguntar por próximas citas y búsquedas por nombre, y sigue resolviendo la identidad con los servicios autorizados. Si hay varias coincidencias, pide elegir una. No se amplió el acceso a historia clínica.
- «Selecciona el catorce» y «No, no era el catorce, era el trece» devuelven una pieza validada, sin propuesta clínica. Cuando hay una propuesta pendiente, la corrección exige que la pieza anterior coincida y sigue requiriendo confirmación antes de agregarla al borrador.
- «Caries en ese diente» usa la pieza seleccionada del odontograma activo, solo si es FDI válida. Sin una selección pide el número; el contexto se invalida al cambiar de paciente o versión.
- Se simplificaron los estados y errores visibles de CoroVoz. El texto de ayuda sobre voz describe el uso del producto sin exponer detalles de implementación.

## Arquitectura y límites

El flujo sigue siendo audio → Whisper local → transcripción → consulta o interpretación validada → respuesta estructurada. La ayuda general clasifica por ejemplos y tiene límites de cobertura; agregar más redes neuronales por cantidad no garantiza comprensión. Para comprensión abierta haría falta evaluar un modelo de lenguaje con un conjunto independiente de preguntas reales, latencia, costo, privacidad de datos clínicos, permisos y confirmaciones antes de integrarlo. No se añadió un proveedor externo ni se modificó el costo operativo de esta fase.

La detección de actividad tiene pruebas sintéticas; necesita una prueba manual con micrófonos y ruido reales. La selección hablada no elimina ni modifica hallazgos ya guardados. El servicio de pacientes todavía busca en la lista completa de la clínica; una instalación grande necesitará búsqueda paginada.

## Verificación

- Pruebas enfocadas: `./mvnw.cmd -q '-Dtest=CoroVozTest,CoroVozPatientTest,CoroVozKnowledgeTest,CoroVozSpeechTest,CoroVozHttpTest' test` — correctas.
- Frontend: `npm run typecheck`, `npm run build` y `npm run verify:corovoz` — correctos. La compilación muestra avisos existentes sobre configuración de Vite y tamaño del paquete.
- La suite Java completa no pasó: 38 errores en `Block1SessionIntegrationTest` porque la base aislada de `127.0.0.1:55431` no estaba disponible, y 4 fallos en `ClinicUsersAndPatientsIntegrationTest` por supuestos de datos de demostración que no coinciden con la base local. **No repetir `./mvnw.cmd test` contra la base local de trabajo**: esas pruebas de integración incluyen escrituras y no todas tienen rollback. La ejecución realizada pudo crear o actualizar registros de demostración en la base local; no se hizo una limpieza automática porque se desconoce el estado anterior. Usar una base de prueba desechable con el perfil y seed correctos.

## Punto de retorno

Antes de esta fase se crearon commits en `feature/AlanAguilera`: backend `c9315a9` y frontend `a7f859c`. El cambio local de `src/main/resources/application.yml` con una contraseña predeterminada no se incluyó en el commit y debe permanecer fuera de control de versiones.
