# CORONYX - Entregable F00-A: Preparación de Entorno, Inventario y Contrato Común

**Fecha:** 10 de octubre de 2026  
**Responsable:** Desarrollador A (Backend y Base de Datos)  
**Versión de Roadmap:** v2.0 · F00 Preparatorio  
**Estado:** Documentado y Acordado para Desarrollo  

---

## 1. Registro de Entorno, Commits y Estado de Migraciones

### 1.1 Entorno de Ejecución
- **Sistema Operativo:** Windows 11 Pro 64-bit.
- **Motor de Base de Datos:** PostgreSQL 18.6 (Servicio local `postgresql-x64-18`).
- **JDK:** Java 25.0.4 LTS (Compatible con especificación `java.version = 21` de Maven).
- **Framework:** Spring Boot 4.0.8 (`spring-boot-starter-parent` 4.0.8).
- **Control de Migraciones:** Flyway Community Edition integrado con PostgreSQL.
- **JPA / Hibernate:** Hibernate ORM con `hibernate.ddl-auto: validate` (Prohibido `create` o `update`).

### 1.2 Registro de Commits Base
- **Backend_Dentista (HEAD):**  
  `f94219854a36b08ff0266eabb27581f4ca45b5ab`  
  *Mensaje:* `[v13] feat(appointments): implementar modulo integral de agenda y citas con validacion anti-solapamiento`
- **Frontend_Dentista (HEAD):**  
  `732e77112e04ddeb7043b1d430fbc0730b156a3c`  
  *Mensaje:* `[v30] feat(appointments): implementar agenda odontologica, anti-solapamiento y agendamiento visual con API REST`

### 1.3 Estado y Checksums de Migraciones Flyway Existentes
Las migraciones aplicadas en V1–V3 se mantienen **completamente intactas e inmutables**:
1. `V1__initial_schema.sql`:
   - SHA-256: `C55C00B456DAF767A8F598C13EA51988D984ECE26BADA8E43390196B1C79BBBC`
   - Alcance: Creación de las 12 tablas con claves UUID y RBAC base.
2. `V2__seed_users_and_roles.sql`:
   - SHA-256: `F56FA18B9FDF8B2A64FF2013D77162C71B5BF582AD2A7E5E86F111F61BCE52CE`
   - Alcance: Catálogo de roles, clínica central y usuarios iniciales.
3. `V3__add_patient_extended_fields.sql`:
   - SHA-256: `CC7D412FEBC5DC55ACB4054C86787311F3C125F4B5DD6F13DBA7CBEFEB016714`
   - Alcance: Columnas `ciudad`, `tipo_sangre`, `seguro` y soporte de `CC` en `paciente`.

> **Reserva Flyway:** La siguiente migración libre para el cambio a `BIGINT identity` queda reservada exclusivamente como **`V4__migrate_uuids_to_bigint.sql`**.

---

## 2. Inventario Exhaustivo: 12 Tablas Originales y 32 Foreign Keys (FK)

A continuación se detalla el censo completo de las 12 tablas y sus 32 restricciones de clave foránea según el esquema físico actual:

| # | Tabla | Dominio | Columnas | Llave Primaria (V1-V3) | Total FKs | Detalle de Claves Foráneas (FK) |
|---|---|---|---|---|---|---|
| **1** | `clinica` | Multi-Sede | 7 | `id` (UUID) | 0 | Ninguna (Tabla Raíz / Tenant). |
| **2** | `usuario` | Acceso / RBAC | 12 | `id` (UUID) | 0 | Ninguna (Cuenta Global). |
| **3** | `rol` | Catálogo RBAC | 7 | `id` (UUID) | 0 | Ninguna (Catálogo Global). |
| **4** | `usuario_clinica` | Membresía Sede | 8 | `id` (UUID) | 4 | **FK1:** `clinica_id` → `clinica(id)` [CASCADE]<br>**FK2:** `usuario_id` → `usuario(id)` [CASCADE]<br>**FK3:** `rol_id` → `rol(id)` [RESTRICT]<br>**FK4:** `rol_asignado_por` → `usuario(id)` [SET NULL] |
| **5** | `paciente` | Expediente | 20 | `id` (UUID) | 3 | **FK5:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK6:** `usuario_id` → `usuario(id)` [SET NULL]<br>**FK7:** `medico_actualizo_id` → `usuario_clinica(id)` [SET NULL] |
| **6** | `cita` | Agenda | 15 | `id` (UUID) | 4 | **FK8:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK9:** `paciente_id` → `paciente(id)` [RESTRICT]<br>**FK10:** `odontologo_id` → `usuario_clinica(id)` [RESTRICT]<br>**FK11:** `creado_por_id` → `usuario_clinica(id)` [RESTRICT] |
| **7** | `atencion_clinica` | Encuentro Clínico | 8 | `id` (UUID) | 4 | **FK12:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK13:** `paciente_id` → `paciente(id)` [RESTRICT]<br>**FK14:** `cita_id` → `cita(id)` [SET NULL, UNIQUE]<br>**FK15:** `odontologo_id` → `usuario_clinica(id)` [RESTRICT] |
| **8** | `version_atencion_clinica` | Historia Inmutable | 15 | `id` (UUID) | 4 | **FK16:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK17:** `atencion_clinica_id` → `atencion_clinica(id)` [CASCADE]<br>**FK18:** `version_anterior_id` → `version_atencion_clinica(id)` [SET NULL]<br>**FK19:** `firmado_por` → `usuario_clinica(id)` [SET NULL] |
| **9** | `version_odontograma` | Odontograma | 9 | `id` (UUID) | 4 | **FK20:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK21:** `paciente_id` → `paciente(id)` [RESTRICT]<br>**FK22:** `version_anterior_id` → `version_odontograma(id)` [SET NULL]<br>**FK23:** `firmado_por` → `usuario_clinica(id)` [SET NULL] |
| **10** | `hallazgo_odontograma` | Hallazgos Diente | 7 | `id` (UUID) | 2 | **FK24:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK25:** `version_odontograma_id` → `version_odontograma(id)` [CASCADE] |
| **11** | `archivo_adjunto` | Archivos / Rayos X | 18 | `id` (UUID) | 5 | **FK26:** `clinica_id` → `clinica(id)` [RESTRICT]<br>**FK27:** `paciente_id` → `paciente(id)` [RESTRICT]<br>**FK28:** `subido_por_usuario_id` → `usuario(id)` [RESTRICT]<br>**FK29:** `cita_id` → `cita(id)` [SET NULL]<br>**FK30:** `ia_revisado_por` → `usuario_clinica(id)` [SET NULL] |
| **12** | `notificacion` | Mensajería | 9 | `id` (UUID) | 2 | **FK31:** `clinica_id` → `clinica(id)` [CASCADE]<br>**FK32:** `usuario_destinatario_id` → `usuario(id)` [CASCADE] |

**Total comprobado:** 12 tablas y exactamente **32 Foreign Keys**.

---

## 3. Inventario de Componentes Backend y Brechas Detectadas

### 3.1 Modelos de Entidad Activos (Java)
- `pe.edu.utp.coronyx.backend.model.Clinic` (`UUID id`)
- `pe.edu.utp.coronyx.backend.model.User` (`UUID id`)
- `pe.edu.utp.coronyx.backend.model.Role` (`UUID id`)
- `pe.edu.utp.coronyx.backend.model.ClinicUser` (`UUID id`)
- `pe.edu.utp.coronyx.backend.model.Patient` (`UUID id`)
- `pe.edu.utp.coronyx.backend.model.Appointment` (`UUID id`)
*Nota:* Las entidades para atención clínica, odontograma, archivos y notificaciones no están implementadas aún en código Java; nacerán directamente con `Long` en sus respectivas fases (F05, F06, F08).

### 3.2 Brechas y Hallazgos a Resolver en F01 y F02
1. **Tipos de Identificador:** Todas las entidades actuales usan `UUID` y `@GeneratedValue(strategy = GenerationType.UUID)`. Deben migrar a `Long` con `GenerationType.IDENTITY`.
2. **Validación Rígida de Edad (5–100 años):**  
   En `PatientService.java` (líneas 207-213) se rechaza el registro de pacientes menores a 5 años (`edad < 5`), lo cual bloquea la odontopediatría. En F03 se corregirá manteniendo únicamente la regla de que la fecha de nacimiento no sea futura.
