# CATÁLOGO DE APIS Y ENDPOINTS REST - PROYECTO CORONYX
## Especificación de Endpoints para las 12 Tablas del Modelo Físico Relacional

> **Versión de API:** `v1`  
> **Host Base Local:** `http://localhost:8081/api/v1`  
> **Formato de Intercambio:** `application/json`  
> **Protocolo:** HTTP/1.1 - RESTful  
> **Autenticación:** Cabecera `Authorization: Bearer <token>` / Sesión de Usuario

---

## ÍNDICE GENERAL POR TABLA Y DOMINIO

| # | Tabla del Modelo Físico | Dominio Funcional | Recurso Endpoint Base | Métodos HTTP Soportados |
|:---:|:---|:---|:---|:---:|
| **1** | `clinica` | Multi-Sede y Tenant | `/api/v1/clinics` | `GET`, `POST`, `PUT`, `GET /{id}` |
| **2** | `usuario` | Cuentas y Credenciales | `/api/v1/users`, `/api/v1/auth` | `POST /login`, `GET /users`, `POST /users` |
| **3** | `rol` | Catálogo RBAC | `/api/v1/roles` | `GET`, `GET /{id}`, `PUT /{id}/permissions` |
| **4** | `usuario_clinica` | Membresía y Asignación | `/api/v1/clinic-memberships` | `GET`, `POST`, `PUT /{id}`, `DELETE /{id}` |
| **5** | `paciente` | Expediente Clínico | `/api/v1/patients` | `GET`, `POST`, `GET /{id}`, `PUT /{id}` |
| **6** | `cita` | Agenda y Teleconsulta | `/api/v1/appointments` | `GET`, `POST`, `PUT /{id}/status`, `GET /{id}` |
| **7** | `atencion_clinica` | Encuentro Médico | `/api/v1/clinical-encounters` | `GET`, `POST`, `GET /{id}`, `PUT /{id}/confirm` |
| **8** | `version_atencion_clinica` | Historia Inmutable y Voz | `/api/v1/clinical-encounters/{id}/versions` | `GET`, `POST`, `PUT /{verId}/sign` |
| **9** | `version_odontograma` | Odontograma Digital | `/api/v1/odontograms` | `GET`, `POST`, `GET /{id}`, `PUT /{id}/sign` |
| **10** | `hallazgo_odontograma` | Piezas Dentales FDI 11-85 | `/api/v1/odontograms/{id}/findings` | `GET`, `POST`, `PUT /{fId}`, `DELETE /{fId}` |
| **11** | `archivo_adjunto` | Radiografías e IA YOLOv8 | `/api/v1/attachments` | `POST (Multipart)`, `GET`, `PUT /{id}/review` |
| **12** | `notificacion` | Mensajería Transaccional | `/api/v1/notifications` | `GET`, `POST`, `PUT /{id}/read` |

---

## DOMINIO 1: SEGURIDAD, ACCESO Y MULTI-SEDE

### 1. Tabla: `clinica`
Gestiona las sedes odontológicas (inquilinos raíz) y su estado operativo.

#### 1.1 Listar Clínicas
- **Endpoint:** `GET /api/v1/clinics`
- **Descripción:** Obtiene las sedes registradas y sus horarios de operación.
- **Respuesta Exitosa (`200 OK`):**
```json
[
  {
    "id": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
    "nombre": "Clínica Dental Coronyx - Sede Central",
    "razonSocial": "Coronyx Dental S.A.C.",
    "zonaHoraria": "America/Lima",
    "estado": "ABIERTO",
    "fechaCreacion": "2026-09-20T10:00:00Z"
  }
]
```

#### 1.2 Registrar Nueva Sede Dental
- **Endpoint:** `POST /api/v1/clinics`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "nombre": "Clínica Dental Coronyx - Sede San Isidro",
  "razonSocial": "Coronyx Dental S.A.C.",
  "zonaHoraria": "America/Lima",
  "estado": "ABIERTO"
}
```
- **Respuesta Exitosa:** `201 Created`

---

### 2. Tabla: `usuario`
Gestiona las cuentas de acceso globales al sistema.

#### 2.1 Autenticación de Usuario (Login)
- **Endpoint:** `POST /api/v1/auth/login`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "correo": "odontologo@coronyx.pe",
  "clave": "123456"
}
```
- **Respuesta Exitosa (`200 OK`):**
```json
{
  "id": "6977fb4e-ae9f-4f58-8387-b2a26fd834c3",
  "correo": "odontologo@coronyx.pe",
  "nombres": "Andrés",
  "apellidos": "Herrera",
  "nombreCompleto": "Andrés Herrera",
  "rol": "ODONTOLOGO",
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "clinicaNombre": "Clínica Dental Coronyx - Sede Central"
}
```

