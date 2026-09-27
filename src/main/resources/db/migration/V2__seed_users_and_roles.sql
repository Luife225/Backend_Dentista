-- V2: Semillero de roles institucionales y usuarios reales

-- 1. Rol SUPER_ADMIN
INSERT INTO rol (codigo, nombre, descripcion, permisos_json, activo)
VALUES (
    'SUPER_ADMIN',
    'Super Administrador',
    'Administrador global de la plataforma SaaS y gestión de clínicas',
    '["ALL_PRIVILEGES", "CLINICA_MANAGE", "USER_MANAGE", "GLOBAL_VIEW"]'::jsonb,
    true
)
ON CONFLICT (codigo) DO NOTHING;

-- 2. Asegurar clínica central
INSERT INTO clinica (nombre, razon_social, zona_horaria, estado)
VALUES ('Clínica Dental Coronyx - Sede Central', 'Coronyx Dental S.A.C.', 'America/Lima', 'ABIERTO')
ON CONFLICT DO NOTHING;

-- 3. Usuarios institucionales reales (Contraseña inicial: 123456)
INSERT INTO usuario (correo, clave_hash, nombres, apellidos, estado)
VALUES
    ('odontologo@coronyx.pe', '123456', 'Andrés', 'Herrera', 'ACTIVO'),
    ('recepcion@coronyx.pe', '123456', 'Paula', 'Suárez', 'ACTIVO'),
    ('admin@coronyx.pe', '123456', 'Carlos', 'Administrador', 'ACTIVO'),
    ('paciente@coronyx.pe', '123456', 'Luis', 'Paciente', 'ACTIVO'),
    ('superadmin@coronyx.pe', '123456', 'Super', 'Admin', 'ACTIVO')
ON CONFLICT (correo) DO NOTHING;

-- 4. Asociar usuarios a la clínica central con sus roles respectivos
DO $$
DECLARE
    v_clinica_id UUID;
    v_user_odontologo UUID;
    v_user_recepcion UUID;
    v_user_admin UUID;
    v_user_paciente UUID;
    v_user_superadmin UUID;
    v_rol_odontologo UUID;
    v_rol_recepcion UUID;
    v_rol_admin UUID;
    v_rol_paciente UUID;
    v_rol_superadmin UUID;
BEGIN
    SELECT id INTO v_clinica_id FROM clinica ORDER BY fecha_creacion ASC LIMIT 1;

    SELECT id INTO v_user_odontologo FROM usuario WHERE correo = 'odontologo@coronyx.pe';
    SELECT id INTO v_user_recepcion FROM usuario WHERE correo = 'recepcion@coronyx.pe';
    SELECT id INTO v_user_admin FROM usuario WHERE correo = 'admin@coronyx.pe';
    SELECT id INTO v_user_paciente FROM usuario WHERE correo = 'paciente@coronyx.pe';
    SELECT id INTO v_user_superadmin FROM usuario WHERE correo = 'superadmin@coronyx.pe';

    SELECT id INTO v_rol_odontologo FROM rol WHERE codigo = 'ODONTOLOGO';
    SELECT id INTO v_rol_recepcion FROM rol WHERE codigo = 'RECEPCIONISTA';
    SELECT id INTO v_rol_admin FROM rol WHERE codigo = 'ADMIN_CLINICA';
    SELECT id INTO v_rol_paciente FROM rol WHERE codigo = 'PACIENTE';
    SELECT id INTO v_rol_superadmin FROM rol WHERE codigo = 'SUPER_ADMIN';

    IF v_clinica_id IS NOT NULL THEN
        -- Odontólogo
        IF v_user_odontologo IS NOT NULL AND v_rol_odontologo IS NOT NULL THEN
            INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
            VALUES (v_clinica_id, v_user_odontologo, v_rol_odontologo, 'ACTIVO')
            ON CONFLICT (clinica_id, usuario_id) DO UPDATE SET rol_id = EXCLUDED.rol_id;
        END IF;

        -- Recepcionista
        IF v_user_recepcion IS NOT NULL AND v_rol_recepcion IS NOT NULL THEN
            INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
            VALUES (v_clinica_id, v_user_recepcion, v_rol_recepcion, 'ACTIVO')
            ON CONFLICT (clinica_id, usuario_id) DO UPDATE SET rol_id = EXCLUDED.rol_id;
        END IF;

        -- Administrador Clínica
        IF v_user_admin IS NOT NULL AND v_rol_admin IS NOT NULL THEN
            INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
            VALUES (v_clinica_id, v_user_admin, v_rol_admin, 'ACTIVO')
            ON CONFLICT (clinica_id, usuario_id) DO UPDATE SET rol_id = EXCLUDED.rol_id;
        END IF;

        -- Paciente
        IF v_user_paciente IS NOT NULL AND v_rol_paciente IS NOT NULL THEN
            INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
            VALUES (v_clinica_id, v_user_paciente, v_rol_paciente, 'ACTIVO')
            ON CONFLICT (clinica_id, usuario_id) DO UPDATE SET rol_id = EXCLUDED.rol_id;
        END IF;

        -- Super Admin
        IF v_user_superadmin IS NOT NULL AND v_rol_superadmin IS NOT NULL THEN
            INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
            VALUES (v_clinica_id, v_user_superadmin, v_rol_superadmin, 'ACTIVO')
            ON CONFLICT (clinica_id, usuario_id) DO UPDATE SET rol_id = EXCLUDED.rol_id;
        END IF;
    END IF;
END $$;
