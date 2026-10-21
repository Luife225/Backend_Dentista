# CORONYX — cierre funcional y ajuste de requisitos

> **Plan del 19/10/2026, revisión 2:** el responsable eligió Inventario por UNIDAD/CAJA/ENVASE, distinguiendo cerrado/disponible, en uso y agotamiento histórico, sin descuento por cita ni conteo obligatorio de piezas. Se mantiene videollamada dentro del sistema, alojada por el equipo y probada primero en una computadora. Leer [el plan actualizado](PLAN_INVENTARIO_TELECONSULTA_2026-10-19.md). Estas ampliaciones están en planificación; las funciones y pruebas del corte del 18/10 no las acreditan.

Fecha de trabajo: 18 de octubre de 2026, America/Lima. Rama: `feature/sebastian` en ambos repositorios.

## Alcance acordado

Terminar frontend y backend de administración de clínica, pacientes, agenda, atención clínica manual, odontograma manual, archivos, portal del paciente, notificaciones, teleconsulta, Caja e Inventario.

Quedan fuera de este cierre el análisis de radiografías mediante ML, la voz con IA y Superadministrador. El almacenamiento, publicación y consulta manual de radiografías sí pertenece al sistema funcional.

Los informes entregados siguen siendo la referencia histórica. Este documento es un anexo de trabajo; no modifica los PDF ni declara aprobados los 34 casos de aceptación. Las correcciones atienden las auditorías del 17 y 18 de octubre. Las decisiones siguientes explicitan el comportamiento implementado para que el equipo pueda acordar la especificación final.

## Requisitos funcionales precisados

### Identidad y acceso

- Los cuatro roles operativos se resuelven desde sus cuentas y membresías reales. Una cuenta, rol, clínica o expediente deshabilitado invalida el acceso según las reglas existentes.
- ADMIN_CLINICA y RECEPCIONISTA consultan información administrativa del paciente. El contenido clínico técnico, los borradores y odontogramas se reservan a ODONTOLOGO.
- PACIENTE consulta sus propias citas y sus propios archivos publicados. Una URL directa no debe permitir consultar otro expediente o clínica.
- La recuperación actualmente disponible es asistida: un administrador verifica identidad y restablece la contraseña desde Usuarios. El login ya no simula el envío de un correo. La recuperación automática por correo requiere decidir e integrar el proveedor antes de incluirla como requisito cumplido.

### Agenda y disponibilidad

- El selector consulta los turnos del servidor para el profesional y fecha elegidos. Usa horario configurado, citas y ausencias; la duración del turno determina el fin de la reserva.
- La cita reprogramada conserva el bloqueo de su nuevo intervalo. Doctor, paciente y consultorio físico no pueden tener reservas solapadas.
- Registrar asistencia física no sustituye la firma clínica ni finaliza por sí mismo la consulta.
- CANCELADA, FINALIZADA y NO_ASISTIO son estados terminales: no se reabren ni reprograman implícitamente. Una cita EN_ATENCION se finaliza mediante la confirmación clínica.
- La ausencia bloquea reservas nuevas. Si ya hay citas durante ese período, se rechaza el bloqueo hasta que el personal reprograme o cancele esas citas.
- El administrador modifica exclusivamente los horarios de su clínica. El personal puede gestionar ausencias según su rol; un paciente no puede consultar motivos de ausencias ni crearlas o eliminarlas.

### Atención clínica manual

- `POST /api/v1/citas/{id}/iniciar-atencion` crea o recupera el borrador y cambia el estado en una transacción. Repetirlo no duplica atenciones. Solo atiende el odontólogo asignado.
- Un borrador de una atención iniciada se autoguarda después de una pausa de escritura. La interfaz informa guardado o error y conserva el guardado manual.
- Firmar requiere motivo de consulta y diagnóstico, la versión exacta y la revisión del contenido que el profesional abrió. Un borrador modificado por otra petición rechaza el guardado o firma obsoletos con 409.
- La confirmación registra profesional y fecha y deja la cita FINALIZADA/ASISTIO. No convierte una cita cancelada en atendida.
- Una versión confirmada no admite sobrescrituras. La corrección genera otra versión; el autor de la atención gestiona sus borradores y correcciones. El resto de odontólogos de la clínica puede consultar su historial.
- Los identificadores se transportan como cadenas decimales. Los signos vitales sin registrar quedan vacíos: no se rellenan con mediciones de ejemplo.

### Documentos y radiografías manuales

