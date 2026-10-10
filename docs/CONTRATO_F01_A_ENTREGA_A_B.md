# CONTRATO DE INTEGRACIÓN API REST (F01-A → DESARROLLADOR B)

**Para:** Desarrollador B (Frontend_Dentista)  
**De:** Desarrollador A (Backend_Dentista)  
**Fecha:** 10 de Octubre de 2026  
**Estado:** V4 Implementada, Probada y Validada en Ensayos  

---

## 1. Resumen Ejecutivo de Cambios

1. **Identificadores Decimal String:** Todos los identificadores en JSON, cabeceras, rutas URL y claims de JWT son ahora **cadenas de texto con dígitos decimales positivos** (ej: `"1"`, `"2"`, `"10"`, `"9007199254740995"`). Se prohíbe el uso de `Number(id)`, `parseInt(id)` o tipos numéricos primitivos de JavaScript para evitar pérdidas de precisión más allá de `Number.MAX_SAFE_INTEGER` (`9007199254740991`).
2. **Sesión Institucional y Distinción de Membresía:** La respuesta de inicio de sesión (`/api/v1/auth/login`) y los claims del JWT entregan de forma separada:
   - `usuarioId`: ID del usuario como persona/cuenta (`usuario.id`).
   - `usuarioClinicaId`: ID de la membresía/asignación activa en la clínica (`usuario_clinica.id`).
   - `clinicaId`: ID de la sede dental activa (`clinica.id`).
3. **Corte Total de Tokens UUID:** Cualquier token heredado que contenga formato UUID es rechazado con `401 Unauthorized`. Frontend debe limpiar `localStorage` y forzar nuevo login.

---

## 2. Especificación de Endpoints y Payloads

### 2.1 Autenticación (`/api/v1/auth`)

#### `POST /api/v1/auth/login`
- **Request Body:**
```json
{
  "correo": "odontologo@coronyx.pe",
  "clave": "123456"
}
```

