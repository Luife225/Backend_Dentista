# Entregable F02-A: Seguridad Completa, Filtros JWT y Sesión Validada

**Módulo:** `Backend_Dentista` (Spring Boot 4.0.8, Java 21, Spring Security 7.x, PostgreSQL, Flyway)  
**Destinatario:** Frontend (`Frontend_Dentista` - Equipo B)  
**Fecha:** 2026-10-10  
**Estado:** COMPLETADO Y VERIFICADO (16/16 tests unitarios e integración pasando)

---

## 1. Resumen Ejecutivo de Implementación

En cumplimiento del requerimiento **F02-A** y los controles obligatorios **G01** (Autenticación real), **G02** (Permisos y separación de datos) e **I05** (Sesiones y permisos), se implementó la arquitectura de seguridad integral en `Backend_Dentista`:

1. **Cadena de Seguridad (`SecurityFilterChain`) en Spring Boot 4.0.8**:
   - `SecurityConfig` configurado con sesiones *stateless* (`SessionCreationPolicy.STATELESS`), CSRF deshabilitado para APIs REST, soporte CORS institucional.
   - Manejadores de excepción REST compatibles con RFC-7807: `RestAuthenticationEntryPoint` (401 Unauthorized) y `RestAccessDeniedHandler` (403 Forbidden), garantizando que fallas de autenticación o autorización nunca respondan `500 Internal Server Error`.
2. **Filtro de Autenticación JWT en Tiempo Real (`JwtAuthenticationFilter`)**:
   - Hereda de `OncePerRequestFilter`. Intercepta peticiones HTTP con encabezado `Authorization: Bearer <token>`.
   - Extrae el identificador `userId` (decimal string) y valida la firma con clave secreta externalizada.
   - **Validación activa en cada petición**: Consulta en base de datos el estado del usuario (`ACTIVO`), el estado de la membresía clínica (`ACTIVO`) y el estado de la sede clínica (`ABIERTO`). Si un usuario o membresía es revocado o pasa a `DESHABILITADO`, la siguiente petición HTTP es rechazada de inmediato con `401 Unauthorized`.
3. **`UserPrincipal` Institucional**:
   - Clase que implementa `UserDetails` encapsulando los objetos de dominio: `User`, `ClinicUser` (membresía), `Clinic`, `Patient` (para rol paciente) y autoridad Spring `ROLE_<ROL>`.
   - Se inyecta en los endpoints vía `@AuthenticationPrincipal UserPrincipal principal`.
   - Todas las operaciones de negocio extraen `clinicaId`, `usuarioId` y `usuarioClinicaId` del principal validado; ningún endpoint confía en IDs enviados en el cuerpo JSON o parámetros para suplantar la sede o el autor.
4. **Autorización RBAC Estricta para los Cuatro Roles Operativos**:
   - Roles operativos: `ADMIN_CLINICA`, `RECEPCIONISTA`, `ODONTOLOGO`, `PACIENTE`.
   - `SUPER_ADMIN` conservado como rol del catálogo pero **denegado en endpoints operativos** (`/patients/**`, `/citas/**`). No se le asigna clínica de fallback ni se le permite operar historias o agendas (retorna `403 Forbidden`).
5. **Protección de Directorio y Datos Clínicos (G02)**:
   - `/api/v1/auth/users`: Restringido exclusivamente a `ADMIN_CLINICA` y acotado a los usuarios de su propia clínica.
   - Exclusión de datos clínicos en DTO administrativo: `PatientAdminDto` y enmascaramiento en `PatientService` omiten `alergias`, `antecedentesMedicos` y `medicamentos` (valor `null`) cuando quien consulta es `ADMIN_CLINICA` o `RECEPCIONISTA`. Solo `ODONTOLOGO` (y el propio `PACIENTE` en su expediente) reciben los datos clínicos.
6. **Contrato de Sesión `GET /api/v1/auth/me`**:
   - Implementado para que el frontend obtenga la identidad autorizada real y verifique su sesión activa sin decodificar tokens en cliente ni exponer tokens en el cuerpo.