3. **Registro de Tokens en Logs:**  
   `AuthController.java` (línea 31) imprimía el token JWT en el log de Spring Boot (`log.info("... Token: {}", ...)`). Debe eliminarse en F02 para cumplir el control de seguridad I05 / G01.
4. **Directorio sin Autorización:**  
   `GET /api/v1/auth/users` expone todos los usuarios sin exigir Bearer Token. Debe protegerse y limitarse en F02/F03.
5. **Fallback de Clínica por Defecto:**  
   Si una petición no especifica `clinicaId`, se asignaba la primera clínica de la base de datos (`SELECT id FROM clinica LIMIT 1`). Este fallback se retirará en F02: la clínica vendrá estrictamente del token validado del usuario autenticado.

---

## 4. Instrucciones para Respaldo y Base de Ensayo (Staging Sandbox)

Para ejecutar las pruebas de migración V4 sin arriesgar ni mutar la base original `coronyx`, se establece el siguiente procedimiento reproducible:

### 4.1 Respaldo Seguro de la Base de Datos Original
Se utiliza `pg_dump` mediante variable de entorno para evitar registrar credenciales en el historial de comandos o logs:

```powershell
# 1. Configurar variables en la sesión temporal de PowerShell
$env:PGHOST = "localhost"
$env:PGPORT = "5432"
$env:PGUSER = "akise"
# $env:PGPASSWORD se configura de forma interactiva si no está en pgpass

# 2. Generar volcado binario custom (comprimido y consistente)
& "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" `
    -h $env:PGHOST -p $env:PGPORT -U $env:PGUSER `
    -F c -b -v `
    -f "c:\Users\USER\Desktop\CoronyxProyecto\Backend_Dentista\backup_coronyx_pre_v4.dump" `
    coronyx
```

### 4.2 Creación de Base de Datos de Ensayo (`coronyx_ensayo`)
```powershell
# 1. Crear la base de datos aislada para probar V4
& "C:\Program Files\PostgreSQL\18\bin\createdb.exe" `
    -h $env:PGHOST -p $env:PGPORT -U $env:PGUSER `
    -O akise `
    coronyx_ensayo

# 2. Restaurar el estado inicial exacto de V1-V3
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" `
    -h $env:PGHOST -p $env:PGPORT -U $env:PGUSER `
    -d coronyx_ensayo -v `
    "c:\Users\USER\Desktop\CoronyxProyecto\Backend_Dentista\backup_coronyx_pre_v4.dump"
