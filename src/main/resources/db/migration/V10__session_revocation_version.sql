-- Conserva cuentas, vínculos y registros existentes. Permite revocar tokens ya emitidos.
ALTER TABLE usuario ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_session_version CHECK (session_version >= 0);