#### 2.2 Listar Usuarios Registrados
- **Endpoint:** `GET /api/v1/auth/users`
- **Respuesta Exitosa (`200 OK`):** Lista con cuentas activas del personal y pacientes.

---

### 3. Tabla: `rol`
Gestiona los roles institucionales y sus facultades serializadas en formato JSONB.

#### 3.1 Listar Catálogo de Roles
- **Endpoint:** `GET /api/v1/roles`
- **Respuesta Exitosa (`200 OK`):**
```json
[
  {
    "id": "r001-uuid",
    "codigo": "ODONTOLOGO",
    "nombre": "Odontólogo Especialista",
    "descripcion": "Atención clínica, historia clínica, odontograma e IA",
    "permisos": ["PATIENT_VIEW", "CLINICAL_WRITE", "ODONTOGRAM_WRITE", "AI_REVIEW"],
    "activo": true
  },
  {
    "id": "r002-uuid",
    "codigo": "RECEPCIONISTA",
    "nombre": "Recepcionista",
    "descripcion": "Gestión de agenda, admisión de pacientes, creación de citas",
    "permisos": ["PATIENT_MANAGE", "APPOINTMENT_MANAGE", "PATIENT_VIEW"],
    "activo": true
  }
]
```

---

### 4. Tabla: `usuario_clinica`
Modela la membresía y qué usuario ejerce qué rol en una sede determinada.

#### 4.1 Asignar un Colaborador a una Clínica
- **Endpoint:** `POST /api/v1/clinic-memberships`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "usuarioId": "6977fb4e-ae9f-4f58-8387-b2a26fd834c3",
  "rolId": "r001-uuid",
  "rolAsignadoPor": "1a367af7-1c90-4a1e-a7bb-16f44234551a",
  "estado": "ACTIVO"
}
```
- **Respuesta Exitosa:** `201 Created`

#### 4.2 Listar Personal Asignado a una Sede
- **Endpoint:** `GET /api/v1/clinic-memberships?clinicaId=f3cfeb91-cccb-47da-9aee-5598ab1b99a7`
- **Respuesta Exitosa:** `200 OK` con listado de doctores, recepcionistas y administradores vinculados.

---

## DOMINIO 2: PACIENTES Y CITAS

### 5. Tabla: `paciente`
Expediente administrativo e historial de antecedentes de salud del paciente.

#### 5.1 Consultar Todos los Pacientes
- **Endpoint:** `GET /api/v1/patients`
- **Parámetros Query Opcionales:** `?buscar=Gamboa&estado=ACTIVO`
- **Respuesta Exitosa (`200 OK`):**
```json
[
  {
    "id": "0495cb2b-b238-431d-9757-1d44f009c90b",
    "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
    "nombres": "Luis Francisco",
    "apellidos": "Gamboa",
    "tipoDocumento": "DNI",
    "numeroDocumento": "61291062",
    "telefono": "+51 989992109",
    "correo": "luisgamboa3322@gmail.com",
    "alergias": "Ninguna",
    "antecedentesMedicos": "Alergia a mariscos",
    "estado": "ACTIVO"
  }
]
```

#### 5.2 Registrar Paciente
- **Endpoint:** `POST /api/v1/patients`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "nombres": "Rosa María",
  "apellidos": "Flores Huamán",
  "tipoDocumento": "DNI",
  "numeroDocumento": "45678901",
  "fechaNacimiento": "1975-11-28",
  "telefono": "+51 912345678",
  "correo": "rosa.flores@gmail.com",
  "alergias": "Penicilina",
  "antecedentesMedicos": "Diabetes tipo 2",
  "medicamentos": "Metformina 850mg",
  "estado": "ACTIVO"
}
```
- **Respuesta Exitosa:** `201 Created`

---

### 6. Tabla: `cita`
Gestión de turnos odontológicos (presenciales y teleconsulta).