```

### 4.3 Validación del Ensayo
- La migración `V4__migrate_uuids_to_bigint.sql` se aplicará primero contra `coronyx_ensayo`.
- Se verificará que:
  * El conteo de filas de cada una de las 12 tablas sea idéntico antes y después.
  * Todas las 32 FK apunten a los nuevos identificadores numéricos correspondientes.
  * Las secuencias `GENERATED ALWAYS AS IDENTITY` queden ajustadas al valor `MAX(id) + 1`.
  * La inserción de un nuevo registro no colisione con los IDs migrados.

---

## 5. Diseño del Dataset Persistido (4 Roles Operativos y 2 Clínicas)

Para garantizar aislamiento multi-tenant y pruebas exhaustivas de negativos, el dataset modela 2 clínicas independientes y cuentas explícitas con diferenciación de `usuario.id`, `usuario_clinica.id` y `paciente.id`:

### 5.1 Sedes Odontológicas (`clinica`)
1. **Clínica 1 (Principal):**  
   - `id`: `"1"`
   - `nombre`: `"Clínica Dental Coronyx - Sede Central"`
   - `razon_social`: `"Coronyx Dental S.A.C."`
   - `estado`: `"ABIERTO"`
   - `zona_horaria`: `"America/Lima"`
2. **Clínica 2 (Control Negativo Multi-Sede):**  
   - `id`: `"2"`
   - `nombre`: `"Clínica Dental San Isidro (Control Negativo)"`
   - `razon_social`: `"San Isidro Dental S.A.C."`
   - `estado`: `"ABIERTO"`
   - `zona_horaria`: `"America/Lima"`

### 5.2 Cuentas y Membresías por Rol

| Propósito / Rol | Correo | Clave Inicial | `usuario.id` | `usuario_clinica.id` | `clinica_id` | Rol Asignado | `paciente.id` Asociado |
|---|---|---|---|---|---|---|---|
| **Admin Principal** | `admin@coronyx.pe` | `Admin2026!` | `"10"` | `"101"` | `"1"` | `ADMIN_CLINICA` | *N/A* |
| **Recepcionista** | `recepcion@coronyx.pe` | `Recep2026!` | `"11"` | `"102"` | `"1"` | `RECEPCIONISTA` | *N/A* |
| **Odontólogo Titular** | `odontologo@coronyx.pe` | `Odonto2026!` | `"12"` | `"103"` | `"1"` | `ODONTOLOGO` | *N/A* |
| **Odontólogo Secundario** | `odontologo2@coronyx.pe` | `Odonto2026!` | `"13"` | `"104"` | `"1"` | `ODONTOLOGO` | *N/A* |
| **Paciente Titular** | `paciente1@coronyx.pe` | `Pac2026!` | `"14"` | `"105"` | `"1"` | `PACIENTE` | `"501"` |
| **Paciente Alterno** | `paciente2@coronyx.pe` | `Pac2026!` | `"15"` | `"106"` | `"1"` | `PACIENTE` | `"502"` |
| **Admin Sede 2 (Negativo)**| `admin2@coronyx.pe` | `Admin2026!` | `"20"` | `"201"` | `"2"` | `ADMIN_CLINICA` | *N/A* |

> **Diferenciación Crucial de Entidades:**
> - `usuario.id` identifica a la persona/credencial global.
> - `usuario_clinica.id` identifica la membresía operativa del usuario dentro de una clínica específica.
> - `paciente.id` identifica el expediente médico dentro de la clínica (apuntando opcionalmente a `paciente.usuario_id`).
> - **Regla Inviolable:** `usuario.id != usuario_clinica.id != paciente.id != clinica.id`.

### 5.3 Política de `SUPER_ADMIN`
El rol `SUPER_ADMIN` se conserva en el catálogo RBAC para compatibilidad y fases futuras de infraestructura SaaS, pero **no posee cuenta operativa ni acceso a los flujos odontológicos del incremento actual**.

---

## 6. Contrato Común Acordado (Backend A ↔ Frontend B)

### 6.1 Contrato Numérico de Identificadores (IDs)
- **Base de Datos (PostgreSQL):** Columnas `BIGINT GENERATED ALWAYS AS IDENTITY`.
- **Backend (Java):** Atributos de tipo `Long`.
- **JSON / URL / TypeScript / JWT:** Representados estrictamente como **cadenas de texto decimal positivo** (ej: `"101"`, `"501"`, `"9007199254740992"`).
- **Prohibiciones en Frontend y Backend:**
  - Prohibido el uso de `Number(id)` o `parseInt(id, 10)` en TypeScript, ya que valores superiores a `Number.MAX_SAFE_INTEGER` (`9,007,199,254,740,991`) sufren redondeo destructivo.
  - Prohibido generar IDs en frontend mediante `Date.now()`, `Math.random()` o UUIDs locales para entidades persistidas.
  - Prohibido el uso de fallback demo si el ID no viene en la respuesta.

### 6.2 Contrato de Sesión y Autenticación

#### A. Inicio de Sesión: `POST /api/v1/auth/login`
**Request:**
```json
{
  "correo": "odontologo@coronyx.pe",
  "clave": "Odonto2026!"
}
```

**Response (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "usuarioId": "12",
  "usuarioClinicaId": "103",
  "clinicaId": "1",
  "clinicaNombre": "Clínica Dental Coronyx - Sede Central",
  "rol": "ODONTOLOGO",
  "correo": "odontologo@coronyx.pe",
  "nombres": "Andrés",
  "apellidos": "Herrera",
  "nombreCompleto": "Andrés Herrera",
  "pacienteId": null
}
```

