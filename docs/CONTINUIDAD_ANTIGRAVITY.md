# CORONYX — contexto vigente y tareas para Antigravity

> **Actualización 19/10/2026 — Implementación local completada según el Plan Revisión 2:**
> Se implementó en el backend y frontend el Inventario por presentación (`UNIDAD`, `CAJA`, `ENVASE`) y desglose de estados (`stockCerrado` / disponible y `stockEnUso`), con operaciones de `ENTRADA`, `APERTURA` (cerrado → en uso sin alterar el total), `AGOTADO` (retira de existencias y registra en historial), `SALIDA` (con motivo), `DEVOLUCION` (para reutilizables) y `AJUSTE` administrativo con auditoría. Se añadieron endpoints y modales para previsualización y confirmación de importación de catálogo con clasificación inteligente (separando insumos físicos de procedimientos clínicos).
> Asimismo, se integró la **teleconsulta segura en CORONYX**: backend emite tokens JWT firmados para salas con identificadores opacos criptográficos (`cx-...`), autorizando exclusivamente al odontólogo asignado y al paciente vinculado; la videollamada está integrada persistentemente en `PacientePerfil.tsx` para el profesional (permaneciendo conectada y preservando el borrador clínico al alternar entre Resumen, Historia, Odontograma, Radiografías y Documentos) y en `PatientApp.tsx` para el paciente (sin acceso a notas clínicas ni expedientes del médico).
> Ambas funcionalidades cuentan con suite de pruebas automatizadas y aisladas aprobadas: **36 pruebas backend** y **13 pruebas frontend**, con compilación TypeScript y build de producción verificados.

Preparado el 18/10/2026 y actualizado el 19/10/2026, America/Lima. Punto de entrada para continuar el software y ordenar el workspace. Esta guía no certifica que el sistema esté desplegado en producción ni sustituye una validación en red real.

## 1. Abrir la copia correcta y conservar el avance

Workspace: `C:/Users/USER/Desktop/CoronyxProyecto`.

Hay **dos repositorios Git independientes**, `Frontend_Dentista` y `Backend_Dentista`. Ambos están en `feature/sebastian`. La carpeta superior no tiene repositorio Git; su `.gitignore` no protege los archivos de los repositorios hijos. No crear un tercer repositorio que englobe a los otros ni convertirlos en un monorepo durante la limpieza.

En el momento de preparar esta guía, los cambios del cierre funcional e implementación de revisión 2 están **sin commit y sin push**. Incluyen archivos nuevos sin seguimiento; un diff de archivos ya versionados no alcanza para respaldarlos. No sustituir el workspace por un clon del remoto, hacer reset/clean, cambiar de rama descartando archivos ni restaurar versiones anteriores para empezar. Se ha generado un respaldo verificable con manifiesto SHA-256 en `tmp/backups/pre_implementation_backup_20261019/`.

HEAD observados, como referencia histórica y no como destino de restauración:

- Frontend: `3d5882c42b90ad2fbbedf865776c2625b34b5dda`.
- Backend: `e6f41425101f024a686bddfa4d9967de172da965`.

Archivos previos que se conservaron durante el cierre: `Backend_Dentista/docs/demo/MANIFIESTO_PRESENTACION.json`, `docs/demo/seed_presentacion.sql` y `src/main/resources/application.yml.local_backup`. No ejecutar la semilla de presentación ni copiarla a producción. El respaldo de configuración es privado: se agregó su ruta al `.gitignore` del backend, sin borrar su contenido. No mostrar sus valores ni publicarlo.

## 2. Qué fuentes usar y cuál es el alcance

Leer primero [CIERRE_FUNCIONAL_Y_REQUISITOS.md](CIERRE_FUNCIONAL_Y_REQUISITOS.md) y [PLAN_INVENTARIO_TELECONSULTA_2026-10-19.md](PLAN_INVENTARIO_TELECONSULTA_2026-10-19.md). Esos anexos explicitan las decisiones del cierre, la migración V16 y los contratos actuales.

Decisiones expresas del responsable aplicadas en esta implementación:

- Terminar frontend y backend funcionales de clínica, usuarios, pacientes, agenda, atención clínica, odontograma manual, archivos/radiografías manuales, portal del paciente y notificaciones.
- **Incluir Caja e Inventario con presentaciones y estados:** `UNIDAD`, `CAJA`, `ENVASE`, existencias cerradas/disponibles y en uso, total calculado, acciones dentro de Inventario, sin registrar materiales ni descontar por cita. El contenido por empaque es solo informativo.
- **Teleconsulta integrada dentro de CORONYX:** Jitsi Meet local con Docker, sala opaca, JWT firmado por cita, llamada persistente simultánea a la historia clínica para el odontólogo y llamada aislada autorizada para el paciente.
- Posponer **Superadministrador**, el análisis radiográfico mediante **ML** y la **voz con IA**. Estos dos últimos los trabajan otros compañeros. Conservar sus archivos y acordar contratos de integración; no implementar sus motores ni eliminarlos.

