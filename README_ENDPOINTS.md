# 🦷 GUÍA MAESTRA DE APIS REST Y ROADMAP DE CONSTRUCCIÓN - CORONYX BACKEND
> **Proyecto:** CORONYX - Plataforma Odontológica Inteligente con Asistencia de Voz e IA  
> **Versión Base:** `v1` | **Base URL:** `http://localhost:8081/api/v1`  
> **Stack Técnico:** Spring Boot 4, Java 21, PostgreSQL 17, Flyway, Maven, React/Vite (Front-End)  
> **Documento Detallado de Esquema:** [docs/ENDPOINTS_API.md](file:///d:/ProyectoCoronyx/Backend_Dentista/docs/ENDPOINTS_API.md)

---

## 🚦 1. Tablero de Estado de Implementación (Semáforo Full Stack)

| Prioridad | Dominio Funcional | Endpoints Principales | Tablas PostgreSQL Relacionadas | Estado Actual | Pantalla Front-End Asociada |
| :---: | :--- | :--- | :--- | :---: | :--- |
| **P0** | **Autenticación & Usuarios** | `/auth/login`, `/auth/users` | `usuario`, `rol`, `usuario_clinica` | ✅ **Operativo** | [`Login.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Login.tsx) |
| **P0** | **Admisión de Pacientes** | `/patients` (GET, POST) | `paciente`, `clinica` | ✅ **Operativo** | [`Patients.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Patients.tsx) |
| **P1** | **Agenda y Citas Médicas** | `/appointments` (CRUD + Status) | `cita`, `paciente`, `usuario_clinica` | 🟡 **A Construir (Inmediato)** | [`Agenda.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Agenda.tsx) / [`AgendaUpdated.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/AgendaUpdated.tsx) |
| **P2** | **Perfil y Ficha de Paciente** | `/patients/{id}` (GET, PUT, PATCH) | `paciente` | 🟡 **A Construir (Inmediato)** | [`PacientePerfil.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/PacientePerfil.tsx) |
| **P3** | **Atención Clínica y Voz** | `/clinical-encounters` (Versiones) | `atencion_clinica`, `version_atencion_clinica` | ⏳ **Siguiente Fase** | [`Consultorio.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Consultorio.tsx), [`HistoriaClinica.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/HistoriaClinica.tsx) |
| **P4** | **Odontograma Digital** | `/odontograms` (FDI 11-85) | `version_odontograma`, `hallazgo_odontograma` | ⏳ **Siguiente Fase** | Odontograma en [`Consultorio.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Consultorio.tsx) |
| **P5** | **Radiografías e IA YOLOv8** | `/attachments/upload`, `/ai/analyze` | `archivo_adjunto` (JSONB) | ⏳ **Fase de Innovación** | [`Radiografias.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Radiografias.tsx) |
| **P6** | **Notificaciones y Alertas** | `/notifications` | `notificacion` | ⏳ **Fase Final** | Header / Campana Notificaciones |

---

## 🎯 2. Roadmap Detallado de APIs a Construir (Por Orden de Prioridad)

---

### 🥇 PRIORIDAD 1: MÓDULO DE CITAS Y AGENDA MÉDICA
El flujo de toda clínica odontológica nace con la programación de citas.  
En el backend ya se tiene creada la entidad [`Appointment.java`](file:///d:/ProyectoCoronyx/Backend_Dentista/src/main/java/pe/edu/utp/coronyx/backend/model/Appointment.java), por lo que crear sus capas desbloquea de inmediato la agenda interactiva del frontend.

#### 2.1.1 `POST /api/v1/appointments` (Agendar Cita)
- **Objetivo:** Reservar un turno de atención presencial o virtual.
- **Validaciones de Negocio:**
  - `finEn` debe ser estrictamente posterior a `inicioEn` (`CHECK (fin_en > inicio_en)`).
  - El odontólogo no debe tener otra cita en estado `PROGRAMADA`, `CONFIRMADA` o `EN_ATENCION` que se solape en ese rango.
  - La modalidad solo acepta: `PRESENCIAL` o `VIRTUAL`.
- **Campos guardados en tabla `cita`:**
  - `id`: UUID (Generado automáticamente).
  - `clinica_id`: UUID de la sede.
  - `paciente_id`: UUID del paciente.
  - `odontologo_id`: UUID de `usuario_clinica` del doctor asignado.
  - `creado_por_id`: UUID de `usuario_clinica` del recepcionista/admin que agenda.
  - `inicio_en`: TIMESTAMPTZ (ISO-8601, ej. `2026-10-05T09:00:00-05:00`).
  - `fin_en`: TIMESTAMPTZ (ISO-8601, ej. `2026-10-05T09:45:00-05:00`).
  - `modalidad`: VARCHAR(12) -> `'PRESENCIAL'` | `'VIRTUAL'`.
  - `estado`: VARCHAR(20) -> `'PROGRAMADA'`.
  - `motivo`: VARCHAR(500) -> Motivo de consulta.
  - `consultorio`: VARCHAR(100) -> Sillón o sala (ej. "Sillón 1").
  - `enlace_teleconsulta`: VARCHAR(500) -> URL en caso sea `VIRTUAL`.

**Payload Request:**
```json
{
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "pacienteId": "0495cb2b-b238-431d-9757-1d44f009c90b",
  "odontologoId": "a9b8c7d6-e5f4-4a3b-2c1d-0e9f8a7b6c5d",
  "creadoPorId": "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e",
  "inicioEn": "2026-10-05T09:00:00-05:00",
  "finEn": "2026-10-05T09:45:00-05:00",
  "modalidad": "PRESENCIAL",
  "motivo": "Curación de caries en molar inferior y evaluación general",
  "consultorio": "Consultorio 1"
}
```

**Payload Response (`201 Created`):**
```json
{
  "id": "c1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
  "paciente": {
    "id": "0495cb2b-b238-431d-9757-1d44f009c90b",
    "nombreCompleto": "Luis Francisco Gamboa",
    "telefono": "+51 989992109"
  },
  "odontologo": {
    "id": "a9b8c7d6-e5f4-4a3b-2c1d-0e9f8a7b6c5d",
    "nombreCompleto": "Dr. Andrés Herrera"
  },
  "inicioEn": "2026-10-05T09:00:00-05:00",
  "finEn": "2026-10-05T09:45:00-05:00",
  "modalidad": "PRESENCIAL",
  "estado": "PROGRAMADA",
  "motivo": "Curación de caries en molar inferior y evaluación general",
  "consultorio": "Consultorio 1",
  "fechaCreacion": "2026-10-01T15:00:00-05:00"
}
```

---

#### 2.1.2 `GET /api/v1/appointments` (Listar Citas con Filtros de Agenda)
- **Query Params:**
  - `fecha`: `YYYY-MM-DD` (Filtra citas de ese día específico).
  - `odontologoId`: UUID (Filtra agenda del doctor seleccionado).
  - `pacienteId`: UUID (Filtra historial de citas del paciente).
  - `estado`: `PROGRAMADA`, `CONFIRMADA`, `EN_ATENCION`, `FINALIZADA`, `CANCELADA`.
- **Respuesta (`200 OK`):** Lista ordenada por `inicio_en ASC`.

---

#### 2.1.3 `PATCH /api/v1/appointments/{id}/status` (Transición de Estados de Cita)
- **Objetivo:** Permite al recepcionista o al dentista avanzar el estado del turno.
- **Flujo de Estados:**
  ```text
  PROGRAMADA ──> CONFIRMADA ──> EN_ATENCION ──> FINALIZADA
       │              │              │
       └─── CANCELADA └── CANCELADA  └── NO_SHOW (No asistió)
  ```
- **Campos modificados:** `estado`, `estado_asistencia`, `fecha_actualizacion`.

**Payload Request:**
```json
{
  "estado": "EN_ATENCION",
  "estadoAsistencia": "ASISTIO"
}
```

---

### 🥈 PRIORIDAD 2: DETALLE Y ACTUALIZACIÓN DE PACIENTES
Actualmente `GET /api/v1/patients` y `POST /api/v1/patients` funcionan. Se requiere completar el CRUD para alimentar [`PacientePerfil.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/PacientePerfil.tsx).

#### 2.2.1 `GET /api/v1/patients/{id}` (Ficha Completa)
- **Respuesta (`200 OK`):** Retorna datos demográficos, alergias, antecedentes médicos y medicamentos del paciente.

#### 2.2.2 `PUT /api/v1/patients/{id}` (Actualización de Ficha)
- **Campos modificados:** `telefono`, `correo`, `alergias`, `antecedentesMedicos`, `medicamentos`, `medicoActualizoId`.

**Payload Request:**
```json
{
  "telefono": "+51 989992100",
  "correo": "luis.actualizado@correo.pe",
  "alergias": "Alergia confirmada a la Penicilina y AINES",
  "antecedentesMedicos": "Hipertensión controlada con Enalapril",
  "medicamentos": "Enalapril 10mg diario",
  "medicoActualizoId": "a9b8c7d6-e5f4-4a3b-2c1d-0e9f8a7b6c5d"
}
```

#### 2.2.3 `PATCH /api/v1/patients/{id}/archive` (Baja Lógica)
- Establece `estado = 'ARCHIVADO'` sin eliminar físicamente el historial clínico legal.

---

### 🥉 PRIORIDAD 3: ATENCIÓN CLÍNICA Y ASISTENTE DE VOZ
Alimenta la pantalla de atención médica en el consultorio ([`Consultorio.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Consultorio.tsx)).  
La arquitectura utiliza el patrón de **Versiones Inmutables**: cada guardado genera una versión auditada sin sobreescribir la anterior.

#### 2.3.1 `POST /api/v1/clinical-encounters` (Apertura de Consulta)
- **Crea registro en:** `atencion_clinica`.
- **Campos:** `clinica_id`, `paciente_id`, `cita_id` (opcional/asociado), `odontologo_id`.
- **Estado Inicial:** `'BORRADOR'`.

#### 2.3.2 `POST /api/v1/clinical-encounters/{id}/versions` (Guardar Evolución / Dictado de Voz)
- **Crea registro en:** `version_atencion_clinica`.
- **Campos guardados:**
  - `atencion_clinica_id`: UUID.
  - `numero_version`: Autoincremental por atención (1, 2, 3...).
  - `diagnostico`: Diagnóstico dental (ej. "K02.1 Caries dentinaria pieza 46").
  - `procedimientos`: Procedimientos aplicados (ej. "Profilaxis + Obturación de resina").
  - `recetas_prescripciones`: Medicamentos recetados (dosis, frecuencia).
  - `indicaciones`: Cuidados posteriores al paciente.
  - `evolucion`: Nota clínica sobre la respuesta del paciente.
  - `texto_pendiente_revision`: **Aquí se guarda el texto capturado por el Asistente de Voz** mientras el doctor tiene las manos ocupadas con el instrumental.

**Payload Request:**
```json
{
  "diagnostico": "Caries profunda en cara oclusal de pieza 46 sin afectación pulpar",
  "procedimientos": "Anestesia infiltrativa, remoción de caries con pieza de alta velocidad, grabado ácido, adhesivo y resina fotocurable 3M",
  "recetasPrescripciones": "Ketorolaco 10mg sublingual solo en caso de molestia aguda",
  "indicaciones": "No consumir alimentos pigmentados (café, té) las primeras 24 horas",
  "evolucion": "Procedimiento finalizado con éxito sin sintomatología dolorosa",
  "textoPendienteRevision": "Voz dictada: 'Paciente tolera bien la anestesia, se realiza cavidad oclusal conservadora pieza cuatro seis'."
}
```

#### 2.3.3 `PUT /api/v1/clinical-encounters/{id}/confirm` (Firma y Cierre Médico)
- Pasa `atencion_clinica.estado` a `'CONFIRMADO'`, marca la última versión como `esta_confirmado = true`, asigna `firmado_por` con el odontólogo y sella con `fecha_confirmacion = NOW()`.

---

### 🏅 PRIORIDAD 4: ODONTOGRAMA DIGITAL INTERACTIVO
Permite mapear las 32 piezas dentales adultas y las 20 deciduas según la nomenclatura FDI internacional (11 a 85).

#### 2.4.1 `GET /api/v1/odontograms/patient/{patientId}/latest` (Odontograma Vigente)
- Trae la última versión confirmada del odontograma del paciente junto con su lista de hallazgos para dibujarlos en el lienzo interactivo de React.

#### 2.4.2 `POST /api/v1/odontograms/{versionId}/findings` (Registrar Hallazgo)
- **Tabla:** `hallazgo_odontograma`.
- **Restricciones CHECK:**
  - `codigo_superficie_cara`: Obligatoriamente uno de:
    `'MESIAL'`, `'DISTAL'`, `'OCLUSAL'`, `'VESTIBULAR'`, `'LINGUAL'`, `'GENERAL'`.
- **Campos:**
  - `codigo_pieza_dental`: Ej. `"18"`, `"21"`, `"36"`, `"48"`.
  - `codigo_condicion`: Ej. `'CARIES'`, `'RESTAURACION_RESINA'`, `'AMALGAMA'`, `'CORONA'`, `'ENDODONCIA'`, `'PIEZA_AUSENTE'`.
  - `nota_observacion`: Comentario adicional.

---

### 🔬 PRIORIDAD 5: RADIOGRAFÍAS Y DETECCIÓN CON VISIÓN ARTIFICIAL (YOLOv8)
Alimenta [`Radiografias.tsx`](file:///d:/ProyectoCoronyx/Frontend_Dentista/src/pages/Radiografias.tsx) y es el factor clave de innovación del sistema.

#### 2.5.1 `POST /api/v1/attachments/upload` (Carga de Radiografía)
- **Tipo:** `multipart/form-data`.
- **Parámetros:** `file` (PNG, JPG, DICOM), `pacienteId`, `clinicaId`, `tipoArchivo: "RADIOGRAFIA"`, `regionAnatomica: "Molar inferior derecho"`.
- **Guarda en `archivo_adjunto`:** Nombre original, peso en bytes, MIME type, clave de almacenamiento y deja `ia_estado_revision: 'PENDIENTE'`.

#### 2.5.2 `POST /api/v1/ai/analyze-xray/{attachmentId}` (Inferencia con Red Neuronal)
- Dispara el modelo de Visión Artificial (YOLOv8 entrenado en radiografías periapicales/panorámicas).
- **Guarda en columna JSONB (`ia_hallazgos_json`):**
```json
{
  "iaNombreModelo": "Coronyx-DentalVision-YOLOv8",
  "iaVersionModelo": "2.4.0",
  "hallazgos": [
    {
      "clase": "CARIES_INTERPROXIMAL",
      "piezaEstimada": "36",
      "confianza": 0.93,
      "bbox": [140, 210, 48, 52]
    },
    {
      "clase": "PERDIDA_OSEA",
      "confianza": 0.87,
      "bbox": [110, 320, 115, 30]
    }
  ]
}
```

#### 2.5.3 `PATCH /api/v1/attachments/{attachmentId}/review` (Revisión Humana / Human-in-the-Loop)
- El odontólogo valida o corrige los hallazgos automáticos de la IA.
- Valores permitidos por CHECK: `'ACEPTADO'`, `'RECHAZADO'`, `'CORREGIDO'`.

---

## 🗄️ 3. Resumen de Tipos de Datos y Restricciones PostgreSQL 17

| Tabla | Columna Clave | Tipo de Dato | Restricción / Validaciones Críticas |
| :--- | :--- | :--- | :--- |
| **`cita`** | `inicio_en`, `fin_en` | `TIMESTAMPTZ` | `CHECK (fin_en > inicio_en)` |
| **`cita`** | `modalidad` | `VARCHAR(12)` | `CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL'))` |
| **`cita`** | `estado` | `VARCHAR(20)` | `'PROGRAMADA'`, `'CONFIRMADA'`, `'CANCELADA'`, `'EN_ATENCION'`, `'FINALIZADA'` |
| **`atencion_clinica`** | `estado` | `VARCHAR(16)` | `'BORRADOR'`, `'CONFIRMADO'` |
| **`hallazgo_odontograma`**| `codigo_superficie_cara` | `VARCHAR(12)` | `'MESIAL'`, `'DISTAL'`, `'OCLUSAL'`, `'VESTIBULAR'`, `'LINGUAL'`, `'GENERAL'` |
| **`archivo_adjunto`** | `ia_hallazgos_json` | `JSONB` | Indexado con **GIN** para consultas semánticas rápidas |
| **`archivo_adjunto`** | `ia_estado_revision` | `VARCHAR(16)` | `'PENDIENTE'`, `'ACEPTADO'`, `'RECHAZADO'`, `'CORREGIDO'` |

---

## 💻 4. Guía Rápida de Pruebas con cURL

### 1. Agendar Cita (Prioridad 1):
```bash
curl -X POST http://localhost:8081/api/v1/appointments \
  -H "Content-Type: application/json" \
  -d '{
    "clinicaId": "f3cfeb91-cccb-47da-9aee-5598ab1b99a7",
    "pacienteId": "0495cb2b-b238-431d-9757-1d44f009c90b",
    "odontologoId": "6977fb4e-ae9f-4f58-8387-b2a26fd834c3",
    "creadoPorId": "6977fb4e-ae9f-4f58-8387-b2a26fd834c3",
    "inicioEn": "2026-10-02T10:00:00-05:00",
    "finEn": "2026-10-02T10:45:00-05:00",
    "modalidad": "PRESENCIAL",
    "motivo": "Consulta diagnóstica inicial",
    "consultorio": "Consultorio 1"
  }'
```

### 2. Cambiar Estado a 'EN_ATENCION':
```bash
curl -X PATCH http://localhost:8081/api/v1/appointments/REEMPLAZAR_CITA_UUID/status \
  -H "Content-Type: application/json" \
  -d '{
    "estado": "EN_ATENCION",
    "estadoAsistencia": "ASISTIO"
  }'
```
