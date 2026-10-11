-- =========================================================================================
-- PROYECTO CORONYX - SISTEMA DE GESTION ODONTOLOGICA MULTI-CLINICA CON IA
-- FLYWAY MIGRATION V6: SOLICITUDES DE REPROGRAMACION Y EXTENSION DE ESTADOS DE AGENDA
-- Requisito F04-A: Persistencia de solicitudes de reprogramación y matriz de estados
-- =========================================================================================

-- 1. Ampliar restricción de estados en tabla cita para soportar matriz completa
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_estado_check;
ALTER TABLE cita ADD CONSTRAINT cita_estado_check 
    CHECK (estado IN ('PROGRAMADA', 'CONFIRMADA', 'CANCELADA', 'EN_ATENCION', 'FINALIZADA', 'REPROGRAMADA', 'NO_ASISTIO'));

-- 2. Asegurar restricción de estado de asistencia en tabla cita
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_estado_asistencia_check;
ALTER TABLE cita ADD CONSTRAINT cita_estado_asistencia_check 
    CHECK (estado_asistencia IS NULL OR estado_asistencia IN ('ASISTIO', 'NO_SHOW'));

-- 3. Crear tabla solicitud_reprogramacion
CREATE TABLE IF NOT EXISTS solicitud_reprogramacion (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cita_id BIGINT NOT NULL,
    solicitante_usuario_id BIGINT NOT NULL,
    nueva_fecha_hora_inicio TIMESTAMPTZ NOT NULL,
    nueva_fecha_hora_fin TIMESTAMPTZ NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA')),
    resolucion_usuario_id BIGINT,
    resolucion_motivo VARCHAR(500),
    fecha_solicitud TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_resolucion TIMESTAMPTZ,
    CONSTRAINT ck_solicitud_fechas CHECK (nueva_fecha_hora_fin > nueva_fecha_hora_inicio),
    CONSTRAINT fk_solicitud_cita FOREIGN KEY (cita_id) REFERENCES cita(id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitud_solicitante FOREIGN KEY (solicitante_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_resolutor FOREIGN KEY (resolucion_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

-- 4. Índices para consultas eficientes de solicitudes por cita, solicitante y estado
CREATE INDEX IF NOT EXISTS idx_solicitud_reprog_cita ON solicitud_reprogramacion (cita_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_reprog_solicitante ON solicitud_reprogramacion (solicitante_usuario_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_reprog_estado ON solicitud_reprogramacion (estado);