El `ROADMAP.md` raíz, el roadmap del frontend y los planes/prompts del 10–12 de octubre contienen estados anteriores. Se marcaron sus principales entradas como históricas. Usarlos para entender el origen y localizar casos, sin retirar funciones ya incluidas ni volver a resolver hallazgos corregidos. Ante una contradicción nueva, registrar el punto concreto y consultar al responsable mientras se avanza en tareas independientes.

## 3. Lo que ya existe y debe conservarse

Hay implementaciones reales, con API y persistencia, que deben revisarse antes de reescribirlas:

- **Caja:** cargos, abonos parciales, saldo calculado en servidor, rechazo de sobrepago, reintentos sin duplicación, anulaciones trazables del administrador y exportación CSV. Importes en PEN.
- **Inventario:** insumos, entradas/salidas/ajustes persistentes, bloqueo concurrente, rechazo de stock negativo, mínimos, historial y exportación.
- **Agenda:** turnos consultados al servidor, horarios y ausencias, validación de solapamientos y estados terminales. Registrar asistencia no firma ni finaliza la atención.
- **Atención clínica:** inicio atómico desde la cita, borrador con autoguardado, guardado manual y firma de la versión/revisión exacta. Lo confirmado es inmutable; una corrección crea otra versión.
- **Archivos:** autorización por clínica/paciente y ámbito CLINICO/ADMINISTRATIVO, publicación al paciente y validación del contenido admitido.
- **Portal y ficha:** sin signos vitales, saldos, fechas de nacimiento o reuniones inventados para aparentar datos registrados.
- **Teleconsulta:** enlace real compartido por personal/paciente y comprobación real de dispositivos. No hay participantes, chat o llamada conectada simulados.
- **Sesión y despliegue:** revocación de sesión, errores visibles y perfil de producción con variables obligatorias; no equivale a despliegue validado.

Contratos que deben preservarse en cualquier corrección:

1. IDs BIGINT/Long en la base/Java y **strings decimales en JSON/TypeScript**. No convertir identificadores a `Number` ni generarlos con `Date.now()`.
2. Clínica, actor y permisos se comprueban en el servidor. Ocultar botones no sustituye autorización. ADMIN_CLINICA y RECEPCIONISTA no acceden al contenido clínico técnico; PACIENTE solo a lo propio permitido.
3. Para editar o firmar un borrador, enviar su `revision`; para firmar, también su `versionId` exacto. Un 409 exige volver a consultar y revisar, sin sobrescribir automáticamente. ML/voz deben entregar propuestas para revisión profesional.
4. Conservar el bloqueo contra dobles reservas, stock negativo y pagos duplicados/concurrentes. No retirar restricciones para que una prueba pase.
5. Respetar America/Lima y los offsets. No agregar `Z` a una hora local sin conversión.
6. Error de API debe verse como error; no reemplazarlo por datos locales, arrays de ejemplo, éxitos con temporizadores o links falsos.
7. V12–V15 son las migraciones nuevas de este cierre. No reescribir V1–V11 ni borrar historia clínica para hacer pasar V12. Verificar qué migraciones se aplicaron realmente antes de cambiar cualquiera.

## 4. Evidencia disponible y límites

Evidencia del último cierre: `Cierre_Funcional_CORONYX_2026-10-18/EVIDENCIA_VALIDACION.json`, relativa al workspace. Contiene hashes de los archivos de aquel corte y capturas con datos ficticios.

Resultados registrados de esa ejecución: **91 pruebas backend**, **10 frontend**, comprobación de TypeScript, build frontend y empaquetado backend aprobados. También se probaron en navegador Caja, Inventario y reserva → atención → autoguardado → firma, con recarga y base aislada.

Es evidencia histórica, no una ejecución de Antigravity ni una aprobación total. Los avisos documentales y la protección adicional del `.gitignore` se añadieron después: no restaurarlos para coincidir con hashes anteriores. No sobrescribir la evidencia histórica; guardar una ejecución posterior con su propia fecha y referencia de código.

Pendientes o límites explícitos:

- Recorrido completo y actualizado de los **34 casos por rol**, los controles y las vistas. Los estados APROBADO_LOCAL antiguos en CSV no prueban el estado de hoy.
- Videollamada integrada, alojamiento propio y prueba local con dos participantes: planificada el 19/10, sin implementar. La integración externa previa de Google tampoco se probó con una cuenta real.
- Sincronización automática del evento de Calendar al reprogramar/cancelar: no implementada. Hoy se avisa que el calendario también se gestiona manualmente.
- Recuperación de contraseña: asistida por administración. El correo automático no está implementado.
- Caja registra cobros recibidos; no procesa tarjetas ni emite comprobantes tributarios. Apertura/cierre físico, pasarela o facturación electrónica requieren requisitos adicionales si se exigen.
- Inventario no incluye lotes, vencimientos, compras o consumo automático por procedimiento. Confirmar su necesidad antes de ampliar el módulo.
- Despliegue final, recuperación de respaldos, almacenamiento duradero y migración sobre copia de datos actuales siguen pendientes de validación.

## 5. Tareas para ejecutar en orden

Trabajar en bloques pequeños y completar cada flujo en frontend y backend. Corregir lo observado y registrar prueba/resultado; no empezar por regenerar pantallas, actualizar todas las dependencias o producir otra auditoría general sin ejecutar casos.

### T0 — Proteger la copia actual

Revisar rama y estado de ambos repositorios; registrar archivos modificados, nuevos e ignorados relevantes sin exponer secretos. Preparar un respaldo local verificable de los cambios y archivos nuevos antes de reorganizar. Incluir el código nuevo de `scripts/` y `tests/`, que antes estaba ignorado. Conservar el respaldo privado fuera de cualquier conjunto preparado para publicar. No usar un stash como único respaldo de archivos nuevos/ignorados.

**Entrega:** inventario inicial, ubicación privada del respaldo y comprobación de que puede recuperarse un archivo. Ningún cambio descartado.

### T1 — Ordenar documentación y temporales sin alterar la aplicación

Aplicar el plan de la sección 6. Empezar por documentación externa a los dos repositorios; no mezclar esta tarea con cambios funcionales. Conservar originales y evidencias; verificar enlaces y referencias después de mover. No eliminar código por parecer viejo o por no ser una ruta principal.

**Entrega:** registro origen → destino, inventario antes/después, accesos documentales actualizados y explicación de lo que se conserva privado o temporal.

### T2 — Validar los flujos de los cuatro roles y corregir fallos reales

Usar cuentas y datos ficticios en una base aislada. Leer `Planificacion_CORONYX/CASOS_VALIDACION_ROLES.csv`, `CONTROLES_OBLIGATORIOS.csv` y `ACEPTACION_VISTAS.csv` (o sus destinos registrados tras T1). Crear una campaña nueva, sin copiar los aprobados antiguos. Cada caso debe identificar rol, pasos, resultado esperado/observado, entorno, evidencia y estado APROBADO/FALLIDO/BLOQUEADO/NO_EJECUTADO.

Cubrir también clínica ajena, paciente ajeno, URL directa, sesión revocada, caída de API, recarga y acceso desde otra sesión. Probar agenda con reserva simultánea, reprogramación, ausencias y estados terminales; atención con autoguardado, conflicto de revisión, firma y corrección; archivos clínicos/administrativos con publicación, revocación y descarga. Corregir verticalmente cada fallo y repetir sus regresiones.

**Entrega:** matriz actualizada con evidencia por caso y diferencias pendientes frente a requisitos. Un test HTTP no sustituye una prueba de interfaz cuando el criterio requiere ambos.

### T3 — Implementación y aceptación de Inventario por presentación y estados (COMPLETADA)

Implementado según la revisión 2 del plan:
- Migración V16 crea columnas `codigo`, `presentacion` (UNIDAD, CAJA, ENVASE), `clasificacion` (CONSUMIBLE, REUTILIZABLE), `contenido_descriptivo`, `stock_cerrado`, `stock_en_uso` y constraint `check_stock_total`.
- Operaciones `ENTRADA`, `APERTURA` (cerrado → en uso; total no varía), `AGOTADO` (se retira de en uso; disminuye total y queda en historial), `SALIDA` (con motivo obligatorio), `DEVOLUCION` (reintegra reutilizable a disponible/cerrado) y `AJUSTE` administrativo.
- Previsualización y confirmación de importación de catálogos mediante `POST /api/v1/inventario/catalogo/previsualizar` y `POST /api/v1/inventario/catalogo/confirmar`. Clasifica automáticamente insumos físicos vs procedimientos/servicios (los cuales se omiten del inventario de insumos).
- Validación de independencia estricta de citas: la reserva, inicio, guardado, firma o corrección de atenciones clínicas no muta stock.
- Pruebas automatizadas: `InventoryAndTeleconsultationTest` (12 tests) integrados en `test-functional-closure.ps1` (36 tests backend totales pasados) y `inventory_teleconsultation.test.cjs` en frontend.

