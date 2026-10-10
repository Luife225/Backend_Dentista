-- =========================================================================================
-- FLYWAY MIGRATION V9: CAMPOS DE CLINICA, DISPONIBILIDAD Y EXPEDIENTE
-- Soporte para:
-- 1. Campos editables de clínica (V09): RUC, teléfono, correo, ciudad, dirección
-- 2. Horarios de atención y ausencias (A09 / R05)
-- 3. Dirección residencial en expediente de paciente (V14)
-- =========================================================================================

-- 1. CAMPOS EDITABLES DE CLINICA
ALTER TABLE clinica
    ADD COLUMN IF NOT EXISTS ruc VARCHAR(20),
    ADD COLUMN IF NOT EXISTS telefono VARCHAR(30),
    ADD COLUMN IF NOT EXISTS correo VARCHAR(254),
    ADD COLUMN IF NOT EXISTS ciudad VARCHAR(100),
    ADD COLUMN IF NOT EXISTS direccion VARCHAR(255);

-- Actualizar clínica inicial (Sede Central) con datos consistentes
UPDATE clinica
SET ruc = COALESCE(ruc, '20601234567'),
    telefono = COALESCE(telefono, '+51 1 456 7890'),
    correo = COALESCE(correo, 'contacto@coronyx.pe'),
    ciudad = COALESCE(ciudad, 'Lima, Perú'),
    direccion = COALESCE(direccion, 'Av. Javier Prado Este 2465, San Borja')
WHERE id = 1;

-- 2. DIRECCION EN EXPEDIENTE DE PACIENTE
ALTER TABLE paciente
    ADD COLUMN IF NOT EXISTS direccion VARCHAR(255);

-- 3. TABLA DE HORARIOS DE ATENCION (DISPONIBILIDAD DE CLINICA Y ODONTOLOGOS)
CREATE TABLE IF NOT EXISTS horario_atencion (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    odontologo_id BIGINT REFERENCES usuario_clinica(id) ON DELETE CASCADE,
    dia_semana INTEGER NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), -- 1: Lunes .. 7: Domingo
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT true,
    duracion_turno_minutos INTEGER NOT NULL DEFAULT 30,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_horario_horas CHECK (hora_cierre > hora_apertura)
);

CREATE INDEX IF NOT EXISTS idx_horario_clinica_dia ON horario_atencion (clinica_id, dia_semana);
CREATE INDEX IF NOT EXISTS idx_horario_odonto_dia ON horario_atencion (odontologo_id, dia_semana);
CREATE UNIQUE INDEX IF NOT EXISTS uq_horario_clinica_dia ON horario_atencion (clinica_id, dia_semana) WHERE odontologo_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_horario_doctor_dia ON horario_atencion (odontologo_id, dia_semana) WHERE odontologo_id IS NOT NULL;

-- Insertar horario base para la Sede Central (Lunes a Viernes 08:00 - 18:00, Sábados 08:00 - 13:00)
INSERT INTO horario_atencion (clinica_id, odontologo_id, dia_semana, hora_apertura, hora_cierre, activo, duracion_turno_minutos)
VALUES
(1, NULL, 1, '08:00', '18:00', true, 30),
(1, NULL, 2, '08:00', '18:00', true, 30),
(1, NULL, 3, '08:00', '18:00', true, 30),
(1, NULL, 4, '08:00', '18:00', true, 30),
(1, NULL, 5, '08:00', '18:00', true, 30),
(1, NULL, 6, '08:00', '13:00', true, 30)
ON CONFLICT DO NOTHING;

-- 4. TABLA DE AUSENCIAS Y BLOQUEOS DE AGENDA PARA ODONTOLOGOS
CREATE TABLE IF NOT EXISTS ausencia_odontologo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    odontologo_id BIGINT NOT NULL REFERENCES usuario_clinica(id) ON DELETE CASCADE,
    inicio_en TIMESTAMPTZ NOT NULL,
    fin_en TIMESTAMPTZ NOT NULL,
    motivo VARCHAR(255),
    tipo VARCHAR(40) NOT NULL DEFAULT 'VACACIONES' CHECK (tipo IN ('VACACIONES', 'PERMISO', 'CAPACITACION', 'LICENCIA_MEDICA', 'OTRO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_ausencia_fechas CHECK (fin_en > inicio_en)
);

CREATE INDEX IF NOT EXISTS idx_ausencia_odonto_fechas ON ausencia_odontologo (odontologo_id, inicio_en, fin_en);