- **Response Body (200 OK):**
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
  "nombreCompleto": "Andrés Herrera",
  "rol": "ODONTOLOGO",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipoToken": "Bearer"
}
```

#### `GET /api/v1/auth/users`
- **Response Body (200 OK):**
```json
[
  {
    "id": "1",
    "usuarioClinicaId": "1",
    "correo": "odontologo@coronyx.pe",
    "nombres": "Andrés",
    "apellidos": "Herrera",
    "estado": "ACTIVO",
    "rol": "ODONTOLOGO",
    "clinicaNombre": "Clínica Dental Coronyx - Sede Central"
  }
]
```

---

### 2.2 Gestión de Pacientes (`/api/v1/patients`)

#### `GET /api/v1/patients`
Lista todos los pacientes ordenados por fecha de creación descendente.
- **Response Body (200 OK):**
```json
[
  {
    "id": "1",
    "clinicaId": "1",
    "nombres": "Juan Carlos",
    "apellidos": "Pérez Miranda",
    "tipoDocumento": "DNI",
    "numeroDocumento": "74829104",
    "fechaNacimiento": "1992-04-12",
    "telefono": "+51 987654321",
    "correo": "jc.perez@gmail.com",
    "estado": "ACTIVO",
    "alergias": "Penicilina",
    "antecedentesMedicos": "Hipertensión leve",
    "medicamentos": null,
    "ciudad": "Lima",
    "tipoSangre": "O+",
    "seguro": "PACIFICO",
    "fechaCreacion": "2026-10-01T10:00:00Z",
    "fechaActualizacion": "2026-10-01T10:00:00Z"
  }
]
```

#### `GET /api/v1/patients/{id}`
- **Parámetro Ruta:** `{id}` como cadena decimal (ej: `/api/v1/patients/1`).
- **Response Body (200 OK):** Mismo schema de `PatientDto`.

#### `POST /api/v1/patients`
- **Request Body:**
```json
{
  "clinicaId": "1",
  "nombres": "Carlos",
  "apellidos": "Gamboa",
  "tipoDocumento": "DNI",
  "numeroDocumento": "77889900",
  "fechaNacimiento": "1995-05-20",
  "telefono": "+51 987654321",
  "correo": "carlos.gamboa@coronyx.pe",
  "alergias": "Penicilina",
  "antecedentesMedicos": "Ninguno",
  "estado": "ACTIVO"
}
```
- **Response Body (201 Created):** `PatientDto` con `id` asignado como decimal string (ej: `"5"`).

#### `PUT /api/v1/patients/{id}`
- **Response Body (200 OK):** `PatientDto` actualizado.

#### `PATCH /api/v1/patients/{id}/archive`
- **Response Body (200 OK):** `PatientDto` con `estado: "ARCHIVADO"`.

---

### 2.3 Agenda y Citas Odontológicas (`/api/v1/citas`)

#### `GET /api/v1/citas`
- **Query Params:** `?clinicaId=1&desde=2026-10-01T00:00:00Z&hasta=2026-10-31T23:59:59Z`
- **Response Body (200 OK):**
```json
[
  {
    "id": "1",
    "clinicaId": "1",
    "clinicaNombre": "Clínica Dental Coronyx - Sede Central",
    "pacienteId": "2",
    "pacienteNombreCompleto": "sebastian Gómez",
    "pacienteNumeroDocumento": "73343371",
    "pacienteTelefono": "+51 912345678",
    "odontologoId": "1",
    "odontologoNombreCompleto": "Dr. Andrés Herrera",
    "odontologoCorreo": "odontologo@coronyx.pe",
    "creadoPorId": "1",
    "creadoPorNombreCompleto": "Dr. Andrés Herrera",
    "inicioEn": "2026-10-15T09:00:00-05:00",
    "finEn": "2026-10-15T09:45:00-05:00",
    "modalidad": "PRESENCIAL",
    "estado": "PROGRAMADA",
    "estadoAsistencia": null,
    "motivo": "Evaluación semestral de ortodoncia",
    "consultorio": "Sillón 1",
    "enlaceTeleconsulta": null,
    "fechaCreacion": "2026-10-01T12:00:00Z",
    "fechaActualizacion": "2026-10-01T12:00:00Z"
  }
]
```

#### `POST /api/v1/citas`
- **Request Body:**
```json
{
  "clinicaId": "1",
  "pacienteId": "2",
  "odontologoId": "1",
  "creadoPorId": "1",
  "inicioEn": "2026-10-16T10:00:00-05:00",
  "finEn": "2026-10-16T10:45:00-05:00",
  "modalidad": "PRESENCIAL",
  "motivo": "Limpieza y profilaxis profunda",
  "consultorio": "Sillón 1"
}
```
- **Response Body (201 Created):** `AppointmentResponseDto`.

#### `PATCH /api/v1/citas/{id}/estado`
- **Request Body:**
```json
{
  "estado": "CONFIRMADA",
  "motivoCancelacion": null
}
```

#### `PATCH /api/v1/citas/{id}/asistencia`
- **Request Body:**
```json
{
  "estadoAsistencia": "ASISTIO"
}
```

#### `PUT /api/v1/citas/{id}/reprogramar?nuevoInicio=...&nuevoFin=...`
- **Response Body (200 OK):** `AppointmentResponseDto`.

---

## 3. Reglas de Validación y Manejo de Errores

| Código HTTP | Escenario | Payload de Error |
| :--- | :--- | :--- |
| `400 Bad Request` | Formato inválido de ID (no decimal), validación de campos (`@Valid`), horarios fuera de atención | `{"message": "...", "errors": {"campo": "motivo"}}` |
| `401 Unauthorized` | Credenciales incorrectas, token expirado, token con formato UUID heredado | `{"message": "Credenciales incorrectas..."}` |
| `404 Not Found` | Paciente o Cita no encontrado por ID | `{"message": "Paciente no encontrado con ID: 99"}` |
| `409 Conflict` | Solapamiento de horario de odontólogo o de sillón dental | `{"message": "Conflicto de horario: El Dr. Andrés Herrera ya tiene una cita activa..."}` |
