-- =========================================================================================
-- FLYWAY MIGRATION V3: CAMPOS EXTENDIDOS DE PACIENTE Y SOPORTE DE DOCUMENTO INTERNACIONAL (CC)
-- =========================================================================================

-- 1. Agregar columnas para ciudad, tipo de sangre y seguro de salud
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS ciudad VARCHAR(100);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS tipo_sangre VARCHAR(20) DEFAULT 'POR DETERMINAR';
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS seguro VARCHAR(80) DEFAULT 'PARTICULAR';

-- 2. Actualizar la restricción de tipo_documento para soportar 'CC' (Cédula de Ciudadanía)
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_tipo_documento_check;
ALTER TABLE paciente ADD CONSTRAINT paciente_tipo_documento_check 
    CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE', 'CC'));
