# Entregable F03-A: Cuentas y Pacientes con Permisos

**Módulo:** `Backend_Dentista` (Java 21, Spring Boot 4.0.8, PostgreSQL, Spring Security, Flyway)  
**Dependencia:** F02 (Seguridad completa y sesión validada)  
**Fecha:** 2026-10-10  
**Estado:** COMPLETADO Y VERIFICADO (47/47 pruebas HTTP en vivo y 29/29 pruebas Maven pasando con 0 fallos)

---

## 1. Resumen Ejecutivo de Implementación

En cumplimiento de los requerimientos de **F03-A** y los criterios de aceptación **A01–A07** y **R01–R03**:

1. **CRUD Completo de Cuentas y Membresías de Clínica (`/api/v1/clinic/users`)**:
   - Exclusivo para el rol `ADMIN_CLINICA` en su propia sede operativa (aislamiento multi-sede).
   - Acciones implementadas:
     - `GET /api/v1/clinic/users`: Listar miembros y cuentas de la clínica del administrador.
     - `GET /api/v1/clinic/users/{id}`: Detalle de membresía/cuenta por ID.
     - `POST /api/v1/clinic/users`: Alta de nueva cuenta y membresía con contraseña hasheada en BCrypt (`$2a$12$`), roles autorizados del incremento (`ADMIN_CLINICA`, `ODONTOLOGO`, `RECEPCIONISTA`, `PACIENTE`).
     - `PUT /api/v1/clinic/users/{id}`: Edición de datos demográficos, cambio de rol o estado.
     - `PATCH /api/v1/clinic/users/{id}/status`: Activación o desactivación inmediata (`ACTIVO` / `INACTIVO`).
2. **Desactivación Afectando Sesión Previa en Tiempo Real**:
   - Al marcar una membresía como `INACTIVO`, `JwtAuthenticationFilter` detecta el estado inactivo de la membresía en la base de datos e inmediatamente rechaza cualquier solicitud con token previo con **HTTP 401 Unauthorized**.
3. **Control RBAC y Restricción de Roles (A04)**:
   - Se rechaza explícitamente cualquier intento de asignar `SUPER_ADMIN` dentro del ámbito de la clínica con `HTTP 400 Bad Request`.
   - Se protege contra la auto-desactivación del propio administrador activo.
   - Odontólogos, recepcionistas y pacientes son rechazados con **HTTP 403 Forbidden** al intentar acceder a `/api/v1/clinic/users`.
4. **Vinculación Explícita de Cuenta de Paciente sin Equiparar IDs**:
   - Se preserva la distinción entre `paciente.id` (registro clínico de sede) y `usuario.id` (cuenta de autenticación).
   - Se implementó `PATCH /api/v1/patients/{id}/link-user` para asociar un `usuarioId` sin modificar el `id` autogenerado del paciente ni equiparar ambos campos.
   - `PatientDto` y `PatientAdminDto` ahora incluyen `usuarioId` en sus respuestas.
5. **DTO Administrativo Separado de Clínica y Filtrado por Principal**:
   - Para roles administrativos (`ADMIN_CLINICA`, `RECEPCIONISTA`), los campos clínicos (`alergias`, `antecedentesMedicos`, `medicamentos`) son estrictamente `null` en `GET /api/v1/patients` y `GET /api/v1/patients/{id}` (Control G02).
   - Se incorporó `GET /api/v1/patients/admin` para proyección administrativa explícita (`PatientAdminDto`).
   - Los intentos de actualizar campos clínicos por parte de personal administrativo en `PUT /api/v1/patients/{id}` son ignorados, garantizando la inmutabilidad de la historia asistencial (A07).
6. **Eliminación de Primera Clínica por Defecto**:
   - Se eliminaron todos los fallbacks a `clinicRepository.findFirstByOrderByFechaCreacionAsc()`.
   - Si no existe sesión activa o el usuario no tiene clínica asignada, el sistema arroja `AccessDeniedException`.