#### B. Revalidación de Sesión: `GET /api/v1/auth/me`
Permite a Frontend revalidar el estado de autenticación tras recargar la página o cambiar de pestaña.
**Headers:** `Authorization: Bearer <token>`
**Response (`200 OK`):**
```json
{
  "usuarioId": "12",
  "usuarioClinicaId": "103",
  "clinicaId": "1",
  "clinicaNombre": "Clínica Dental Coronyx - Sede Central",
  "rol": "ODONTOLOGO",
  "correo": "odontologo@coronyx.pe",
  "nombres": "Andrés",
  "apellidos": "Herrera",
  "nombreCompleto": "Andrés Herrera",
  "pacienteId": null
}
```

### 6.3 Separación de DTOs: Administrativo vs. Clínico

1. **DTO Administrativo (`PatientAdminDto`):**
   - Destinado a: `ADMIN_CLINICA`, `RECEPCIONISTA`.
   - Campos incluidos: `id`, `clinicaId`, `nombres`, `apellidos`, `tipoDocumento`, `numeroDocumento`, `fechaNacimiento`, `telefono`, `correo`, `ciudad`, `seguro`, `estado`.
   - **Campos Estrictamente Omitidos:** `alergias`, `antecedentesMedicos`, `medicamentos`, diagnóstico, evolución, odontograma, radiografías privadas.
2. **DTO Clínico Integral (`PatientClinicalDto`):**
   - Destinado a: `ODONTOLOGO` (únicamente de la clínica y pacientes asignados).
   - Incluye todos los campos demográficos más antecedentes médicos, alergias, recetas y atenciones clínicas.
3. **DTO Portal Paciente (`PatientPortalProfileDto`):**
   - Destinado a: `PACIENTE`.
   - Incluye datos personales propios y acceso a citas propias.

### 6.4 Catálogo y Estructura Estándar de Errores HTTP
Las respuestas de error adoptan formato homogéneo RFC-7807 compatible:

```json
{
  "timestamp": "2026-10-10T01:00:00-05:00",
  "status": 409,
  "error": "Conflict",
  "message": "Conflicto de agenda: el profesional ya cuenta con una cita en el horario seleccionado.",
  "path": "/api/v1/citas"
}
```

- `400 Bad Request`: Formato de ID inválido, datos requeridos faltantes o fecha sintácticamente inválida.
- `401 Unauthorized`: Token JWT ausente, expirado, firma incorrecta o cuenta desactivada.
- `403 Forbidden`: Intento de acceder a recursos con un rol no autorizado o clínica distinta.
- `404 Not Found`: Recurso inexistente o perteneciente a otra clínica/paciente (política de ocultación de datos).
- `409 Conflict`: Solapamiento de citas simultáneas o colisión de versión concurrente.

---

## 7. Estrategia de Almacenamiento Privado Durable (F06)

Para satisfacer la persistencia durable de radiografías y consentimientos sin exponer binarios en carpetas efímeras de contenedores Railway:
1. **Interfaz Desacoplada (`StorageService`):**
   - `String store(MultipartFile file, String pathPrefix, String clinicId);`
   - `InputStream load(String storageKey);`
   - `void delete(String storageKey);`
2. **Implementación Local (Desarrollo):**
   - `LocalStorageServiceImpl`: Almacena en ruta absoluta configurada fuera del classpath/target (`storage.local.directory`), retornando claves relativas protegidas.
3. **Implementación Cloud (Railway / Producción):**
   - `S3StorageServiceImpl`: Utiliza el SDK compatible con S3 para conectar con el Bucket Privado de Railway mediante variables `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`.
4. **Seguridad:**
   - La descarga se efectúa exclusivamente a través de un endpoint protegido (`GET /api/v1/attachments/{id}/download`), validando en tiempo real si el usuario o paciente tiene derecho de acceso antes de transmitir el flujo de bytes.

---

## 8. Resumen de Aceptación del Bloque F00-A

- [x] Inventario de las 12 tablas y 32 llaves foráneas verificado al 100%.
- [x] Commits base y versiones de tecnología registradas.
- [x] Checksums SHA-256 de V1, V2 y V3 congelados y validados.
- [x] Reserva de versión Flyway `V4` asignada formalmente.
- [x] Procedimiento seguro de respaldo y copia de ensayo documentado sin secretos.
- [x] Dataset de 4 roles, 2 clínicas y IDs diferenciados completamente diseñado.
- [x] Contrato común de IDs (decimal string), sesión (`/auth/me`), DTOs y errores formalizado para entrega a Frontend (B).