7. **Endurecimiento Criptográfico y Limpieza de Logs**:
   - Eliminado todo soporte de contraseñas en texto plano. Todas las credenciales se validan exclusivamente con `BCryptPasswordEncoder`.
   - Secreto JWT externalizado (`${JWT_SECRET}`) en `application.yml`.
   - Eliminados logs de tokens en consola/archivos (cumplimiento control I05).

---

## 2. Contrato de Sesión para Frontend (Equipo B)

### `GET /api/v1/auth/me`
- **Método:** `GET`
- **Ruta:** `/api/v1/auth/me`
- **Headers Requeridos:** `Authorization: Bearer <token_jwt>`
- **Respuestas:**
  - `200 OK`: Sesión válida.
  - `401 Unauthorized`: Token ausente, inválido, expirado o usuario/membresía deshabilitados en BD.
  - `403 Forbidden`: Usuario sin permisos o sin rol institucional asignado.

#### Payload JSON de Éxito (`200 OK`)
```json
{
  "id": "1",
  "usuarioId": "1",
  "usuarioClinicaId": "1",
  "clinicaId": "1",
  "clinicaNombre": "Clínica Dental Coronyx - Sede Central",
  "correo": "odontologo@coronyx.pe",
  "nombres": "Andrés",
  "apellidos": "Herrera",
  "rol": "ODONTOLOGO",
  "pacienteId": null
}
```

#### Reglas de Tipado e IDs:
1. `usuarioId`, `id`, `usuarioClinicaId` y `clinicaId` son **cadenas decimales positivas** (`string` en TypeScript, `^[1-9]\d*$`).
2. Se diferencia explícitamente `usuarioId` (ID en tabla `usuario`) de `usuarioClinicaId` (ID en tabla `usuario_clinica`).
3. Para usuarios con rol `PACIENTE`, `pacienteId` contendrá el ID decimal string de su registro en tabla `paciente`; para roles del personal clínico, es `null`.
4. El endpoint `GET /api/v1/auth/me` **no retorna el token JWT** para evitar reflexiones innecesarias. El token se conserva en la cabecera HTTP de la petición.

---

## 3. Matriz de Permisos y Códigos de Respuesta HTTP

| Recurso / Endpoint | ADMIN_CLINICA | RECEPCIONISTA | ODONTOLOGO | PACIENTE | SUPER_ADMIN | Regla de Seguridad |
|---|---|---|---|---|---|---|
| `GET /api/v1/auth/me` | 200 | 200 | 200 | 200 | 200* (sin sede) | Devuelve sesión validada del principal actual |
| `GET /api/v1/auth/users` | 200 (propia clínica) | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | Directorio administrativo de personal |
| `GET /api/v1/patients` | 200 (sin datos clínicos) | 200 (sin datos clínicos) | 200 (con datos clínicos) | 403 Forbidden | 403 Forbidden | Listado de pacientes de la propia clínica |
| `GET /api/v1/patients/{id}` | 200 (sin datos clínicos) | 200 (sin datos clínicos) | 200 (con datos clínicos) | 200 (solo expediente propio) | 403 Forbidden | Acceso a expediente. Paciente ajeno = 404 (multi-sede) o 403 (vecino) |
| `POST /api/v1/patients` | 200 | 200 | 403 Forbidden | 403 Forbidden | 403 Forbidden | Alta de paciente en sede del principal |
| `PUT /api/v1/patients/{id}` | 200 | 200 | 403 Forbidden | 403 Forbidden | 403 Forbidden | Edición demográfica de paciente |
| `GET /api/v1/citas` | 200 | 200 | 200 | 403 Forbidden | 403 Forbidden | Agenda general de la clínica del principal |
| `GET /api/v1/citas/paciente/{id}` | 200 | 200 | 200 | 200 (solo citas propias) | 403 Forbidden | Citas de un paciente específico |
| `POST /api/v1/citas` | 200 | 200 | 403 Forbidden | 200 (auto-reserva) | 403 Forbidden | Creación de cita en la sede autorizada |

### Comportamiento de Seguridad para Estados Anómalos:
- **Sin Token / Token manipulado / Token UUID antiguo:** `401 Unauthorized` (RFC-7807).
- **Usuario o membresía desactivados en BD:** `401 Unauthorized` de inmediato en la siguiente petición.
- **Acceso a recurso de otra clínica (Multi-Tenant):** `404 Not Found` en expedientes de pacientes (para evitar enumeración y revelación de existencia de pacientes de otras clínicas) o `403 Forbidden` en agendas de otra sede. Ninguna modificación ocurre.
- **Paciente intentando acceder al ID de un paciente vecino:** `403 Forbidden` explícito.
- **Fallos de autorización:** Nunca devuelven `500 Internal Server Error`.