#### 6.1 Agendar una Nueva Cita
- **Endpoint:** `POST /api/v1/appointments`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "pacienteId": "0495cb2b-b238-431d-9757-1d44f009c90b",
  "odontologoId": "uc-odontologo-uuid",
  "creadoPorId": "uc-recepcionista-uuid",
  "inicioEn": "2026-10-02T10:00:00-05:00",
  "finEn": "2026-10-02T10:45:00-05:00",
  "modalidad": "PRESENCIAL",
  "motivo": "Dolor agudo en molar inferior izquierdo",
  "consultorio": "Consultorio 2"
}
```
*(Para teleconsulta, se especifica `modalidad: "VIRTUAL"` y `enlaceTeleconsulta: "https://meet.coronyx.pe/sala-uuid"`).*
- **Respuesta Exitosa:** `201 Created`

#### 6.2 Consultar Agenda por Odontólogo y Fecha
- **Endpoint:** `GET /api/v1/appointments?odontologoId=uc-odontologo-uuid&fecha=2026-10-02`
- **Respuesta Exitosa (`200 OK`):** Lista de turnos del día ordenados cronológicamente.

#### 6.3 Actualizar Estado de la Cita
- **Endpoint:** `PUT /api/v1/appointments/{id}/status`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "estado": "EN_ATENCION",
  "estadoAsistencia": "ASISTIO"
}
```
- **Respuesta Exitosa:** `200 OK`

---

## DOMINIO 3: HISTORIA CLÍNICA Y ASISTENTE DE VOZ

### 7. Tabla: `atencion_clinica`
Representa el acto médico odontológico global vinculado a una cita o consulta.

#### 7.1 Abrir Encuentro Clínico
- **Endpoint:** `POST /api/v1/clinical-encounters`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "pacienteId": "0495cb2b-b238-431d-9757-1d44f009c90b",
  "citaId": "cita-uuid-opcional",
  "odontologoId": "uc-odontologo-uuid"
}
```
- **Respuesta Exitosa:** `201 Created` (Devuelve `estado: "BORRADOR"`).

---

### 8. Tabla: `version_atencion_clinica`
Historial cronológico inmutable de la historia clínica con soporte de Asistente de Voz IA.

#### 8.1 Registrar / Actualizar Versión (con Borrador de Voz)
- **Endpoint:** `POST /api/v1/clinical-encounters/{atencionId}/versions`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "diagnostico": "Caries profunda en pieza 36, con compromiso pulpar leve",
  "procedimientos": "Apertura cameral, limpieza con hipoclorito, colocación de MTA",
  "recetasPrescripciones": "Ibuprofeno 400mg cada 8 horas por 3 días. Amoxicilina 500mg cada 8 horas por 7 días.",
  "indicaciones": "Dieta blanda por 48 horas. Evitar masticar por lado izquierdo.",
  "evolucion": "Paciente refiere alivio tras anestesia local.",
  "textoPendienteRevision": "Transcripción de voz: Doctor observa cavidad oclusal profunda en pieza treinta y seis, procede con obturación provisoria."
}
```
- **Respuesta Exitosa:** `201 Created` (Crea una nueva versión inmutable con `numeroVersion` incremental).

#### 8.2 Firmar y Confirmar la Historia Clínica
- **Endpoint:** `PUT /api/v1/clinical-encounters/{atencionId}/versions/{versionId}/sign`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "firmadoPor": "uc-odontologo-uuid"
}
```
- **Respuesta Exitosa:** `200 OK` (Establece `estaConfirmado = true` y fecha de confirmación legal).

---

## DOMINIO 4: ODONTOGRAMA DIGITAL FDI (11 a 85)

### 9. Tabla: `version_odontograma`
Guarda el estado completo de la cavidad bucal del paciente en una versión cronológica.

#### 9.1 Crear Nueva Versión del Odontograma
- **Endpoint:** `POST /api/v1/odontograms`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "pacienteId": "0495cb2b-b238-431d-9757-1d44f009c90b"
}
```
- **Respuesta Exitosa:** `201 Created`

#### 9.2 Consultar Último Odontograma del Paciente
- **Endpoint:** `GET /api/v1/odontograms/latest?pacienteId=0495cb2b-b238-431d-9757-1d44f009c90b`
- **Respuesta Exitosa:** `200 OK` (Retorna la versión activa junto con todos sus hallazgos).

---

### 10. Tabla: `hallazgo_odontograma`
Detalle anatómico por pieza dental FDI, superficie afectada y patología.

