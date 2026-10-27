-- V19: Habilitar SUPER_ADMIN como rol de Soporte Tecnico TI para la clinica central
-- 1. Actualizar metadatos del rol SUPER_ADMIN
UPDATE rol
SET nombre = 'Soporte Tecnico TI',
    descripcion = 'Soporte tecnico, mesa de ayuda e incidencias globales de la plataforma',
    activo = true
WHERE codigo = 'SUPER_ADMIN';

-- 2. Asegurar que superadmin@coronyx.pe tenga hash BCrypt de 123456 y este ACTIVO
UPDATE usuario
SET clave_hash = '$2a$12$jaFqSb3PGkdmyW..hsz0H.E0RPdJi02iujJc6icr.DuPTP5pZdNey',
    estado = 'ACTIVO'
WHERE correo = 'superadmin@coronyx.pe';

-- 3. Vincular a superadmin@coronyx.pe a la clinica central con el rol SUPER_ADMIN
INSERT INTO usuario_clinica (clinica_id, usuario_id, rol_id, estado)
SELECT c.id, u.id, r.id, 'ACTIVO'
FROM clinica c, usuario u, rol r
WHERE c.id = 1
  AND u.correo = 'superadmin@coronyx.pe'
  AND r.codigo = 'SUPER_ADMIN'
ON CONFLICT (clinica_id, usuario_id) DO UPDATE
SET rol_id = EXCLUDED.rol_id,
    estado = 'ACTIVO';
