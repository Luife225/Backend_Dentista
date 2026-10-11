# Adjuntos del caso de presentación CORONYX

Paquete de 14 archivos ficticios (11 PDF y 3 PNG), creado el 11/10/2026. Usa las cuentas y las citas ya existentes; no crea pacientes, no cambia historias clínicas y no ejecuta SQL ni limpiezas.

## Contenido y vistas

Marina Ríos Demo tiene 12 adjuntos: las evoluciones del 03/08, 17/08, 07/09 y 21/09/2026, plan de tratamiento, indicaciones, consentimiento de muestra, resumen de progreso, dos diagramas del odontograma, una imagen radiográfica simulada y una nota interna privada. Otros dos pacientes tienen un informe de muestra cada uno.

En el portal de Marina se publican 11 adjuntos: 6 INFORME, 1 RECETA, 1 CONSENTIMIENTO, 2 DOCUMENTO y 1 RADIOGRAFIA. Su sección Mis tratamientos muestra los 10 elementos que cumplen el filtro actual de la interfaz. Estos son documentos compartidos; no se ha creado un módulo adicional de tratamientos ni se han alterado los registros clínicos.

Las indicaciones clasificadas como RECETA no contienen medicamentos ni dosis. El consentimiento es una plantilla académica sin firmas reales. Los PNG son ilustraciones marcadas como simulación: no son fotografías ni exámenes diagnósticos. La categoría Fotografías permanece vacía porque no se han fabricado fotografías clínicas.

## Archivos que se pueden subir a Git

Desde la raíz de Backend_Dentista, incluir únicamente los elementos de este paquete:

```text
docs/demo/adjuntos/
docs/demo/MANIFIESTO_ADJUNTOS_DEMO.json
docs/demo/cargar_adjuntos_demo.py
docs/demo/README_ADJUNTOS_RAILWAY.md
```

El manifiesto contiene identidades ficticias, fechas, categorías y hashes; no contiene contraseñas. No incluir el TSV privado, tokens, evidencias locales ni la carpeta de almacenamiento de usuarios. No agregar toda la carpeta docs/demo sin revisar: allí puede haber otros scripts de preparación de la BD ajenos a esta carga.

Subir los archivos a Git no los registra automáticamente en PostgreSQL ni los hace aparecer en la aplicación. El cargador utiliza la API para almacenar los binarios y su ficha con la clínica, paciente, cita y permisos correspondientes.

## Carga local o en Railway

Se necesita Python 3.10 o superior; el cargador solo usa la biblioteca estándar. Primero comprobar que el backend de destino tenga los tres pacientes ficticios identificados por documento/correo y sus seis citas históricas. Si faltan, el cargador se detiene antes de subir archivos. No ejecutar un reset para resolver esa falta: preparar los datos de presentación por separado.

El cargador reconoce a cada odontólogo por su correo del manifiesto. Puede especificarse otra cuenta correspondiente mediante CORONYX_DEMO_DOCTOR1_EMAIL y CORONYX_DEMO_DOCTOR2_EMAIL; cada cuenta debe ser ODONTOLOGO de la clínica y ser el profesional asignado a las citas respectivas. Por defecto, pide ambas contraseñas de forma oculta. También admite variables CORONYX_DEMO_DOCTOR1_PASSWORD y CORONYX_DEMO_DOCTOR2_PASSWORD, que no deben subirse a Git.

Prevalidación local, sin subir archivos:

```powershell
python docs/demo/cargar_adjuntos_demo.py --base-url http://localhost:8081
```

Carga local:

```powershell
python docs/demo/cargar_adjuntos_demo.py --base-url http://localhost:8081 --aplicar
```

Carga en producción desde una copia local del repositorio. Sustituir el dominio de ejemplo por el dominio real del **backend**, no el del frontend:

```powershell
python docs/demo/cargar_adjuntos_demo.py --base-url https://DOMINIO-REAL-DEL-BACKEND --permitir-remoto
python docs/demo/cargar_adjuntos_demo.py --base-url https://DOMINIO-REAL-DEL-BACKEND --permitir-remoto --aplicar
```

Cada invocación pide las contraseñas. Ejecutar una sola carga a la vez. El modo sin --aplicar verifica identidad, citas, integridad y adjuntos existentes. La carga valida las descargas por SHA-256; si vuelve a ejecutarse, reconoce por nombre los documentos existentes y comprueba su contenido y metadatos antes de omitirlos. Ante una coincidencia incompatible se detiene. Si hay un fallo de conexión, volver a prevalidar: no repetir subidas manuales a ciegas.

La fecha de subida será la fecha real de ejecución; las fechas de las atenciones se conservan en los documentos y en su vínculo con las citas existentes. Los IDs se resuelven por API y se manejan como strings; no se presupone que producción tenga los mismos IDs que el entorno local.

## Persistencia en Railway

Adjuntar un volumen **al servicio backend**, por ejemplo con montaje /data, y configurar STORAGE_LOCAL_DIRECTORY=/data/coronyx. El servicio Java lee storage.local.directory; Spring puede obtener esa propiedad de STORAGE_LOCAL_DIRECTORY. Si la configuración activa utiliza la plantilla application.example.yml, también admite CORONYX_STORAGE_PATH=/data/coronyx. Comprobar la ruta efectiva del almacenamiento al iniciar el backend.

El volumen debe estar montado antes de cargar los adjuntos. Los archivos se suben a través de la API del backend ya iniciado, no durante la construcción de la imagen. PostgreSQL conserva los metadatos y el volumen conserva los bytes: ambos deben mantenerse. Un volumen del servicio PostgreSQL no conserva por sí solo los archivos del backend. [Documentación oficial de volúmenes de Railway](https://docs.railway.com/volumes).

Después de cargar: entrar como Marina, refrescar Documentos y Radiografías y Mis tratamientos; abrir y descargar un PDF y la imagen simulada. Como odontólogo, comprobar que aparece la nota interna. Como Marina, comprobar que esa nota no aparece. Tras reiniciar el backend, repetir una descarga para verificar el volumen.

## Resultado local real

El 11/10/2026 se cargaron los 14 adjuntos contra localhost:8081. Se verificaron los 14 binarios descargados y sus metadatos. Una segunda ejecución encontró los 14 existentes y no añadió duplicados. Pasaron 58 comprobaciones de formato, integridad, listados del portal, descargas y aislamiento: Marina recibe 11; los pacientes 2 y 3 reciben 1 cada uno; los pacientes 4 y 5 reciben 0 adjuntos de este paquete. Las descargas de la nota privada y de documentos ajenos se bloquearon con HTTP 403.

Los PDF se renderizaron y revisaron visualmente. La validación de las vistas se hizo mediante sus filtros de código y las respuestas de API; no se declara una prueba nueva de navegación. La carga, el reinicio y la comprobación del volumen en Railway quedan pendientes: no se ha hecho push ni despliegue desde esta tarea.
