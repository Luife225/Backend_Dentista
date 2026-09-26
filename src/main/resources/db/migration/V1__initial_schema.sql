-- =========================================================================================
-- PROYECTO CORONYX - SISTEMA DE GESTION ODONTOLOGICA MULTI-CLINICA CON IA
-- FLYWAY MIGRATION V1: ESQUEMA INICIAL DE PERSISTENCIA (12 TABLAS CON RBAC)
-- Responsable: Luis Gamboa (Sprint 3 / Tarea 3.7 - Issue #92)
-- Motor: PostgreSQL 17
-- =========================================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================================
-- DOMINIO 1: SEGURIDAD, ACCESO Y MULTI-SEDE (4 TABLAS)
-- =========================================================================================

-- 1. clinica (7 cols)
CREATE TABLE clinica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(160) NOT NULL,
    razon_social VARCHAR(200),
    zona_horaria VARCHAR(64) NOT NULL DEFAULT 'America/Lima',
    estado VARCHAR(16) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'CERRADO', 'MANTENIMIENTO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 2. usuario (12 cols)
CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo VARCHAR(254) NOT NULL UNIQUE,
    clave_hash VARCHAR(255) NOT NULL,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'DESHABILITADO')),
    correo_verificado_en TIMESTAMPTZ,
    token_recuperacion_hash VARCHAR(255),
    token_recuperacion_expira_en TIMESTAMPTZ,
    token_recuperacion_usado_en TIMESTAMPTZ,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE UNIQUE INDEX idx_usuario_correo_lower ON usuario (lower(correo));
CREATE INDEX idx_usuario_recuperacion ON usuario (token_recuperacion_expira_en) WHERE token_recuperacion_hash IS NOT NULL;

-- 3. rol (7 cols)
CREATE TABLE rol (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    descripcion TEXT,
    permisos_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    activo BOOLEAN NOT NULL DEFAULT true,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 4. usuario_clinica (8 cols)
CREATE TABLE usuario_clinica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_id UUID NOT NULL REFERENCES rol(id) ON DELETE RESTRICT,
    rol_asignado_por UUID REFERENCES usuario(id) ON DELETE SET NULL,
    estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_usuario_clinica_rol UNIQUE (clinica_id, usuario_id)
);

CREATE INDEX idx_usuario_clinica_lookup ON usuario_clinica (clinica_id, usuario_id);

-- =========================================================================================
-- DOMINIO 2: PACIENTES Y CITAS (2 TABLAS)
-- =========================================================================================

-- 5. paciente (17 cols)
CREATE TABLE paciente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    medico_actualizo_id UUID REFERENCES usuario_clinica(id) ON DELETE SET NULL,
    tipo_documento VARCHAR(20) DEFAULT 'DNI' CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE')),
    numero_documento VARCHAR(30),
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    fecha_nacimiento DATE,
    telefono VARCHAR(30),
    correo VARCHAR(254),
    estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO', 'ARCHIVADO')),
    alergias TEXT,
    antecedentes_medicos TEXT,
    medicamentos TEXT,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_paciente_clinica UNIQUE (clinica_id, id)
);

CREATE INDEX idx_paciente_clinica ON paciente (clinica_id);
CREATE INDEX idx_paciente_documento ON paciente (clinica_id, tipo_documento, numero_documento);

-- 6. cita (15 cols)
CREATE TABLE cita (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    paciente_id UUID NOT NULL REFERENCES paciente(id) ON DELETE RESTRICT,
    odontologo_id UUID NOT NULL REFERENCES usuario_clinica(id) ON DELETE RESTRICT,
    creado_por_id UUID NOT NULL REFERENCES usuario_clinica(id) ON DELETE RESTRICT,
    inicio_en TIMESTAMPTZ NOT NULL,
    fin_en TIMESTAMPTZ NOT NULL,
    modalidad VARCHAR(12) NOT NULL DEFAULT 'PRESENCIAL' CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL')),
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA', 'CONFIRMADA', 'CANCELADA', 'EN_ATENCION', 'FINALIZADA')),
    estado_asistencia VARCHAR(16) CHECK (estado_asistencia IN ('ASISTIO', 'NO_SHOW')),
    motivo VARCHAR(500),
    enlace_teleconsulta VARCHAR(500),
    consultorio VARCHAR(100),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_cita_fechas CHECK (fin_en > inicio_en)
);

CREATE INDEX idx_cita_agenda ON cita (clinica_id, odontologo_id, inicio_en);
CREATE INDEX idx_cita_paciente ON cita (paciente_id, inicio_en);

-- =========================================================================================
-- DOMINIO 3: HISTORIA CLINICA Y ODONTOGRAMA (4 TABLAS)
-- =========================================================================================

-- 7. atencion_clinica (8 cols)
CREATE TABLE atencion_clinica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    paciente_id UUID NOT NULL REFERENCES paciente(id) ON DELETE RESTRICT,
    cita_id UUID UNIQUE REFERENCES cita(id) ON DELETE SET NULL,
    odontologo_id UUID NOT NULL REFERENCES usuario_clinica(id) ON DELETE RESTRICT,
    estado VARCHAR(16) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'CONFIRMADO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_confirmacion TIMESTAMPTZ
);

CREATE INDEX idx_atencion_paciente ON atencion_clinica (paciente_id);