---

## 4. Evidencia de Ejecución de Pruebas Automatizadas

La suite de pruebas de integración HTTP `SecurityFilterChainIntegrationTest` fue ejecutada contra la base de datos real a través de los filtros de Spring Security (`MockMvc` con `springSecurityFilterChain` activo):

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running pe.edu.utp.coronyx.backend.SecurityFilterChainIntegrationTest
2026-10-10T03:28:03.356-05:00  WARN ... [AUTH-EX] 403 en /api/v1/citas: Acceso denegado: no tiene autorización para consultar citas de otra clínica
2026-10-10T03:28:05.045-05:00  WARN ... [SEC] Usuario odontologo@coronyx.pe se encuentra desactivado (DESHABILITADO)
2026-10-10T03:28:05.297-05:00  WARN ... [SEC] Token inválido o expirado en petición: /api/v1/auth/me
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.871 s -- in pe.edu.utp.coronyx.backend.SecurityFilterChainIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Detalle de los 9 Casos del Filtro de Seguridad:
1. `testEndpointsWithoutTokenReturn401`: Petición a `/auth/me`, `/patients`, `/citas`, `/auth/users` sin token retorna `401 Unauthorized` RFC-7807.
2. `testInvalidOrLegacyTokenReturns401`: Petición con token corrupto o token con `userId` en formato UUID heredado retorna `401 Unauthorized`.
3. `testDeactivatedUserReturns401OnNextRequest`: Tras login exitoso, al marcar en BD `usuario.estado = 'DESHABILITADO'`, la siguiente petición retorna `401 Unauthorized` en tiempo real.
4. `testWrongRoleReturns403`: Recepcionista intentando consultar `/auth/users` retorna `403 Forbidden`. SUPER_ADMIN intentando consultar `/patients` retorna `403 Forbidden`.
5. `testCrossClinicPatientAccessReturns404`: Usuario de Sede Central intentando leer o mutar paciente de Sede Secundaria retorna `404 Not Found` sin modificar datos.
6. `testCrossClinicAppointmentsReturns403`: Usuario intentando filtrar citas de otra clínica retorna `403 Forbidden`.
7. `testPatientAccessingNeighborIdReturns403`: Paciente titular consultando su propio expediente retorna `200 OK`; consultando paciente vecino o citas vecinas retorna `403 Forbidden`.
8. `testAdminDtoExcludesClinicalData`: ADMIN_CLINICA recibe `alergias = null`, `antecedentesMedicos = null`, `medicamentos = null`. ODONTOLOGO recibe los datos clínicos completos.
9. `testAuthMeReturnsAgreedContract`: `GET /api/v1/auth/me` retorna todos los campos requeridos con IDs decimal string diferenciados (`usuarioId`, `usuarioClinicaId`, `clinicaId`) y sin token expuesto.

---

## 5. Instrucciones para Frontend (Equipo B)

1. **Uso de `GET /api/v1/auth/me` al inicializar sesión:**
   - En `AuthContext.tsx` o al cargar la aplicación con un token en `sessionStorage` / `localStorage`, ejecutar `GET /api/v1/auth/me`.
   - Si responde `401` o `403`, limpiar el token local y redirigir a `/login`.
   - Si responde `200 OK`, poblar el estado del usuario autenticado con `usuarioId`, `usuarioClinicaId`, `clinicaId`, `rol`, etc.
2. **No depender de Guards de React para seguridad:**
   - Los guards de React son únicamente para experiencia de usuario (navegación visual).
   - El backend deniega cualquier operación no autorizada con `401`, `403` o `404`. El cliente debe manejar estos códigos y mostrar notificaciones amigables sin colapsar.
3. **No solicitar datos clínicos desde vistas de administración o recepción:**
   - Los campos clínicos vendrán como `null` en `PatientAdminDto`. Las tablas y formularios administrativos no deben intentar mostrarlos ni sobreescribirlos.