#### 10.1 Registrar Hallazgo Dental
- **Endpoint:** `POST /api/v1/odontograms/{versionOdontogramaId}/findings`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "codigoPiezaDental": "36",
  "codigoSuperficieCara": "OCLUSAL",
  "codigoCondicion": "CARIES",
  "notaObservacion": "Caries de esmalte y dentina sin afección pulpar"
}
```
- **Caras Validadas por CHECK:** `'MESIAL'`, `'DISTAL'`, `'OCLUSAL'`, `'VESTIBULAR'`, `'LINGUAL'`, `'GENERAL'`.
- **Respuesta Exitosa:** `201 Created`

#### 10.2 Eliminar o Corregir Hallazgo
- **Endpoint:** `DELETE /api/v1/odontograms/{versionOdontogramaId}/findings/{findingId}`
- **Respuesta Exitosa:** `204 No Content`

---

## DOMINIO 5: RADIOGRAFÍAS, IA Y NOTIFICACIONES

### 11. Tabla: `archivo_adjunto`
Almacenamiento de radiografías, metadatos e inferencias de Visión Artificial (YOLOv8).

#### 11.1 Subir Radiografía (Multipart Form Data)
- **Endpoint:** `POST /api/v1/attachments/upload`
- **Headers:** `Content-Type: multipart/form-data`
- **Form Data Parameters:**
  - `file`: (Archivo binario PNG/JPG/DICOM)
  - `clinicaId`: `f3cfeb91-cccb-47da-9aee-5598ab1b99a7`
  - `pacienteId`: `0495cb2b-b238-431d-9757-1d44f009c90b`
  - `citaId`: `cita-uuid`
  - `tipoArchivo`: `RADIOGRAFIA`
  - `regionAnatomica`: `Maxilar inferior izquierdo - Zona molar (piezas 36-38)`
- **Respuesta Exitosa (`201 Created`):**
```json
{
  "id": "aa001-uuid",
  "nombreOriginal": "Rx_Periapical_Pieza36.png",
  "tipoMime": "image/png",
  "pesoBytes": 2458624,
  "claveAlmacenamiento": "clinicas/f3cf/pacientes/0495/rx-36.png",
  "iaNombreModelo": "YOLOv8-DentalCaries-v2.1",
  "iaEstadoRevision": "PENDIENTE",
  "iaHallazgosJson": {
    "detections": [
      {
        "label": "caries",
        "bbox": [120, 85, 45, 38],
        "confidence": 0.94
      }
    ]
  }
}
```

#### 11.2 Dictamen del Odontólogo sobre la IA (Human-in-the-Loop)
- **Endpoint:** `PUT /api/v1/attachments/{id}/review`
- **Cuerpo de Solicitud (JSON):**
```json
{
  "iaEstadoRevision": "ACEPTADO",
  "iaRevisadoPor": "uc-odontologo-uuid"
}
```
*(Valores permitidos: `ACEPTADO`, `RECHAZADO`, `CORREGIDO`).*
- **Respuesta Exitosa:** `200 OK`

---

### 12. Tabla: `notificacion`
Bandeja de alertas para citas, recordatorios y resultados de IA.

#### 12.1 Consultar Notificaciones del Usuario
- **Endpoint:** `GET /api/v1/notifications?usuarioId=6977fb4e-ae9f-4f58-8387-b2a26fd834c3`
- **Respuesta Exitosa (`200 OK`):**
```json
[
  {
    "id": "notif-001",
    "tipoEvento": "CITA_RECORDATORIO",
    "titulo": "Recordatorio de Turno",
    "cuerpoMensaje": "Tiene una cita programada para hoy a las 10:00 AM en Consultorio 2.",
    "estado": "ENVIADO",
    "fechaCreacion": "2026-09-30T07:00:00Z",
    "fechaLectura": null
  }
]
```

#### 12.2 Marcar Notificación como Leída
- **Endpoint:** `PUT /api/v1/notifications/{id}/read`
- **Respuesta Exitosa:** `200 OK` (Actualiza `estado: "LEIDO"` y `fechaLectura: timestamp`).

---

## CÓDIGOS DE ESTADO HTTP Y MANEJO DE ERRORES

| Código HTTP | Significado | Escenario de Uso |
|:---:|:---|:---|
| **`200 OK`** | Operación exitosa | Consultas `GET` y actualizaciones `PUT` confirmadas. |
| **`201 CREATED`** | Recurso creado | Altas de pacientes, citas, versiones o archivos (`POST`). |
| **`204 NO CONTENT`** | Eliminación exitosa | Eliminación de registros huérfanos o hallazgos. |
| **`400 BAD REQUEST`** | Error en los datos | Formato JSON incorrecto o validación fallida (`@NotBlank`). |
| **`401 UNAUTHORIZED`** | Credenciales inválidas | Clave o correo erróneo en `/api/v1/auth/login`. |
| **`404 NOT FOUND`** | Recurso no encontrado | UUID de paciente, cita o clínica inexistente en BD. |
| **`409 CONFLICT`** | Conflicto de integridad | Correo duplicado o solapamiento de horario de cita. |