-- 8. version_atencion_clinica (15 cols)
CREATE TABLE version_atencion_clinica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    atencion_clinica_id UUID NOT NULL REFERENCES atencion_clinica(id) ON DELETE CASCADE,
    version_anterior_id UUID REFERENCES version_atencion_clinica(id) ON DELETE SET NULL,
    firmado_por UUID REFERENCES usuario_clinica(id) ON DELETE SET NULL,
    numero_version INTEGER NOT NULL DEFAULT 1 CHECK (numero_version > 0),
    diagnostico TEXT,
    procedimientos TEXT,
    recetas_prescripciones TEXT,
    indicaciones TEXT,
    evolucion TEXT,
    texto_pendiente_revision TEXT,
    esta_confirmado BOOLEAN NOT NULL DEFAULT false,
    fecha_registro TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_confirmacion TIMESTAMPTZ
);

CREATE INDEX idx_version_atencion_lookup ON version_atencion_clinica (atencion_clinica_id, numero_version);

-- 9. version_odontograma (9 cols)
CREATE TABLE version_odontograma (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    paciente_id UUID NOT NULL REFERENCES paciente(id) ON DELETE RESTRICT,
    version_anterior_id UUID REFERENCES version_odontograma(id) ON DELETE SET NULL,
    firmado_por UUID REFERENCES usuario_clinica(id) ON DELETE SET NULL,
    numero_version INTEGER NOT NULL DEFAULT 1 CHECK (numero_version > 0),
    estado VARCHAR(16) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'CONFIRMADO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_confirmacion TIMESTAMPTZ
);

CREATE INDEX idx_odontograma_paciente ON version_odontograma (paciente_id);

-- 10. hallazgo_odontograma (7 cols)
CREATE TABLE hallazgo_odontograma (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    version_odontograma_id UUID NOT NULL REFERENCES version_odontograma(id) ON DELETE CASCADE,
    codigo_pieza_dental VARCHAR(4) NOT NULL,
    codigo_superficie_cara VARCHAR(12) NOT NULL CHECK (codigo_superficie_cara IN ('MESIAL', 'DISTAL', 'OCLUSAL', 'VESTIBULAR', 'LINGUAL', 'GENERAL')),
    codigo_condicion VARCHAR(40) NOT NULL,
    nota_observacion TEXT
);

CREATE INDEX idx_hallazgo_odontograma_pieza ON hallazgo_odontograma (version_odontograma_id, codigo_pieza_dental);

-- =========================================================================================
-- DOMINIO 4: ARCHIVOS E INTELIGENCIA ARTIFICIAL (1 TABLA)
-- =========================================================================================

-- 11. archivo_adjunto (18 cols)
CREATE TABLE archivo_adjunto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE RESTRICT,
    paciente_id UUID NOT NULL REFERENCES paciente(id) ON DELETE RESTRICT,
    subido_por_usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    cita_id UUID REFERENCES cita(id) ON DELETE SET NULL,
    ia_revisado_por UUID REFERENCES usuario_clinica(id) ON DELETE SET NULL,
    tipo_archivo VARCHAR(24) NOT NULL CHECK (tipo_archivo IN ('RADIOGRAFIA', 'FOTOGRAFIA', 'CONSENTIMIENTO', 'LABORATORIO')),
    clave_almacenamiento VARCHAR(500) NOT NULL UNIQUE,
    nombre_original VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    peso_bytes BIGINT NOT NULL CHECK (peso_bytes > 0),
    region_anatomica VARCHAR(80),
    es_visible_paciente BOOLEAN NOT NULL DEFAULT false,
    ia_nombre_modelo VARCHAR(100),
    ia_version_modelo VARCHAR(80),
    ia_hallazgos_json JSONB,
    ia_estado_revision VARCHAR(16) DEFAULT 'PENDIENTE' CHECK (ia_estado_revision IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'CORREGIDO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_archivo_paciente ON archivo_adjunto (paciente_id);
CREATE INDEX idx_archivo_ia_gin ON archivo_adjunto USING GIN (ia_hallazgos_json);

-- =========================================================================================
-- DOMINIO 5: NOTIFICACIONES TRANSACCIONALES (1 TABLA)
-- =========================================================================================

-- 12. notificacion (9 cols)
CREATE TABLE notificacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id UUID NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    usuario_destinatario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tipo_evento VARCHAR(40) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    cuerpo_mensaje VARCHAR(500) NOT NULL,
    estado VARCHAR(16) NOT NULL DEFAULT 'ENVIADO' CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'LEIDO', 'FALLIDO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_lectura TIMESTAMPTZ
);

CREATE INDEX idx_notificacion_destinatario ON notificacion (usuario_destinatario_id, estado);

-- =========================================================================================
-- SEMILLAS DE ROLES MAESTROS (RBAC)
-- =========================================================================================
INSERT INTO rol (codigo, nombre, descripcion, permisos_json) VALUES
('ADMIN_CLINICA', 'Administrador de Clínica', 'Gestión total de la sede, usuarios y auditoría', '["CLINICA_MANAGE", "USER_MANAGE", "REPORT_VIEW", "PATIENT_VIEW", "APPOINTMENT_MANAGE"]'::jsonb),
('ODONTOLOGO', 'Odontólogo Especialista', 'Atención médica, odontograma e interacciones con IA', '["PATIENT_VIEW", "APPOINTMENT_VIEW", "CLINICAL_WRITE", "ODONTOGRAM_WRITE", "AI_REVIEW"]'::jsonb),
('RECEPCIONISTA', 'Recepcionista', 'Gestión de agenda y registro de admisiones de pacientes', '["PATIENT_MANAGE", "APPOINTMENT_MANAGE"]'::jsonb),
('PACIENTE', 'Paciente', 'Acceso seguro a citas, teleconsulta e historial de recetas', '["MY_APPOINTMENTS_VIEW", "MY_RECORDS_VIEW"]'::jsonb)
ON CONFLICT (codigo) DO NOTHING;