7. **Eliminación de Regla Universal 5–100 Años en Pacientes**:
   - Se eliminó la validación rígida que rechazaba menores de 5 años o mayores de 100 años.
   - Se conservó la validación obligatoria de fecha válida y no futura (`fechaNacimiento.isAfter(hoy)`). Pacientes pediátricos o geriátricos son aceptados normalmente con `HTTP 201 Created`.
8. **Bootstrap Seguro y Flyway V5**:
   - Se creó la migración `V5__secure_bootstrap_and_clinic_cleanup.sql` sin modificar `V2`.
   - Se eliminaron las membresías operativas de `SUPER_ADMIN` en `usuario_clinica` para la clínica operativa.
   - Se aseguró que todos los usuarios institucionales tengan contraseñas con hash BCrypt.

---

## 2. Contrato de la API para Frontend (F03-B)

### 2.1. Gestión de Cuentas de Clínica (`/api/v1/clinic/users`)

| Método | Endpoint | Rol Requerido | Descripción |
|---|---|:---:|---|
| `GET` | `/api/v1/clinic/users` | `ADMIN_CLINICA` | Lista todas las cuentas y membresías de la clínica activa. |
| `GET` | `/api/v1/clinic/users/{id}` | `ADMIN_CLINICA` | Obtiene el detalle de una cuenta/membresía por su ID. |
| `POST` | `/api/v1/clinic/users` | `ADMIN_CLINICA` | Registra una nueva cuenta y membresía institucional. |
| `PUT` | `/api/v1/clinic/users/{id}` | `ADMIN_CLINICA` | Actualiza nombres, apellidos, rol o contraseña del miembro. |
| `PATCH` | `/api/v1/clinic/users/{id}/status` | `ADMIN_CLINICA` | Activa o desactiva la cuenta (`{"estado": "ACTIVO"\|"INACTIVO"}`). |

#### Payload de Creación (`POST /api/v1/clinic/users`):
```json
{
  "correo": "nuevo.profesional@coronyx.pe",
  "clave": "claveSegura123",
  "nombres": "Roberto",
  "apellidos": "Vargas",
  "rol": "ODONTOLOGO",
  "estado": "ACTIVO",
  "pacienteId": null
}
```

#### Respuesta de Cuenta / Membresía (`ClinicUserDto`):
```json
{
  "id": "5",
  "usuarioId": "6",
  "correo": "nuevo.profesional@coronyx.pe",
  "nombres": "Roberto",
  "apellidos": "Vargas",
  "nombreCompleto": "Roberto Vargas",
  "rol": "ODONTOLOGO",
  "rolNombre": "Odontólogo Especialista",
  "estado": "ACTIVO",
  "clinicaId": "1",
  "clinicaNombre": "Clínica Dental Coronyx - Sede Central",
  "pacienteId": null,
  "fechaAsignacion": "2026-10-10T10:03:00Z",
  "fechaActualizacion": "2026-10-10T10:03:00Z"
}
```

### 2.2. Vinculación de Paciente con Cuenta de Usuario

| Método | Endpoint | Rol Requerido | Descripción |
|---|---|:---:|---|
| `PATCH` | `/api/v1/patients/{id}/link-user` | `ADMIN_CLINICA`, `RECEPCIONISTA` | Vincula el `usuarioId` al `pacienteId`. |

#### Payload:
```json
{
  "usuarioId": "4"
}
```

#### Respuesta:
```json
{
  "id": "2",
  "clinicaId": "1",
  "usuarioId": "4",
  "nombres": "Juan Carlos",
  "apellidos": "Pérez Morales",
  "tipoDocumento": "DNI",
  "numeroDocumento": "12345678"
}
```

---

## 3. Matriz de Cobertura de Criterios (A01–A07 y R01–R03)