- Los archivos anteriores se clasifican como CLINICO. Solo el odontólogo puede gestionarlos y publicarlos al paciente.
- Los documentos que administración o recepción suben como DOCUMENTO se clasifican como ADMINISTRATIVO. Estos roles no pueden convertir una radiografía o informe clínico en documento administrativo mediante un parámetro del navegador.
- El paciente solo accede a archivos propios publicados; sus cargas propias siguen siendo visibles para él. Publicación/revocación y descargas se comprueban en el servidor.
- Se comprueban tamaño y firma del contenido para PDF, PNG, JPEG y DICOM con encabezado DICM. DICOM se conserva para descarga; el visor integrado continúa mostrando imágenes compatibles y PDF.

### Teleconsulta integrada — ampliación implementada (Revisión 2)

- Videollamada integrada directamente en el sistema mediante Jitsi Meet alojado localmente.
- El backend (`/api/v1/teleconsulta/sesion/{citaId}`) verifica que la cita sea virtual y esté activa (no cancelada ni finalizada) y autoriza exclusivamente al odontólogo asignado a la cita o al paciente vinculado (o admin de la clínica).
- Identificadores de sala opacos criptográficos (`cx-...` seguido de 16 caracteres hexadecimales aleatorios). Queda estrictamente prohibido usar nombres de pacientes, documentos de identidad o diagnósticos en la sala.
- Autenticación mediante tokens JWT firmados con HMAC-SHA256, con expiración y delimitación por sala; no se permiten accesos anónimos ni tokens vacíos o adulterados.
- Vista de atención clínica persistente: en `PacientePerfil.tsx`, la llamada se integra docked o minimizada en esquina junto a los paneles clínicos. Cambiar entre Resumen, Historia clínica, Odontograma, Radiografías y Documentos mantiene la llamada conectada y el borrador de atención intacto sin recargas.
- Portal del paciente (`PatientApp.tsx`): el paciente ingresa desde su cita virtual y únicamente recibe la llamada segura y sus archivos autorizados. Nunca recibe el panel clínico del profesional.
- Separación de controles: colgar/salir de la llamada, cerrar formalmente la sala (`/cerrar`, reservado al profesional) y firmar la atención clínica en la historia son tres acciones independientes. Una caída o reconexión de video no altera ni duplica atenciones.

### Caja y cobros — ampliación solicitada

- Registro de cargos por paciente de la clínica, con cita opcional, concepto e importe en PEN.
- Registro de abonos parciales o completos, medio de pago y referencia. Se calcula saldo en el servidor y se rechaza un sobrepago.
- Cada petición de cargo o abono lleva una clave de solicitud. Reintentar la misma operación no duplica el cargo/pago; reutilizar una clave con otros datos se rechaza.
- ADMIN_CLINICA, RECEPCIONISTA y ODONTOLOGO operan Caja en su clínica. Solo el administrador anula pagos/cargos; se conservan motivo, autor y fecha. Un cargo con pagos activos no se anula hasta anular esos pagos.
- La pantalla presenta cobrado por fecha de pago en Lima, saldo pendiente y exportación CSV. El saldo del resumen del paciente se obtiene de Caja, no de un cero fijo.
- Esta implementación registra cobros recibidos. No procesa tarjetas, transfiere dinero ni emite comprobantes tributarios. Apertura/cierre de caja física, pasarela y facturación electrónica requieren requisitos adicionales si la entrega los exige.

### Inventario por presentación y estados — ampliación implementada (Revisión 2)

- Cada SKU se controla por presentación: `UNIDAD`, `CAJA` o `ENVASE`, y clasificación `CONSUMIBLE` o `REUTILIZABLE`.
- Desglose estricto de existencias: `stock_cerrado` (disponible) y `stock_en_uso`. El stock total actual es la suma de ambos (`stock_cerrado + stock_en_uso = stock`).
- Operaciones soportadas en backend e interfaz:
  - `ENTRADA`: suma existencias a `stock_cerrado` y al total.
  - `APERTURA`: transfiere cantidades de `stock_cerrado` a `stock_en_uso`. El stock total no varía.
  - `AGOTADO`: retira unidades de `stock_en_uso` y disminuye el stock total; el agotamiento queda registrado en el historial de movimientos.
  - `SALIDA`: baja con motivo obligatorio desde existencias cerradas o en uso (por merma, vencimiento o rotura).
  - `DEVOLUCION`: devuelve instrumental reutilizable desde `stock_en_uso` a `stock_cerrado` sin alterar el stock total ni duplicar insumos. Solo disponible para insumos clasificados como `REUTILIZABLE`.
  - `AJUSTE`: corrección administrativa de conteo físico, auditada y autorizada exclusivamente para `ADMIN_CLINICA`.
