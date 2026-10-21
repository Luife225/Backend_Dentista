-- V5: Bootstrap seguro de cuentas y corrección de seeds operativos
-- 1. Asegurar contraseñas con hash BCrypt para usuarios existentes
UPDATE usuario
SET clave_hash = '$2a$12$dJv1UTQjcbs9B0RkrFAZX..SFNijMRVVluarPK5pRzWh4cQn22QtO'
WHERE clave_hash IS NULL OR clave_hash = '123456' OR length(clave_hash) < 30;

-- 2. Desvincular membresías operativas de SUPER_ADMIN en usuario_clinica
-- SUPER_ADMIN es una identidad SaaS global fuera del ámbito operativo de las clínicas.
DELETE FROM usuario_clinica 
WHERE rol_id IN (SELECT id FROM rol WHERE codigo = 'SUPER_ADMIN');

-- 3. Asegurar los cuatro roles del incremento operativo
INSERT INTO rol (codigo, nombre, descripcion, permisos_json, activo)
VALUES 
    ('ADMIN_CLINICA', 'Administrador de Clínica', 'Gestión administrativa, personal y configuración de sede', '["CLINIC_ADMIN", "USERS_MANAGE", "PATIENTS_MANAGE", "APPOINTMENTS_MANAGE"]'::jsonb, true),
    ('ODONTOLOGO', 'Odontólogo Especialista', 'Atención clínica asistencial, historia, odontograma y citas propias', '["CLINICAL_ATTENTION", "ODONTOGRAM_WRITE", "APPOINTMENTS_READ"]'::jsonb, true),
    ('RECEPCIONISTA', 'Recepcionista de Sede', 'Gestión de agenda, citas, pagos y ficha administrativa', '["APPOINTMENTS_MANAGE", "PATIENTS_ADMIN", "SCHEDULE_VIEW"]'::jsonb, true),
    ('PACIENTE', 'Paciente Titular', 'Acceso a portal de paciente, citas propias y resumen', '["PORTAL_ACCESS", "MY_APPOINTMENTS"]'::jsonb, true)
ON CONFLICT (codigo) DO UPDATE 
SET activo = true,
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion;