| Código | Rol | Función / Escenario | Criterio de Aceptación | Estado |
|:---:|:---:|---|---|:---:|
| **A01** | `ADMIN_CLINICA` | Crear cuenta de clínica | Cuenta persistente, contraseña hash BCrypt y nueva sesión real. | **APROBADO** |
| **A02** | `ADMIN_CLINICA` | Editar cuenta | Cambios en BD y sesión coherente; sin cambiar clínica ajena. | **APROBADO** |
| **A03** | `ADMIN_CLINICA` | Activar / desactivar cuenta | Cuenta/membresía desactivada pierde acceso inmediato (401), incluso con sesión previa. | **APROBADO** |
| **A04** | `ADMIN_CLINICA` | Asignar rol autorizado | Solo roles del alcance; rechaza SUPER_ADMIN (400) y auto-desactivación. | **APROBADO** |
| **A05** | `ADMIN_CLINICA` | Buscar/listar pacientes administrativos | Solo clínica del principal y DTO administrativo (campos clínicos en null). | **APROBADO** |
| **A06** | `ADMIN_CLINICA` | Registrar paciente administrativo | Paciente guardado, consultable por otro usuario, sin restricción 5–100. | **APROBADO** |
| **A07** | `ADMIN_CLINICA` | Editar paciente administrativo | Cambios persistentes demográficos sin modificar campos clínicos. | **APROBADO** |
| **R01** | `RECEPCIONISTA` | Buscar/listar pacientes | Solo información administrativa de su clínica (clínicos en null). | **APROBADO** |
| **R02** | `RECEPCIONISTA` | Registrar paciente | Alta exitosa y persistencia comprobada. | **APROBADO** |
| **R03** | `RECEPCIONISTA` | Actualizar paciente | Datos administrativos persistidos, sin alterar campos clínicos. | **APROBADO** |
| **NEG-01**| Varios | Control RBAC en `/clinic/users` | ODONTOLOGO, RECEPCIONISTA y PACIENTE rechazados con HTTP 403 Forbidden. | **APROBADO** |
| **NEG-02**| Varios | Solicitud anónima en `/clinic/users` | Rechazada con HTTP 401 Unauthorized. | **APROBADO** |
| **NEG-03**| Varios | Fecha de nacimiento futura | Rechazada con HTTP 400 Bad Request ("no puede ser futura"). | **APROBADO** |

---

## 4. Archivos Modificados y Creados

1. [ClinicUserController.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/controller/ClinicUserController.java): Controlador REST exclusivo para `ADMIN_CLINICA` (`/api/v1/clinic/users`).
2. [ClinicUserService.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/service/ClinicUserService.java): Lógica de negocio de cuentas, asignación de roles y activación/desactivación.
3. [ClinicUserDto.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/dto/ClinicUserDto.java), [CreateClinicUserDto.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/dto/CreateClinicUserDto.java), [UpdateClinicUserDto.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/dto/UpdateClinicUserDto.java), [StatusUpdateDto.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/dto/StatusUpdateDto.java): DTOs estructurados con validaciones y contratos decimal string.
4. [PatientService.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/service/PatientService.java): Eliminación de regla 5–100, soporte de `usuarioId`, vinculación `linkUserAccount`, endpoint administrativo, y eliminación de fallbacks a primera clínica.
5. [PatientController.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/controller/PatientController.java): Endpoints `/admin` y `/{id}/link-user`, eliminación de fallback de clínica por omisión.
6. [SecurityConfig.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/security/SecurityConfig.java): Protección de `/api/v1/clinic/users/**` restringido a `ADMIN_CLINICA`.
7. [V5__secure_bootstrap_and_clinic_cleanup.sql](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/main/resources/db/migration/V5__secure_bootstrap_and_clinic_cleanup.sql): Nueva migración Flyway para bootstrap seguro y desvinculación operativa de SUPER_ADMIN.
8. [clean_seed_data.sql](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/clean_seed_data.sql): Semillero limpio con exactamente 4 membresías operativas.
9. [ClinicUsersAndPatientsIntegrationTest.java](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/src/test/java/pe/edu/utp/coronyx/backend/ClinicUsersAndPatientsIntegrationTest.java): Suite de pruebas de integración JUnit/MockMvc con 13 pruebas.
10. [test_f03_a_backend.mjs](file:///c:/Users/USER/Desktop/CoronyxProyecto/Backend_Dentista/test_f03_a_backend.mjs): Suite de verificación HTTP en vivo contra puerto 8081 con 47 pruebas.
