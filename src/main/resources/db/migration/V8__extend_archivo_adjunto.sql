-- =========================================================================================
-- V8__extend_archivo_adjunto.sql
-- F06-A: Archivos duraderos y publicación
-- Ampliación de tipos de archivo en restricción CHECK e índices para optimización de consultas
-- =========================================================================================

-- 1. Ampliar catálogo de tipos de archivo permitidos
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_tipo_archivo_check;

ALTER TABLE archivo_adjunto ADD CONSTRAINT archivo_adjunto_tipo_archivo_check 
    CHECK (tipo_archivo IN ('RADIOGRAFIA', 'FOTOGRAFIA', 'CONSENTIMIENTO', 'LABORATORIO', 'DOCUMENTO', 'INFORME', 'RECETA', 'OTRO'));

-- 2. Índices de aceleración para consultas multi-tenant y visibilidad de paciente
CREATE INDEX IF NOT EXISTS idx_archivo_clinica ON archivo_adjunto (clinica_id);
CREATE INDEX IF NOT EXISTS idx_archivo_visible ON archivo_adjunto (paciente_id, es_visible_paciente);