### T4 — Implementación y validación de Teleconsulta Integrada (COMPLETADA)

Implementado según la revisión 2 del plan:
- Backend: `POST /api/v1/teleconsulta/sesion/{citaId}` verifica que la cita sea virtual y no esté cancelada/finalizada. Autoriza estrictamente al odontólogo asignado o al paciente vinculado (o admin de la clínica). Genera identificador de sala opaco criptográfico `cx-...` (sin nombres ni DNI) y firma JWT con expiración para Jitsi Meet.
- Endpoint `POST /api/v1/teleconsulta/sesion/{citaId}/cerrar` finaliza formalmente la sala (solo moderador).
- Frontend: componente `TeleconsultationRoom.tsx` incrustado con iframe autenticado por JWT y controles de conexión, colgar/salir, finalizar sala y minimizar a esquina flotante.
- Integrado en `PacientePerfil.tsx`: docked persistentemente junto a los paneles clínicos; alternar entre Resumen, Historia clínica, Odontograma, Radiografías y Documentos mantiene la llamada conectada y el borrador de atención intacto.
- Integrado en `PatientApp.tsx`: el paciente ingresa desde su cita virtual directamente en el portal y solo recibe la llamada; nunca recibe el panel clínico del profesional.
- Infraestructura Jitsi: configuración Docker Compose en `Backend_Dentista/infrastructure/jitsi/docker-compose.yml`, `start-jitsi.ps1` y `env.example`.
- Nota de entorno: el servicio de Windows `com.docker.service` requiere ejecución con permisos de Administrador para arrancar Docker Desktop.

### T5 — Preparar entrega y probar recuperación

Sobre una copia de la base, ensayar migraciones y respaldo/restauración, comprobar archivos en almacenamiento persistente y variables del perfil `prod`. No apuntar las suites a datos de presentación o de pacientes reales. Preparar instrucciones reproducibles de despliegue y rollback, sin publicar ni modificar el entorno remoto en esta tarea.

**Entrega:** resultado del ensayo, configuración pendiente y lista corta de riesgos reales para la entrega.

### T6 — Preparar cambios revisables para GitHub

Separar backend y frontend. Revisar todos los archivos nuevos y el diff de lo que se vaya a incluir; no usar una inclusión indiscriminada que arrastre respaldos, datos de demo o credenciales. Proponer commits por bloque funcional en cada repositorio. Conservar las pruebas y scripts mantenidos permitidos expresamente por sus `.gitignore`.

**Entrega:** lista de archivos propuestos/excluidos, pruebas ejecutadas y mensajes de commit. Este traspaso solicita preparación local; hacer commit, push, merge o despliegue cuando el responsable lo indique. No usar force push, reescribir historial ni inicializar Git en la carpeta superior para publicar todo junto.

## 6. Plan concreto de organización

El objetivo es facilitar la navegación, no eliminar documentación necesaria. Propuesta para la raíz:

```text
CoronyxProyecto/
  Backend_Dentista/          # Repositorio backend; conserva su .git y docs técnicos
  Frontend_Dentista/         # Repositorio frontend; conserva su .git y docs técnicos
  ML_Laboratorio/            # Trabajo separado de otros compañeros
  Documentacion_CORONYX/     # Agrupa los siete directorios documentales listados abajo
  tmp/                      # Trabajo temporal local; revisar antes de eliminar
  .vscode/                  # Preferencias del workspace; conservar
  ROADMAP.md                # Entrada breve que apunta al alcance vigente
  .gitignore                # Solo afecta a un eventual repo superior; hoy no existe
  Coronyx_API_Collection.postman_collection.json
```

Mantener los nombres originales de estos siete directorios, agrupándolos bajo `Documentacion_CORONYX/`:

- `Auditoria_CORONYX_2026-10-17/`.
- `Auditoria_CORONYX_2026-10-18/`.
- `Cierre_Funcional_CORONYX_2026-10-18/`.
- `Entregables_Avance_2/`.
- `Entregables_Presentacion_Avance_2/`.
- `Entregables_Sprints_Corregidos/`.
- `Planificacion_CORONYX/`.

