-- =========================================================================================
-- PROYECTO CORONYX - SISTEMA DE GESTION ODONTOLOGICA MULTI-CLINICA CON IA
-- FLYWAY MIGRATION V7: CAMPOS MANUALES DE ATENCION CLINICA Y CONTROL DE VERSIONES
-- Requisito F05-A: Ficha clínica manual, versionamiento inmutable e idempotente
-- =========================================================================================

-- 1. Agregar campos manuales de atención odontológica en version_atencion_clinica
ALTER TABLE version_atencion_clinica 
    ADD COLUMN IF NOT EXISTS motivo_consulta TEXT,
    ADD COLUMN IF NOT EXISTS examen_clinico TEXT,
    ADD COLUMN IF NOT EXISTS plan_tratamiento TEXT,
    ADD COLUMN IF NOT EXISTS signos_vitales JSONB,
    ADD COLUMN IF NOT EXISTS proxima_cita_sugerida VARCHAR(50);

-- 2. Asegurar restricción de unicidad estricta para versión por atención clínica
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_version_atencion_numero'
    ) THEN
        ALTER TABLE version_atencion_clinica 
            ADD CONSTRAINT uq_version_atencion_numero UNIQUE (atencion_clinica_id, numero_version);
    END IF;
END $$;

-- 3. Índices de optimización para recuperación de atenciones por paciente y clínica
CREATE INDEX IF NOT EXISTS idx_atencion_clinica_paciente ON atencion_clinica (paciente_id, clinica_id);
CREATE INDEX IF NOT EXISTS idx_atencion_clinica_odontologo ON atencion_clinica (odontologo_id);
CREATE INDEX IF NOT EXISTS idx_version_atencion_anterior ON version_atencion_clinica (version_anterior_id);
