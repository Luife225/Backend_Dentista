-- =========================================================================================
-- PROYECTO CORONYX - SISTEMA DE GESTION ODONTOLOGICA MULTI-CLINICA
-- FLYWAY MIGRATION V4: MIGRACION INTEGRAL DE UUID A BIGINT IDENTITY
-- Motor: PostgreSQL 17 / 18
-- Responsable: Desarrollador A (F01-A)
-- =========================================================================================

-- 1. TABLAS TEMPORALES DE CORRESPONDENCIA UUID -> BIGINT (12 TABLAS)
-- Genera enteros secuenciales deterministas preservando el orden de creación.

CREATE TEMP TABLE _map_clinica (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_clinica (old_id) SELECT id FROM clinica ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_usuario (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_usuario (old_id) SELECT id FROM usuario ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_rol (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_rol (old_id) SELECT id FROM rol ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_usuario_clinica (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_usuario_clinica (old_id) SELECT id FROM usuario_clinica ORDER BY fecha_asignacion ASC, id ASC;

CREATE TEMP TABLE _map_paciente (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_paciente (old_id) SELECT id FROM paciente ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_cita (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_cita (old_id) SELECT id FROM cita ORDER BY inicio_en ASC, id ASC;

CREATE TEMP TABLE _map_atencion_clinica (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_atencion_clinica (old_id) SELECT id FROM atencion_clinica ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_version_atencion_clinica (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_version_atencion_clinica (old_id) SELECT id FROM version_atencion_clinica ORDER BY fecha_registro ASC, id ASC;

CREATE TEMP TABLE _map_version_odontograma (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_version_odontograma (old_id) SELECT id FROM version_odontograma ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_hallazgo_odontograma (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_hallazgo_odontograma (old_id) SELECT id FROM hallazgo_odontograma ORDER BY id ASC;

CREATE TEMP TABLE _map_archivo_adjunto (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_archivo_adjunto (old_id) SELECT id FROM archivo_adjunto ORDER BY fecha_creacion ASC, id ASC;

CREATE TEMP TABLE _map_notificacion (old_id UUID PRIMARY KEY, new_id BIGINT GENERATED ALWAYS AS IDENTITY);
INSERT INTO _map_notificacion (old_id) SELECT id FROM notificacion ORDER BY fecha_creacion ASC, id ASC;

-- 2. AGREGAR COLUMNAS BIGINT TEMPORALES A LAS 12 TABLAS
ALTER TABLE clinica ADD COLUMN new_id BIGINT;

ALTER TABLE usuario ADD COLUMN new_id BIGINT;

ALTER TABLE rol ADD COLUMN new_id BIGINT;

ALTER TABLE usuario_clinica
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_usuario_id BIGINT,
    ADD COLUMN new_rol_id BIGINT,
    ADD COLUMN new_rol_asignado_por BIGINT;

ALTER TABLE paciente
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_usuario_id BIGINT,
    ADD COLUMN new_medico_actualizo_id BIGINT;

ALTER TABLE cita
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_paciente_id BIGINT,
    ADD COLUMN new_odontologo_id BIGINT,
    ADD COLUMN new_creado_por_id BIGINT;

ALTER TABLE atencion_clinica
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_paciente_id BIGINT,
    ADD COLUMN new_cita_id BIGINT,
    ADD COLUMN new_odontologo_id BIGINT;

ALTER TABLE version_atencion_clinica
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_atencion_clinica_id BIGINT,
    ADD COLUMN new_version_anterior_id BIGINT,
    ADD COLUMN new_firmado_por BIGINT;

ALTER TABLE version_odontograma
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_paciente_id BIGINT,
    ADD COLUMN new_version_anterior_id BIGINT,
    ADD COLUMN new_firmado_por BIGINT;

ALTER TABLE hallazgo_odontograma
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_version_odontograma_id BIGINT;

ALTER TABLE archivo_adjunto
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_paciente_id BIGINT,
    ADD COLUMN new_subido_por_usuario_id BIGINT,
    ADD COLUMN new_cita_id BIGINT,
    ADD COLUMN new_ia_revisado_por BIGINT;

ALTER TABLE notificacion
    ADD COLUMN new_id BIGINT,
    ADD COLUMN new_clinica_id BIGINT,
    ADD COLUMN new_usuario_destinatario_id BIGINT;

-- 3. POBLAR NUEVAS COLUMNAS CON LOS MAPEOS CORRESPONDIENTES (CORRELATED SUBQUERIES SEGURAS PARA NULLS)
UPDATE clinica t SET new_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.id);
UPDATE usuario t SET new_id = (SELECT new_id FROM _map_usuario WHERE old_id = t.id);
UPDATE rol t SET new_id = (SELECT new_id FROM _map_rol WHERE old_id = t.id);

UPDATE usuario_clinica t
SET new_id = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_usuario_id = (SELECT new_id FROM _map_usuario WHERE old_id = t.usuario_id),
    new_rol_id = (SELECT new_id FROM _map_rol WHERE old_id = t.rol_id),
    new_rol_asignado_por = (SELECT new_id FROM _map_usuario WHERE old_id = t.rol_asignado_por);

UPDATE paciente t
SET new_id = (SELECT new_id FROM _map_paciente WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_usuario_id = (SELECT new_id FROM _map_usuario WHERE old_id = t.usuario_id),
    new_medico_actualizo_id = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.medico_actualizo_id);

UPDATE cita t
SET new_id = (SELECT new_id FROM _map_cita WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_paciente_id = (SELECT new_id FROM _map_paciente WHERE old_id = t.paciente_id),
    new_odontologo_id = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.odontologo_id),
    new_creado_por_id = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.creado_por_id);

UPDATE atencion_clinica t
SET new_id = (SELECT new_id FROM _map_atencion_clinica WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_paciente_id = (SELECT new_id FROM _map_paciente WHERE old_id = t.paciente_id),
    new_cita_id = (SELECT new_id FROM _map_cita WHERE old_id = t.cita_id),
    new_odontologo_id = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.odontologo_id);

UPDATE version_atencion_clinica t
SET new_id = (SELECT new_id FROM _map_version_atencion_clinica WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_atencion_clinica_id = (SELECT new_id FROM _map_atencion_clinica WHERE old_id = t.atencion_clinica_id),
    new_version_anterior_id = (SELECT new_id FROM _map_version_atencion_clinica WHERE old_id = t.version_anterior_id),
    new_firmado_por = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.firmado_por);

UPDATE version_odontograma t
SET new_id = (SELECT new_id FROM _map_version_odontograma WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_paciente_id = (SELECT new_id FROM _map_paciente WHERE old_id = t.paciente_id),
    new_version_anterior_id = (SELECT new_id FROM _map_version_odontograma WHERE old_id = t.version_anterior_id),
    new_firmado_por = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.firmado_por);

UPDATE hallazgo_odontograma t
SET new_id = (SELECT new_id FROM _map_hallazgo_odontograma WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_version_odontograma_id = (SELECT new_id FROM _map_version_odontograma WHERE old_id = t.version_odontograma_id);

UPDATE archivo_adjunto t
SET new_id = (SELECT new_id FROM _map_archivo_adjunto WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_paciente_id = (SELECT new_id FROM _map_paciente WHERE old_id = t.paciente_id),
    new_subido_por_usuario_id = (SELECT new_id FROM _map_usuario WHERE old_id = t.subido_por_usuario_id),
    new_cita_id = (SELECT new_id FROM _map_cita WHERE old_id = t.cita_id),
    new_ia_revisado_por = (SELECT new_id FROM _map_usuario_clinica WHERE old_id = t.ia_revisado_por);

UPDATE notificacion t
SET new_id = (SELECT new_id FROM _map_notificacion WHERE old_id = t.id),
    new_clinica_id = (SELECT new_id FROM _map_clinica WHERE old_id = t.clinica_id),
    new_usuario_destinatario_id = (SELECT new_id FROM _map_usuario WHERE old_id = t.usuario_destinatario_id);

-- 4. ELIMINAR LAS 32 FOREIGN KEYS EXISTENTES
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_clinica_id_fkey;
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_usuario_destinatario_id_fkey;

ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_clinica_id_fkey;
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_paciente_id_fkey;
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_subido_por_usuario_id_fkey;
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_cita_id_fkey;
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_ia_revisado_por_fkey;

ALTER TABLE hallazgo_odontograma DROP CONSTRAINT IF EXISTS hallazgo_odontograma_clinica_id_fkey;
ALTER TABLE hallazgo_odontograma DROP CONSTRAINT IF EXISTS hallazgo_odontograma_version_odontograma_id_fkey;

ALTER TABLE version_odontograma DROP CONSTRAINT IF EXISTS version_odontograma_clinica_id_fkey;
ALTER TABLE version_odontograma DROP CONSTRAINT IF EXISTS version_odontograma_paciente_id_fkey;
ALTER TABLE version_odontograma DROP CONSTRAINT IF EXISTS version_odontograma_version_anterior_id_fkey;
ALTER TABLE version_odontograma DROP CONSTRAINT IF EXISTS version_odontograma_firmado_por_fkey;

ALTER TABLE version_atencion_clinica DROP CONSTRAINT IF EXISTS version_atencion_clinica_clinica_id_fkey;
ALTER TABLE version_atencion_clinica DROP CONSTRAINT IF EXISTS version_atencion_clinica_atencion_clinica_id_fkey;
ALTER TABLE version_atencion_clinica DROP CONSTRAINT IF EXISTS version_atencion_clinica_version_anterior_id_fkey;
ALTER TABLE version_atencion_clinica DROP CONSTRAINT IF EXISTS version_atencion_clinica_firmado_por_fkey;

ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_clinica_id_fkey;
ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_paciente_id_fkey;
ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_cita_id_fkey;
ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_odontologo_id_fkey;

ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_clinica_id_fkey;
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_paciente_id_fkey;
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_odontologo_id_fkey;
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_creado_por_id_fkey;

ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_clinica_id_fkey;
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_usuario_id_fkey;
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_medico_actualizo_id_fkey;

ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS usuario_clinica_clinica_id_fkey;
ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS usuario_clinica_usuario_id_fkey;
ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS usuario_clinica_rol_id_fkey;
ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS usuario_clinica_rol_asignado_por_fkey;

-- 5. ELIMINAR RESTRICCIONES DE CLAVE PRIMARIA Y UNICIDAD BASADAS EN UUID
ALTER TABLE notificacion DROP CONSTRAINT IF EXISTS notificacion_pkey;
ALTER TABLE archivo_adjunto DROP CONSTRAINT IF EXISTS archivo_adjunto_pkey;
ALTER TABLE hallazgo_odontograma DROP CONSTRAINT IF EXISTS hallazgo_odontograma_pkey;
ALTER TABLE version_odontograma DROP CONSTRAINT IF EXISTS version_odontograma_pkey;
ALTER TABLE version_atencion_clinica DROP CONSTRAINT IF EXISTS version_atencion_clinica_pkey;
ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_cita_id_key;
ALTER TABLE atencion_clinica DROP CONSTRAINT IF EXISTS atencion_clinica_pkey;
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_pkey;
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS uq_paciente_clinica;
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_pkey;
ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS uq_usuario_clinica_rol;
ALTER TABLE usuario_clinica DROP CONSTRAINT IF EXISTS usuario_clinica_pkey;
ALTER TABLE rol DROP CONSTRAINT IF EXISTS rol_pkey;
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_pkey;
ALTER TABLE clinica DROP CONSTRAINT IF EXISTS clinica_pkey;

-- 6. REEMPLAZAR COLUMNAS UUID POR BIGINT EN CADA TABLA Y AGREGAR IDENTITY
-- clinica
ALTER TABLE clinica DROP COLUMN id;
ALTER TABLE clinica RENAME COLUMN new_id TO id;
ALTER TABLE clinica ALTER COLUMN id SET NOT NULL;
ALTER TABLE clinica ADD CONSTRAINT clinica_pkey PRIMARY KEY (id);
ALTER TABLE clinica ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- usuario
ALTER TABLE usuario DROP COLUMN id;
ALTER TABLE usuario RENAME COLUMN new_id TO id;
ALTER TABLE usuario ALTER COLUMN id SET NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);
ALTER TABLE usuario ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- rol
ALTER TABLE rol DROP COLUMN id;
ALTER TABLE rol RENAME COLUMN new_id TO id;
ALTER TABLE rol ALTER COLUMN id SET NOT NULL;
ALTER TABLE rol ADD CONSTRAINT rol_pkey PRIMARY KEY (id);
ALTER TABLE rol ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- usuario_clinica
ALTER TABLE usuario_clinica
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN usuario_id,
    DROP COLUMN rol_id,
    DROP COLUMN rol_asignado_por;
ALTER TABLE usuario_clinica
    RENAME COLUMN new_id TO id;
ALTER TABLE usuario_clinica
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE usuario_clinica
    RENAME COLUMN new_usuario_id TO usuario_id;
ALTER TABLE usuario_clinica
    RENAME COLUMN new_rol_id TO rol_id;
ALTER TABLE usuario_clinica
    RENAME COLUMN new_rol_asignado_por TO rol_asignado_por;

ALTER TABLE usuario_clinica ALTER COLUMN id SET NOT NULL;
ALTER TABLE usuario_clinica ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE usuario_clinica ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE usuario_clinica ALTER COLUMN rol_id SET NOT NULL;
ALTER TABLE usuario_clinica ADD CONSTRAINT usuario_clinica_pkey PRIMARY KEY (id);
ALTER TABLE usuario_clinica ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
ALTER TABLE usuario_clinica ADD CONSTRAINT uq_usuario_clinica_rol UNIQUE (clinica_id, usuario_id);

-- paciente
ALTER TABLE paciente
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN usuario_id,
    DROP COLUMN medico_actualizo_id;
ALTER TABLE paciente
    RENAME COLUMN new_id TO id;
ALTER TABLE paciente
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE paciente
    RENAME COLUMN new_usuario_id TO usuario_id;
ALTER TABLE paciente
    RENAME COLUMN new_medico_actualizo_id TO medico_actualizo_id;

ALTER TABLE paciente ALTER COLUMN id SET NOT NULL;
ALTER TABLE paciente ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE paciente ADD CONSTRAINT paciente_pkey PRIMARY KEY (id);
ALTER TABLE paciente ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
ALTER TABLE paciente ADD CONSTRAINT uq_paciente_clinica UNIQUE (clinica_id, id);

-- cita
ALTER TABLE cita
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN paciente_id,
    DROP COLUMN odontologo_id,
    DROP COLUMN creado_por_id;
ALTER TABLE cita
    RENAME COLUMN new_id TO id;
ALTER TABLE cita
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE cita
    RENAME COLUMN new_paciente_id TO paciente_id;
ALTER TABLE cita
    RENAME COLUMN new_odontologo_id TO odontologo_id;
ALTER TABLE cita
    RENAME COLUMN new_creado_por_id TO creado_por_id;

ALTER TABLE cita ALTER COLUMN id SET NOT NULL;
ALTER TABLE cita ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE cita ALTER COLUMN paciente_id SET NOT NULL;
ALTER TABLE cita ALTER COLUMN odontologo_id SET NOT NULL;
ALTER TABLE cita ALTER COLUMN creado_por_id SET NOT NULL;
ALTER TABLE cita ADD CONSTRAINT cita_pkey PRIMARY KEY (id);
ALTER TABLE cita ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- atencion_clinica
ALTER TABLE atencion_clinica
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN paciente_id,
    DROP COLUMN cita_id,
    DROP COLUMN odontologo_id;
ALTER TABLE atencion_clinica
    RENAME COLUMN new_id TO id;
ALTER TABLE atencion_clinica
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE atencion_clinica
    RENAME COLUMN new_paciente_id TO paciente_id;
ALTER TABLE atencion_clinica
    RENAME COLUMN new_cita_id TO cita_id;
ALTER TABLE atencion_clinica
    RENAME COLUMN new_odontologo_id TO odontologo_id;

ALTER TABLE atencion_clinica ALTER COLUMN id SET NOT NULL;
ALTER TABLE atencion_clinica ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE atencion_clinica ALTER COLUMN paciente_id SET NOT NULL;
ALTER TABLE atencion_clinica ALTER COLUMN odontologo_id SET NOT NULL;
ALTER TABLE atencion_clinica ADD CONSTRAINT atencion_clinica_pkey PRIMARY KEY (id);
ALTER TABLE atencion_clinica ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
ALTER TABLE atencion_clinica ADD CONSTRAINT atencion_clinica_cita_id_key UNIQUE (cita_id);

-- version_atencion_clinica
ALTER TABLE version_atencion_clinica
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN atencion_clinica_id,
    DROP COLUMN version_anterior_id,
    DROP COLUMN firmado_por;
ALTER TABLE version_atencion_clinica
    RENAME COLUMN new_id TO id;
ALTER TABLE version_atencion_clinica
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE version_atencion_clinica
    RENAME COLUMN new_atencion_clinica_id TO atencion_clinica_id;
ALTER TABLE version_atencion_clinica
    RENAME COLUMN new_version_anterior_id TO version_anterior_id;
ALTER TABLE version_atencion_clinica
    RENAME COLUMN new_firmado_por TO firmado_por;

ALTER TABLE version_atencion_clinica ALTER COLUMN id SET NOT NULL;
ALTER TABLE version_atencion_clinica ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE version_atencion_clinica ALTER COLUMN atencion_clinica_id SET NOT NULL;
ALTER TABLE version_atencion_clinica ADD CONSTRAINT version_atencion_clinica_pkey PRIMARY KEY (id);
ALTER TABLE version_atencion_clinica ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- version_odontograma
ALTER TABLE version_odontograma
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN paciente_id,
    DROP COLUMN version_anterior_id,
    DROP COLUMN firmado_por;
ALTER TABLE version_odontograma
    RENAME COLUMN new_id TO id;
ALTER TABLE version_odontograma
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE version_odontograma
    RENAME COLUMN new_paciente_id TO paciente_id;
ALTER TABLE version_odontograma
    RENAME COLUMN new_version_anterior_id TO version_anterior_id;
ALTER TABLE version_odontograma
    RENAME COLUMN new_firmado_por TO firmado_por;

ALTER TABLE version_odontograma ALTER COLUMN id SET NOT NULL;
ALTER TABLE version_odontograma ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE version_odontograma ALTER COLUMN paciente_id SET NOT NULL;
ALTER TABLE version_odontograma ADD CONSTRAINT version_odontograma_pkey PRIMARY KEY (id);
ALTER TABLE version_odontograma ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- hallazgo_odontograma
ALTER TABLE hallazgo_odontograma
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN version_odontograma_id;
ALTER TABLE hallazgo_odontograma
    RENAME COLUMN new_id TO id;
ALTER TABLE hallazgo_odontograma
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE hallazgo_odontograma
    RENAME COLUMN new_version_odontograma_id TO version_odontograma_id;

ALTER TABLE hallazgo_odontograma ALTER COLUMN id SET NOT NULL;
ALTER TABLE hallazgo_odontograma ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE hallazgo_odontograma ALTER COLUMN version_odontograma_id SET NOT NULL;
ALTER TABLE hallazgo_odontograma ADD CONSTRAINT hallazgo_odontograma_pkey PRIMARY KEY (id);
ALTER TABLE hallazgo_odontograma ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- archivo_adjunto
ALTER TABLE archivo_adjunto
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN paciente_id,
    DROP COLUMN subido_por_usuario_id,
    DROP COLUMN cita_id,
    DROP COLUMN ia_revisado_por;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_id TO id;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_paciente_id TO paciente_id;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_subido_por_usuario_id TO subido_por_usuario_id;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_cita_id TO cita_id;
ALTER TABLE archivo_adjunto
    RENAME COLUMN new_ia_revisado_por TO ia_revisado_por;

ALTER TABLE archivo_adjunto ALTER COLUMN id SET NOT NULL;
ALTER TABLE archivo_adjunto ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE archivo_adjunto ALTER COLUMN paciente_id SET NOT NULL;
ALTER TABLE archivo_adjunto ALTER COLUMN subido_por_usuario_id SET NOT NULL;
ALTER TABLE archivo_adjunto ADD CONSTRAINT archivo_adjunto_pkey PRIMARY KEY (id);
ALTER TABLE archivo_adjunto ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- notificacion
ALTER TABLE notificacion
    DROP COLUMN id,
    DROP COLUMN clinica_id,
    DROP COLUMN usuario_destinatario_id;
ALTER TABLE notificacion
    RENAME COLUMN new_id TO id;
ALTER TABLE notificacion
    RENAME COLUMN new_clinica_id TO clinica_id;
ALTER TABLE notificacion
    RENAME COLUMN new_usuario_destinatario_id TO usuario_destinatario_id;

ALTER TABLE notificacion ALTER COLUMN id SET NOT NULL;
ALTER TABLE notificacion ALTER COLUMN clinica_id SET NOT NULL;
ALTER TABLE notificacion ALTER COLUMN usuario_destinatario_id SET NOT NULL;
ALTER TABLE notificacion ADD CONSTRAINT notificacion_pkey PRIMARY KEY (id);
ALTER TABLE notificacion ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;

-- 7. REESTABLECER LAS 32 FOREIGN KEYS CON REGLAS EXACTAS
-- usuario_clinica (4 FKs)
ALTER TABLE usuario_clinica
    ADD CONSTRAINT usuario_clinica_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE CASCADE,
    ADD CONSTRAINT usuario_clinica_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
    ADD CONSTRAINT usuario_clinica_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES rol(id) ON DELETE RESTRICT,
    ADD CONSTRAINT usuario_clinica_rol_asignado_por_fkey FOREIGN KEY (rol_asignado_por) REFERENCES usuario(id) ON DELETE SET NULL;

-- paciente (3 FKs)
ALTER TABLE paciente
    ADD CONSTRAINT paciente_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT paciente_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE SET NULL,
    ADD CONSTRAINT paciente_medico_actualizo_id_fkey FOREIGN KEY (medico_actualizo_id) REFERENCES usuario_clinica(id) ON DELETE SET NULL;

-- cita (4 FKs)
ALTER TABLE cita
    ADD CONSTRAINT cita_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT cita_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE RESTRICT,
    ADD CONSTRAINT cita_odontologo_id_fkey FOREIGN KEY (odontologo_id) REFERENCES usuario_clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT cita_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES usuario_clinica(id) ON DELETE RESTRICT;

-- atencion_clinica (4 FKs)
ALTER TABLE atencion_clinica
    ADD CONSTRAINT atencion_clinica_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT atencion_clinica_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE RESTRICT,
    ADD CONSTRAINT atencion_clinica_cita_id_fkey FOREIGN KEY (cita_id) REFERENCES cita(id) ON DELETE SET NULL,
    ADD CONSTRAINT atencion_clinica_odontologo_id_fkey FOREIGN KEY (odontologo_id) REFERENCES usuario_clinica(id) ON DELETE RESTRICT;

-- version_atencion_clinica (4 FKs)
ALTER TABLE version_atencion_clinica
    ADD CONSTRAINT version_atencion_clinica_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT version_atencion_clinica_atencion_clinica_id_fkey FOREIGN KEY (atencion_clinica_id) REFERENCES atencion_clinica(id) ON DELETE CASCADE,
    ADD CONSTRAINT version_atencion_clinica_version_anterior_id_fkey FOREIGN KEY (version_anterior_id) REFERENCES version_atencion_clinica(id) ON DELETE SET NULL,
    ADD CONSTRAINT version_atencion_clinica_firmado_por_fkey FOREIGN KEY (firmado_por) REFERENCES usuario_clinica(id) ON DELETE SET NULL;

-- version_odontograma (4 FKs)
ALTER TABLE version_odontograma
    ADD CONSTRAINT version_odontograma_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT version_odontograma_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE RESTRICT,
    ADD CONSTRAINT version_odontograma_version_anterior_id_fkey FOREIGN KEY (version_anterior_id) REFERENCES version_odontograma(id) ON DELETE SET NULL,
    ADD CONSTRAINT version_odontograma_firmado_por_fkey FOREIGN KEY (firmado_por) REFERENCES usuario_clinica(id) ON DELETE SET NULL;

-- hallazgo_odontograma (2 FKs)
ALTER TABLE hallazgo_odontograma
    ADD CONSTRAINT hallazgo_odontograma_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT hallazgo_odontograma_version_odontograma_id_fkey FOREIGN KEY (version_odontograma_id) REFERENCES version_odontograma(id) ON DELETE CASCADE;

-- archivo_adjunto (5 FKs)
ALTER TABLE archivo_adjunto
    ADD CONSTRAINT archivo_adjunto_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE RESTRICT,
    ADD CONSTRAINT archivo_adjunto_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE RESTRICT,
    ADD CONSTRAINT archivo_adjunto_subido_por_usuario_id_fkey FOREIGN KEY (subido_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    ADD CONSTRAINT archivo_adjunto_cita_id_fkey FOREIGN KEY (cita_id) REFERENCES cita(id) ON DELETE SET NULL,
    ADD CONSTRAINT archivo_adjunto_ia_revisado_por_fkey FOREIGN KEY (ia_revisado_por) REFERENCES usuario_clinica(id) ON DELETE SET NULL;

-- notificacion (2 FKs)
ALTER TABLE notificacion
    ADD CONSTRAINT notificacion_clinica_id_fkey FOREIGN KEY (clinica_id) REFERENCES clinica(id) ON DELETE CASCADE,
    ADD CONSTRAINT notificacion_usuario_destinatario_id_fkey FOREIGN KEY (usuario_destinatario_id) REFERENCES usuario(id) ON DELETE CASCADE;

-- 8. RECONSTRUIR INDICES DE CONSULTA
CREATE INDEX IF NOT EXISTS idx_usuario_clinica_lookup ON usuario_clinica (clinica_id, usuario_id);
CREATE INDEX IF NOT EXISTS idx_paciente_clinica ON paciente (clinica_id);
CREATE INDEX IF NOT EXISTS idx_paciente_documento ON paciente (clinica_id, tipo_documento, numero_documento);
CREATE INDEX IF NOT EXISTS idx_cita_agenda ON cita (clinica_id, odontologo_id, inicio_en);
CREATE INDEX IF NOT EXISTS idx_cita_paciente ON cita (paciente_id, inicio_en);
CREATE INDEX IF NOT EXISTS idx_atencion_paciente ON atencion_clinica (paciente_id);
CREATE INDEX IF NOT EXISTS idx_version_atencion_lookup ON version_atencion_clinica (atencion_clinica_id, numero_version);
CREATE INDEX IF NOT EXISTS idx_odontograma_paciente ON version_odontograma (paciente_id);
CREATE INDEX IF NOT EXISTS idx_hallazgo_odontograma_pieza ON hallazgo_odontograma (version_odontograma_id, codigo_pieza_dental);
CREATE INDEX IF NOT EXISTS idx_archivo_paciente ON archivo_adjunto (paciente_id);
CREATE INDEX IF NOT EXISTS idx_notificacion_destinatario ON notificacion (usuario_destinatario_id, estado);

-- 9. AJUSTAR SECUENCIAS IDENTITY PARA QUE NUEVAS ALTAS NO COLISIONEN CON FILAS MIGRADAS
DO $$
DECLARE
    m BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) INTO m FROM clinica;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('clinica', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('clinica', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM usuario;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('usuario', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('usuario', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM rol;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('rol', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('rol', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM usuario_clinica;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('usuario_clinica', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('usuario_clinica', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM paciente;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('paciente', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('paciente', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM cita;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('cita', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('cita', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM atencion_clinica;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('atencion_clinica', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('atencion_clinica', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM version_atencion_clinica;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('version_atencion_clinica', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('version_atencion_clinica', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM version_odontograma;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('version_odontograma', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('version_odontograma', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM hallazgo_odontograma;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('hallazgo_odontograma', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('hallazgo_odontograma', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM archivo_adjunto;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('archivo_adjunto', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('archivo_adjunto', 'id'), 1, false); END IF;

    SELECT COALESCE(MAX(id), 0) INTO m FROM notificacion;
    IF m > 0 THEN PERFORM setval(pg_get_serial_sequence('notificacion', 'id'), m, true);
    ELSE PERFORM setval(pg_get_serial_sequence('notificacion', 'id'), 1, false); END IF;
END $$;
