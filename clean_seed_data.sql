-- Script de Limpieza y Semillero Limpio para Coronyx
-- Restablece las tablas operativas con datos institucionales limpios, contraseñas BCrypt uniformes y secuencias sincronizadas.

BEGIN;

-- 1. Limpieza en orden de dependencias
TRUNCATE TABLE archivo_adjunto RESTART IDENTITY CASCADE;
TRUNCATE TABLE notificacion RESTART IDENTITY CASCADE;
TRUNCATE TABLE hallazgo_odontograma RESTART IDENTITY CASCADE;
TRUNCATE TABLE version_odontograma RESTART IDENTITY CASCADE;
TRUNCATE TABLE version_atencion_clinica RESTART IDENTITY CASCADE;
TRUNCATE TABLE atencion_clinica RESTART IDENTITY CASCADE;
TRUNCATE TABLE cita RESTART IDENTITY CASCADE;
TRUNCATE TABLE paciente RESTART IDENTITY CASCADE;
TRUNCATE TABLE usuario_clinica RESTART IDENTITY CASCADE;
TRUNCATE TABLE usuario RESTART IDENTITY CASCADE;

-- 2. Asegurar Sede Central (Clínica 1) y Sede Secundaria (Clínica 2)
INSERT INTO clinica (id, nombre, razon_social, zona_horaria, estado, fecha_creacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES (1, 'Clínica Dental Coronyx - Sede Central', 'Coronyx Dental S.A.C.', 'America/Lima', 'ABIERTO', NOW(), NOW())
ON CONFLICT (id) DO UPDATE 
SET nombre = EXCLUDED.nombre,
    razon_social = EXCLUDED.razon_social,
    estado = 'ABIERTO',
    fecha_actualizacion = NOW();

INSERT INTO clinica (id, nombre, razon_social, zona_horaria, estado, fecha_creacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES (2, 'Clínica San Isidro Negativo', 'San Isidro SAC', 'America/Lima', 'ABIERTO', NOW(), NOW())
ON CONFLICT (id) DO UPDATE 
SET nombre = EXCLUDED.nombre,
    estado = 'ABIERTO',
    fecha_actualizacion = NOW();

SELECT setval('clinica_id_seq', (SELECT GREATEST(MAX(id), 2) FROM clinica), true);

-- 3. Insertar Usuarios Institucionales Limpios (Contraseña común '123456' hasheada con BCrypt $2a$12$)
-- Hash: $2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO
INSERT INTO usuario (id, correo, clave_hash, nombres, apellidos, estado, fecha_creacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES 
    (1, 'odontologo@coronyx.pe', '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO', 'Andrés', 'Herrera', 'ACTIVO', NOW(), NOW()),
    (2, 'recepcion@coronyx.pe', '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO', 'Paula', 'Suárez', 'ACTIVO', NOW(), NOW()),
    (3, 'admin@coronyx.pe', '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO', 'Carlos', 'Administrador', 'ACTIVO', NOW(), NOW()),
    (4, 'paciente@coronyx.pe', '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO', 'Luis', 'Paciente', 'ACTIVO', NOW(), NOW()),
    (5, 'superadmin@coronyx.pe', '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO', 'Super', 'Admin', 'ACTIVO', NOW(), NOW());

SELECT setval('usuario_id_seq', 5, true);

-- 4. Asociar membresías institucionales en usuario_clinica (Exactamente los cuatro roles del incremento operativo)
INSERT INTO usuario_clinica (id, clinica_id, usuario_id, rol_id, rol_asignado_por, estado, fecha_asignacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES
    (1, 1, 1, (SELECT id FROM rol WHERE codigo = 'ODONTOLOGO'), 3, 'ACTIVO', NOW(), NOW()),
    (2, 1, 2, (SELECT id FROM rol WHERE codigo = 'RECEPCIONISTA'), 3, 'ACTIVO', NOW(), NOW()),
    (3, 1, 3, (SELECT id FROM rol WHERE codigo = 'ADMIN_CLINICA'), 3, 'ACTIVO', NOW(), NOW()),
    (4, 1, 4, (SELECT id FROM rol WHERE codigo = 'PACIENTE'), 3, 'ACTIVO', NOW(), NOW());

SELECT setval('usuario_clinica_id_seq', 4, true);

-- 5. Insertar Pacientes de Negocio Limpios
INSERT INTO paciente (id, clinica_id, usuario_id, nombres, apellidos, tipo_documento, numero_documento, fecha_nacimiento, telefono, correo, estado, alergias, antecedentes_medicos, medicamentos, fecha_creacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES
    (1, 1, 4, 'Luis', 'Paciente', 'DNI', '74829104', '1990-05-10', '987654321', 'paciente@coronyx.pe', 'ACTIVO', 'Penicilina', 'Hipertensión controlada', 'Enalapril 10mg', NOW(), NOW()),
    (2, 1, NULL, 'Juan Carlos', 'Pérez Morales', 'DNI', '12345678', '1985-03-22', '999888777', 'juan.perez@gmail.com', 'ACTIVO', NULL, NULL, NULL, NOW(), NOW()),
    (3, 1, NULL, 'María Elena', 'Rodríguez Torres', 'DNI', '87654321', '1992-08-15', '999111222', 'maria.rodriguez@gmail.com', 'ACTIVO', 'Aspirina', 'Diabetes tipo 2', 'Metformina 850mg', NOW(), NOW()),
    (4, 1, NULL, 'Sebastián', 'Pérez Escobedo', 'DNI', '45678912', '1998-11-04', '999333444', 'sebastian.perez@gmail.com', 'ACTIVO', NULL, NULL, NULL, NOW(), NOW());

SELECT setval('paciente_id_seq', 4, true);

-- 6. Insertar Citas Operativas Iniciales Limpias
INSERT INTO cita (id, clinica_id, paciente_id, odontologo_id, creado_por_id, inicio_en, fin_en, modalidad, estado, motivo, fecha_creacion, fecha_actualizacion)
OVERRIDING SYSTEM VALUE
VALUES
    (1, 1, 1, 1, 2, '2026-10-12 09:00:00-05', '2026-10-12 10:00:00-05', 'PRESENCIAL', 'CONFIRMADA', 'Control y evaluación odontológica general', NOW(), NOW()),
    (2, 1, 2, 1, 2, '2026-10-12 10:30:00-05', '2026-10-12 11:30:00-05', 'PRESENCIAL', 'PROGRAMADA', 'Limpieza dental profiláctica', NOW(), NOW()),
    (3, 1, 3, 1, 2, '2026-10-13 14:00:00-05', '2026-10-13 15:00:00-05', 'PRESENCIAL', 'CONFIRMADA', 'Evaluación de endodoncia', NOW(), NOW()),
    (4, 1, 4, 1, 2, '2026-10-13 15:30:00-05', '2026-10-13 16:30:00-05', 'PRESENCIAL', 'PROGRAMADA', 'Ajuste de ortodoncia', NOW(), NOW());

SELECT setval('cita_id_seq', 4, true);

COMMIT;