- Contenido descriptivo del empaque: es puramente informativo (ej. "Caja x 100 unidades"). Una caja parcialmente usada se registra como "en uso, contenido restante desconocido". No se calculan piezas remanentes ficticias ni se obliga a convertir cajas en piezas.
- Alertas de stock bajo: consideran existencias cerradas/disponibles (`stock_cerrado <= stock_minimo`) y muestran las unidades abiertas en uso de manera separada.
- Independencia absoluta de citas: las reservas, inicio, autoguardado, firma o corrección de atenciones clínicas son 100% independientes de Inventario y no modifican existencias.
- Importación y previsualización de catálogos (`POST /api/v1/inventario/catalogo/previsualizar` y `POST /api/v1/inventario/catalogo/confirmar`): separa automáticamente insumos físicos de procedimientos/servicios clínicos, estandariza presentaciones y previene duplicaciones.

## Contrato para integrar el trabajo de otros compañeros

La voz y el ML no deben escribir directamente en versiones confirmadas ni modificar permisos o estados de citas. Deben presentar su salida como contenido pendiente de revisión profesional.

Para editar un borrador clínico, leer primero la atención y enviar `revision` de `versionActual` en el PUT `/clinical-encounters/{id}/draft`. La respuesta contiene la nueva revisión. Para confirmar, enviar `versionId` y esa `revision` en POST `/clinical-encounters/{id}/confirm`. Una respuesta 409 exige volver a consultar y revisar, no reintentar sobrescribiendo el contenido automáticamente.

Conservar los IDs como cadenas; por ejemplo, `"9007199254740993"`. Nunca convertirlos a `Number` en JavaScript. El archivo de voz/radiografía debe entrar por los endpoints autenticados del sistema y mantener clínica, paciente y visibilidad.

## Orden de cierre durante la semana

1. **Integridad y privacidad:** revisar estados, conflictos de agenda, propiedad de expedientes, versiones y publicación de archivos. Hay correcciones y pruebas automatizadas nuevas en este cambio.
2. **Operación por rol:** recorrer administración, recepción, odontólogo y paciente con la misma cita, incluyendo errores de red, permisos y recarga. Registrar evidencia para cada caso del informe, sin sustituirla por porcentajes.
3. **Caja e Inventario:** validar con el equipo los casos de uso anteriores y verificar datos persistidos, abonos, anulaciones, mínimos y movimientos.
4. **Teleconsulta integrada, decisión del 19/10:** preparar una prueba local de video alojado por el equipo y comprobar dos participantes con acceso por cita, manteniendo la historia clínica visible. Seguir el plan nuevo antes de implementar. El enlace externo de Google no satisface por sí solo este requisito.
5. **Entrega:** copia de seguridad y recuperación verificadas, revisión de migración sobre una copia de los datos actuales, despliegue con HTTPS/volumen duradero, prueba de los 34 casos y registro de observaciones restantes.

El cierre de producción sigue dependiendo de esos recorridos y de la configuración real. Compilar o pasar una suite dirigida no certifica por sí solo el sistema al 100%.

## Validación reproducible

Frontend: `pnpm typecheck`, `pnpm test` y `pnpm build`.

Backend: `scripts/test-functional-closure.ps1` crea PostgreSQL temporal, ejecuta los tests nuevos y lo detiene. Con `-AllTests` ejecuta también las suites anteriores, manteniendo la comprobación de aislamiento del bloque 1. Usa PostgreSQL 18 y Java 21 de las rutas configuradas en el script; no apunta a la base habitual.

`scripts/start-closure-preview.ps1` permite revisar la UI con otra base temporal y datos ficticios. Usa API 18081 y PostgreSQL 55463. El frontend de esa revisión debe iniciarse con `VITE_API_BASE_URL=http://127.0.0.1:18081/api/v1` y puerto 5177. La clave de los usuarios de `preview-fixture.sql` pertenece solo a esa base de validación y nunca debe emplearse como credencial de producción.

Las migraciones V12–V15 son nuevas. V12 no borra versiones clínicas si encuentra duplicados: rechaza la migración para que se revisen. No ejecutar una limpieza automática de historia clínica para hacer pasar ese control.

En producción activar el perfil `prod` y proporcionar DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS y CORONYX_STORAGE_PATH. El directorio de archivos debe estar en un volumen persistente. Las copias locales de configuración quedan excluidas del empaquetado Maven.

## Referencias técnicas de Google consultadas

- OAuth y cierre de ventana: https://developers.google.com/identity/oauth2/web/reference/js-reference
- Consulta del evento: https://developers.google.com/workspace/calendar/api/v3/reference/events/get
- Estados de creación de conferencia: https://developers.google.com/workspace/calendar/api/v3/reference/events