**Este movimiento está propuesto; no se realizó al crear esta guía.** Antes de ejecutarlo, buscar referencias a las rutas antiguas en documentación, scripts, enlaces absolutos y `ML_Laboratorio/README.md`. Registrar rutas originales/destino y hashes de archivos. Usar copia verificada seguida de traslado reversible, sin borrar originales hasta comprobar el resultado y sin sobrescribir destinos existentes. Actualizar enlaces activos; para evidencias históricas con rutas antiguas, conservar su contenido y registrar el mapa de localización actual en un índice.

Los `docs/` dentro de cada repositorio permanecen allí. La colección Postman raíz se conserva inicialmente: comparar con `Backend_Dentista/postman` y `docs/postman` antes de decidir si es un duplicado. No borrar un archivo solo por compartir nombre o apariencia.

`Frontend_Dentista/node_modules/`, `dist/` y `Backend_Dentista/target/` son salidas regenerables e ignoradas por Git. No impiden publicar el código y no es necesario borrarlas para ordenar. `target/closure-*` y `target/preview-*` contienen bases temporales: comprobar que sus procesos estén detenidos y guardar la evidencia necesaria antes de cualquier eliminación. No confundirlos con bases o almacenamiento reales.

`tmp/` contiene material de elaboración y adjuntos de demostración. Revisar referencias y conservar lo usado por scripts/entregables; solo eliminar archivos concretos cuya regeneración y falta de uso estén demostradas. Mantener datos privados, dumps y adjuntos fuera de commits. No copiar `Planificacion_CORONYX/Privado_Presentacion` a un repositorio público.

Una página que parezca duplicada en `src/` requiere comprobar imports, rutas y uso antes de proponer su retiro; revisar además build y flujos afectados. No tocar archivos de voz/ML o Superadministrador durante la limpieza documental. La carpeta `.git` y los archivos de dependencias, configuración, migración y tests no son basura.

## 7. Cómo verificar sin tocar la base habitual

Frontend, desde `Frontend_Dentista`:

```powershell
pnpm typecheck
pnpm test
pnpm build
```

Usar el gestor declarado, pnpm 10.34.3. Hay dos lockfiles históricos: no alternar npm/pnpm, regenerarlos o borrar uno como parte de la limpieza. El formateador `oxfmt` instalado produjo errores en tipos de TypeScript durante el cierre; evitar formateo masivo y comprobar tipos si se usa.

Backend, desde `Backend_Dentista`:

```powershell
.\scripts\test-functional-closure.ps1 -AllTests
```

El script crea/detiene un PostgreSQL 18 temporal en loopback 55462, usa almacenamiento aislado, Java 21 y Maven del caché local. Funciona con dependencias ya disponibles; si el entorno cambió, explicar la dependencia faltante y adaptar la herramienta sin recurrir a la base habitual. No interpretar fallos del entorno como aprobación ni quitar controles de aislamiento para ejecutar tests.

Para probar en navegador, primero empaquetar la versión actual del backend con Java 21 y el Maven Wrapper; `scripts/start-closure-preview.ps1` ejecuta el JAR ya generado, no recompila cambios. El preview usa API 18081, PostgreSQL 55463 y datos ficticios. Iniciar el frontend con `VITE_API_BASE_URL=http://127.0.0.1:18081/api/v1` y puerto 5177; mantener los procesos de preview durante la revisión y detener únicamente los propios al terminar. No cambiar permanentemente `.env` para esta revisión.

Mantener `.env`, JWT_SECRET, contraseñas, backups locales, dumps y datos privados fuera del diff publicable. Revisar `git check-ignore` y el conjunto de archivos preparado en **cada** repositorio. La exclusión del respaldo local en Maven no sustituye su exclusión en Git.

## 8. Primer mensaje listo para copiar

> Continúa CORONYX desde mi copia local `C:/Users/USER/Desktop/CoronyxProyecto`. Lee la revisión 2 de `Backend_Dentista/docs/PLAN_INVENTARIO_TELECONSULTA_2026-10-19.md`, esta guía y el anexo de requisitos. Revisa ambos repositorios en `feature/sebastian` y conserva sus cambios locales. El inventario se controla por UNIDAD/CAJA/ENVASE, distinguiendo cerrado/disponible y en uso, con agotamientos en el historial y acciones dentro de Inventario. No implementes consumo por atención, descuentos desde citas ni conversión obligatoria de cajas a piezas. La videollamada debe estar dentro de CORONYX, con alojamiento propio y prueba inicial en una computadora. ML, voz con IA y Superadministrador quedan fuera. El pedido actual es planificar; no asumas permiso para implementar sin una petición posterior. Al recibirla, ejecuta los bloques con pruebas aisladas, evidencia nueva y cambios revisables por repositorio. Conserva datos y secretos fuera de Git. No hagas commit, push, merge ni despliegue salvo petición expresa.
