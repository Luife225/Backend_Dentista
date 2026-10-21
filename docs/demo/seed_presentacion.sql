-- =========================================================================================
-- PROYECTO CORONYX — RESET Y CARGA CONTROLADA DE DATOS PARA PRESENTACION (R2)
-- SCRIPT DE TRANSACCION ATOMICA Y REPRODUCIBLE (CARIES / RESTAURACIONES)
-- Ubicacion: Backend_Dentista/docs/demo/seed_presentacion.sql
-- =========================================================================================

-- 1. GUARDIA DE SEGURIDAD DEL DESTINO
DO $$
BEGIN
    IF current_database() != 'coronyx' THEN
        RAISE EXCEPTION 'ABORTADO: La base de datos actual es % pero este script solo puede ejecutarse sobre coronyx.', current_database();
    END IF;
END $$;

BEGIN;

-- =========================================================================================
-- 2. LIMPIEZA EXPLICITA DE TABLAS DE NEGOCIO Y CUENTAS EN ORDEN DE DEPENDENCIAS FK
-- Conserva flyway_schema_history, rol, permiso, rol_permiso y definiciones DDL
-- =========================================================================================

DELETE FROM hallazgo_odontograma;
DELETE FROM version_odontograma;

DELETE FROM version_atencion_clinica;
DELETE FROM atencion_clinica;

DELETE FROM solicitud_reprogramacion;
DELETE FROM cita;

DELETE FROM archivo_adjunto;
DELETE FROM notificacion;

DELETE FROM ausencia_odontologo;
DELETE FROM horario_atencion;

DELETE FROM paciente;
DELETE FROM usuario_clinica;
DELETE FROM usuario;
DELETE FROM clinica;

-- =========================================================================================
-- 3. REINICIO DE IDENTIDADES IDENTITY A VALORES DETERMINISTAS (1..N)
-- =========================================================================================

ALTER TABLE clinica ALTER COLUMN id RESTART WITH 1;
ALTER TABLE usuario ALTER COLUMN id RESTART WITH 1;
ALTER TABLE usuario_clinica ALTER COLUMN id RESTART WITH 1;
ALTER TABLE paciente ALTER COLUMN id RESTART WITH 1;
ALTER TABLE horario_atencion ALTER COLUMN id RESTART WITH 1;
ALTER TABLE ausencia_odontologo ALTER COLUMN id RESTART WITH 1;
ALTER TABLE cita ALTER COLUMN id RESTART WITH 1;
ALTER TABLE solicitud_reprogramacion ALTER COLUMN id RESTART WITH 1;
ALTER TABLE atencion_clinica ALTER COLUMN id RESTART WITH 1;
ALTER TABLE version_atencion_clinica ALTER COLUMN id RESTART WITH 1;
ALTER TABLE version_odontograma ALTER COLUMN id RESTART WITH 1;
ALTER TABLE hallazgo_odontograma ALTER COLUMN id RESTART WITH 1;
ALTER TABLE archivo_adjunto ALTER COLUMN id RESTART WITH 1;
ALTER TABLE notificacion ALTER COLUMN id RESTART WITH 1;

-- =========================================================================================
-- 4. INSERT CLINICA DE DEMOSTRACION
-- =========================================================================================

INSERT INTO clinica (
    id, nombre, razon_social, ruc, telefono, correo, ciudad, direccion, zona_horaria, estado, fecha_creacion, fecha_actualizacion
) OVERRIDING SYSTEM VALUE VALUES (
    1,
    'Sede Central Coronyx',
    'Coronyx Dental System S.A.C.',
    '20601234567',
    '+51 1 456 7890',
    'contacto@coronyx.pe',
    'Lima, Perú',
    'Av. Javier Prado Este 2465, San Borja',
    'America/Lima',
    'ABIERTO',
    '2026-07-01 08:00:00-05:00',
    '2026-07-01 08:00:00-05:00'
);

-- =========================================================================================
-- 5. INSERT 9 USUARIOS (1 ADMIN, 2 ODONTOLOGOS, 1 RECEPCIONISTA, 5 PACIENTES)
-- Hashes generados con BCryptPasswordEncoder(12)
-- =========================================================================================

INSERT INTO usuario (
    id, nombres, apellidos, correo, clave_hash, estado, correo_verificado_en, session_version, fecha_creacion, fecha_actualizacion
) OVERRIDING SYSTEM VALUE VALUES
(1, 'Elena', 'Demo', 'admin@coronyx-demo.example', '$2a$12$NTafX/bk3sQqoNjH.VgDIOD6VIKqloETwlVKOJFvCXHFjdueryRMK', 'ACTIVO', '2026-07-01 08:00:00-05:00', 0, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(2, 'Camila', 'Soto Demo', 'odontologo1@coronyx-demo.example', '$2a$12$1Yx6.1YWQqOBYwEDAADamerchI8NVygvlvnP8kMQUWD3Xpqw4gBFq', 'ACTIVO', '2026-07-01 08:00:00-05:00', 0, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(3, 'Andrés', 'Vega Demo', 'odontologo2@coronyx-demo.example', '$2a$12$l1yStcFY/ls6zHHYj2s19uc4LwHDwVpjHGsNSGPfKB3Vx0JrxYRLu', 'ACTIVO', '2026-07-01 08:00:00-05:00', 0, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(4, 'Rosa', 'Demo', 'recepcion@coronyx-demo.example', '$2a$12$zMea.G4vol3XpP1BnTCBUupG85CFFqyaLRb.pcXJU/9i65Y7n4wMK', 'ACTIVO', '2026-07-01 08:00:00-05:00', 0, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(5, 'Marina', 'Ríos Demo', 'paciente1@coronyx-demo.example', '$2a$12$BLlxe3.7AiDP06brxDL95OEguKb4ICU6BlUZoSI9ozEQTHHkWBoui', 'ACTIVO', '2026-07-28 14:00:00-05:00', 0, '2026-07-28 14:00:00-05:00', '2026-07-28 14:00:00-05:00'),
(6, 'Diego', 'Torres Demo', 'paciente2@coronyx-demo.example', '$2a$12$uLofapEnauWBU5Jthj31H.wQQXUSlOoZut17ZpPof.Zc672W8oZzK', 'ACTIVO', '2026-09-01 09:00:00-05:00', 0, '2026-09-01 09:00:00-05:00', '2026-09-01 09:00:00-05:00'),
(7, 'Lucía', 'Paredes Demo', 'paciente3@coronyx-demo.example', '$2a$12$wEb1ou9/jCwmsbdk7V6Rh.x5KDAzSSRTDeoRyPvJuKM/3catiqPyu', 'ACTIVO', '2026-09-08 10:00:00-05:00', 0, '2026-09-08 10:00:00-05:00', '2026-09-08 10:00:00-05:00'),
(8, 'Nadia', 'Solís Demo', 'paciente4@coronyx-demo.example', '$2a$12$JGcQ4Rn7xQex0zouYYxtS.M.3aG65MzDDoEffjQGr7O5gc2M3LAdC', 'ACTIVO', '2026-10-09 15:00:00-05:00', 0, '2026-10-09 15:00:00-05:00', '2026-10-09 15:00:00-05:00'),
(9, 'Mateo', 'Luna Demo', 'paciente5@coronyx-demo.example', '$2a$12$.sYSVTmDFtKKVJvCJFzPKeel5RzdamYPpUQTiNve4uoJ9kBQk4UDK', 'ACTIVO', '2026-10-09 15:30:00-05:00', 0, '2026-10-09 15:30:00-05:00', '2026-10-09 15:30:00-05:00');

-- =========================================================================================
-- 6. INSERT 9 MEMBRESIAS USUARIO_CLINICA
-- rol_id: 1=ADMIN_CLINICA, 2=ODONTOLOGO, 3=RECEPCIONISTA, 4=PACIENTE
-- =========================================================================================

INSERT INTO usuario_clinica (
    id, clinica_id, usuario_id, rol_id, estado, fecha_asignacion, fecha_actualizacion
) OVERRIDING SYSTEM VALUE VALUES
(1, 1, 1, 1, 'ACTIVO', '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(2, 1, 2, 2, 'ACTIVO', '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(3, 1, 3, 2, 'ACTIVO', '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(4, 1, 4, 3, 'ACTIVO', '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(5, 1, 5, 4, 'ACTIVO', '2026-07-28 14:00:00-05:00', '2026-07-28 14:00:00-05:00'),
(6, 1, 6, 4, 'ACTIVO', '2026-09-01 09:00:00-05:00', '2026-09-01 09:00:00-05:00'),
(7, 1, 7, 4, 'ACTIVO', '2026-09-08 10:00:00-05:00', '2026-09-08 10:00:00-05:00'),
(8, 1, 8, 4, 'ACTIVO', '2026-10-09 15:00:00-05:00', '2026-10-09 15:00:00-05:00'),
(9, 1, 9, 4, 'ACTIVO', '2026-10-09 15:30:00-05:00', '2026-10-09 15:30:00-05:00');

-- =========================================================================================
-- 7. INSERT 5 EXPEDIENTES DE PACIENTE VINCULADOS A USUARIOS TITULARES
-- =========================================================================================

INSERT INTO paciente (
    id, clinica_id, usuario_id, nombres, apellidos, tipo_documento, numero_documento,
    fecha_nacimiento, telefono, correo, direccion, ciudad, tipo_sangre, seguro, estado, fecha_creacion, fecha_actualizacion
) OVERRIDING SYSTEM VALUE VALUES
(1, 1, 5, 'Marina', 'Ríos Demo', 'DNI', '71234501', '1995-04-12', '987654301', 'paciente1@coronyx-demo.example', 'Av. Los Frutales 124, La Molina', 'Lima', 'O+', 'PARTICULAR', 'ACTIVO', '2026-07-28 14:10:00-05:00', '2026-07-28 14:10:00-05:00'),
(2, 1, 6, 'Diego', 'Torres Demo', 'DNI', '71234502', '1988-11-23', '987654302', 'paciente2@coronyx-demo.example', 'Jr. Las Palmeras 450, San Isidro', 'Lima', 'A+', 'PARTICULAR', 'ACTIVO', '2026-09-01 09:15:00-05:00', '2026-09-01 09:15:00-05:00'),
(3, 1, 7, 'Lucía', 'Paredes Demo', 'DNI', '71234503', '1992-07-08', '987654303', 'paciente3@coronyx-demo.example', 'Calle Los Pinos 320, Miraflores', 'Lima', 'B+', 'RIMAC', 'ACTIVO', '2026-09-08 10:15:00-05:00', '2026-09-08 10:15:00-05:00'),
(4, 1, 8, 'Nadia', 'Solís Demo', 'DNI', '71234504', '2000-02-14', '987654304', 'paciente4@coronyx-demo.example', 'Av. República de Panamá 5600, Surquillo', 'Lima', 'O+', 'PARTICULAR', 'ACTIVO', '2026-10-09 15:10:00-05:00', '2026-10-09 15:10:00-05:00'),
(5, 1, 9, 'Mateo', 'Luna Demo', 'DNI', '71234505', '1998-09-30', '987654305', 'paciente5@coronyx-demo.example', 'Calle Alcanfores 890, Miraflores', 'Lima', 'AB+', 'PACIFICO', 'ACTIVO', '2026-10-09 15:40:00-05:00', '2026-10-09 15:40:00-05:00');

-- =========================================================================================
-- 8. INSERT HORARIOS DE ATENCION DE CLINICA Y DOCTORES
-- =========================================================================================

INSERT INTO horario_atencion (
    clinica_id, odontologo_id, dia_semana, hora_apertura, hora_cierre, activo, duracion_turno_minutos, fecha_creacion, fecha_actualizacion
) VALUES
(1, NULL, 1, '08:00', '18:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(1, NULL, 2, '08:00', '18:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(1, NULL, 3, '08:00', '18:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(1, NULL, 4, '08:00', '18:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(1, NULL, 5, '08:00', '18:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00'),
(1, NULL, 6, '08:00', '13:00', true, 30, '2026-07-01 08:00:00-05:00', '2026-07-01 08:00:00-05:00');

-- =========================================================================================
-- 9. INSERT 11 CITAS (6 HISTORICAS FINALIZADAS/ASISTIDAS + 5 FUTURAS)
-- Odontologo 1 = usuario_clinica id 2; Odontologo 2 = usuario_clinica id 3
-- Creadas por Recepcionista (usuario_clinica id 4)
-- =========================================================================================

INSERT INTO cita (
    id, clinica_id, paciente_id, odontologo_id, creado_por_id,
    inicio_en, fin_en, modalidad, estado, estado_asistencia, motivo, consultorio, fecha_creacion, fecha_actualizacion
) OVERRIDING SYSTEM VALUE VALUES
-- P001 Históricas con Odontólogo 1
(1, 1, 1, 2, 4, '2026-08-03 09:00:00-05:00', '2026-08-03 10:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Consulta inicial por dolor leve en molares superiores', 'Sillón 1', '2026-07-28 14:30:00-05:00', '2026-08-03 10:00:00-05:00'),
(2, 1, 1, 2, 4, '2026-08-17 09:00:00-05:00', '2026-08-17 10:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Primera restauración: resina compuesta oclusal en pieza 16', 'Sillón 1', '2026-08-03 09:58:00-05:00', '2026-08-17 10:00:00-05:00'),
(3, 1, 1, 2, 4, '2026-09-07 09:00:00-05:00', '2026-09-07 10:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Segunda restauración: resina compuesta oclusal en pieza 26', 'Sillón 1', '2026-08-17 09:52:00-05:00', '2026-09-07 10:00:00-05:00'),
(4, 1, 1, 2, 4, '2026-09-21 09:00:00-05:00', '2026-09-21 10:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Control clínico post-restauración y profilaxis', 'Sillón 1', '2026-09-07 09:55:00-05:00', '2026-09-21 10:00:00-05:00'),

-- P002 Histórica con Odontólogo 2
(5, 1, 2, 3, 4, '2026-09-08 10:00:00-05:00', '2026-09-08 11:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Restauración oclusal de molar inferior izquierdo 36', 'Sillón 2', '2026-09-01 10:00:00-05:00', '2026-09-08 11:00:00-05:00'),

-- P003 Histórica con Odontólogo 2
(6, 1, 3, 3, 4, '2026-09-15 11:00:00-05:00', '2026-09-15 12:00:00-05:00', 'PRESENCIAL', 'FINALIZADA', 'ASISTIO', 'Evaluación odontológica general y profilaxis dental', 'Sillón 2', '2026-09-08 11:15:00-05:00', '2026-09-15 12:00:00-05:00'),

-- 5 Citas Futuras calculadas sin conflictos de horario/sillón/profesional
(7, 1, 1, 2, 4, '2026-10-13 10:00:00-05:00', '2026-10-13 10:30:00-05:00', 'PRESENCIAL', 'CONFIRMADA', NULL, 'Control semestral preventivo y revisión de restauraciones', 'Sillón 1', '2026-09-21 09:50:00-05:00', '2026-10-09 11:00:00-05:00'),
(8, 1, 2, 3, 4, '2026-10-14 11:00:00-05:00', '2026-10-14 11:30:00-05:00', 'PRESENCIAL', 'PROGRAMADA', NULL, 'Control y seguimiento post-operatorio de pieza 36', 'Sillón 2', '2026-09-08 11:05:00-05:00', '2026-09-08 11:05:00-05:00'),
(9, 1, 3, 3, 4, '2026-10-15 11:00:00-05:00', '2026-10-15 11:30:00-05:00', 'PRESENCIAL', 'CONFIRMADA', NULL, 'Seguimiento semestral programado y control gingival', 'Sillón 2', '2026-09-15 12:05:00-05:00', '2026-10-08 14:00:00-05:00'),
(10, 1, 4, 2, 4, '2026-10-16 10:00:00-05:00', '2026-10-16 10:30:00-05:00', 'PRESENCIAL', 'PROGRAMADA', NULL, 'Consulta inicial de diagnóstico integral y plan de tratamiento', 'Sillón 1', '2026-10-09 16:00:00-05:00', '2026-10-09 16:00:00-05:00'),
(11, 1, 5, 3, 4, '2026-10-19 11:30:00-05:00', '2026-10-19 12:00:00-05:00', 'PRESENCIAL', 'PROGRAMADA', NULL, 'Evaluación clínica general de primera visita y profilaxis', 'Sillón 2', '2026-10-09 16:30:00-05:00', '2026-10-09 16:30:00-05:00');

-- =========================================================================================
-- 10. INSERT 6 ATENCIONES CLINICAS (4 P001 + 1 P002 + 1 P003)
-- =========================================================================================

INSERT INTO atencion_clinica (
    id, clinica_id, paciente_id, cita_id, odontologo_id, estado, fecha_creacion, fecha_confirmacion
) OVERRIDING SYSTEM VALUE VALUES
(1, 1, 1, 1, 2, 'CONFIRMADO', '2026-08-03 09:10:00-05:00', '2026-08-03 09:55:00-05:00'),
(2, 1, 1, 2, 2, 'CONFIRMADO', '2026-08-17 09:05:00-05:00', '2026-08-17 09:50:00-05:00'),
(3, 1, 1, 3, 2, 'CONFIRMADO', '2026-09-07 09:05:00-05:00', '2026-09-07 09:52:00-05:00'),
(4, 1, 1, 4, 2, 'CONFIRMADO', '2026-09-21 09:08:00-05:00', '2026-09-21 09:45:00-05:00'),
(5, 1, 2, 5, 3, 'CONFIRMADO', '2026-09-08 10:05:00-05:00', '2026-09-08 10:50:00-05:00'),
(6, 1, 3, 6, 3, 'CONFIRMADO', '2026-09-15 11:05:00-05:00', '2026-09-15 11:55:00-05:00');

-- =========================================================================================
-- 11. INSERT 6 VERSIONES CLINICAS INICIALES CONFIRMADAS (1 POR ATENCION)
-- =========================================================================================

INSERT INTO version_atencion_clinica (
    id, clinica_id, atencion_clinica_id, version_anterior_id, firmado_por, numero_version,
    motivo_consulta, examen_clinico, diagnostico, plan_tratamiento, procedimientos,
    recetas_prescripciones, indicaciones, evolucion, signos_vitales, proxima_cita_sugerida,
    esta_confirmado, fecha_registro, fecha_confirmacion
) OVERRIDING SYSTEM VALUE VALUES
-- P001 - Visita 1
(1, 1, 1, NULL, 2, 1,
 'Dolor intermitente a la masticación en zona posterior superior derecha e izquierda.',
 'Lesiones cariosas cavitadas en fosas y fisuras oclusales de piezas 16 y 26, sin compromiso pulpar evidente. Encías clínicamente sanas.',
 'K02.1 Caries de la dentina en piezas 16 y 26 (caras oclusales).',
 '1. Profilaxis dental. 2. Restauración con resina compuesta fotopolimerizable pieza 16. 3. Restauración pieza 26 en segunda sesión. 4. Control clínico.',
 'Evaluación odontológica completa, odontograma diagnóstico y profilaxis profiláctica coronal.',
 'Ninguna prescripción requerida.',
 'Evitar alimentos excesivamente azucarados y realizar cepillado tras cada comida con técnica de Bass.',
 'Paciente tolera evaluación. Se programa primera restauración para el 17 de agosto.',
 '{"pa": "115/75", "pulso": 72, "temperatura": 36.5}'::jsonb,
 '2026-08-17',
 true, '2026-08-03 09:12:00-05:00', '2026-08-03 09:55:00-05:00'),

-- P001 - Visita 2
(2, 1, 2, NULL, 2, 1,
 'Sesión programada para restauración de molar superior derecho 16.',
 'Pieza 16 presenta cavidad oclusal limpia tras remoción de tejido cariado. Pieza 26 pendiente de restauración.',
 'K02.1 Caries dentinaria tratada en pieza 16; caries oclusal activa en pieza 26.',
 'Restauración directa con resina compuesta fotocurable en pieza 16 completada. Próxima sesión pieza 26.',
 'Anestesia infiltrativa local, aislamiento absoluto, apertura y eliminación de caries dentinaria en 16, grabado ácido, adhesivo universal, obturación resina compuesta nanohíbrida A2, pulido y control de oclusión.',
 'Paracetamol 500mg condicional si presenta molestia postoperatoria leve.',
 'No masticar alimentos duros durante las primeras 2 horas posteriores al procedimiento.',
 'Procedimiento culminado sin complicaciones. Oclusión adecuada sin interferencias.',
 '{"pa": "118/76", "pulso": 70, "temperatura": 36.6}'::jsonb,
 '2026-09-07',
 true, '2026-08-17 09:10:00-05:00', '2026-08-17 09:50:00-05:00'),

-- P001 - Visita 3
(3, 1, 3, NULL, 2, 1,
 'Restauración programada de molar superior izquierdo 26.',
 'Pieza 16 restaurada estable. Pieza 26 con lesión cariosa oclusal sin afectación pulpar.',
 'K02.1 Caries dentinaria tratada en pieza 26.',
 'Restauración con resina oclusal en pieza 26 completada. Programar cita de control y profilaxis.',
 'Aislamiento absoluto, eliminación de tejido reblandecido en 26, acondicionamiento dentinario, colocación de resina fotopolimerizable, ajuste oclusal y pulido.',
 'Ninguna medicación requerida.',
 'Mantener técnica de cepillado y uso riguroso de hilo dental interproximal.',
 'Paciente refiere alivio completo. Ambas molares restauradas y en correcta función masticatoria.',
 '{"pa": "116/74", "pulso": 68, "temperatura": 36.4}'::jsonb,
 '2026-09-21',
 true, '2026-09-07 09:10:00-05:00', '2026-09-07 09:52:00-05:00'),

-- P001 - Visita 4
(4, 1, 4, NULL, 2, 1,
 'Control clínico evolutivo de restauraciones molares.',
 'Restauraciones en piezas 16 y 26 íntegras, con excelente sellado marginal, sin filtración ni recidiva cariosa. Sensibilidad pulpar normal al estímulo térmico.',
 'Z01.2 Examen odontológico de control y seguimiento post-restauración satisfactorio.',
 'Mantenimiento preventivo semestral y refuerzo de higiene oral.',
 'Inspección clínica minuciosa, sondaje periodontal no invasivo, prueba de vitalidad pulpar y pulido superficial de resinas.',
 'Ninguna medicación necesaria.',
 'Continuar con crema dental fluorada y agendar control semestral de rutina.',
 'Paciente asintomática, alta asistencial del episodio de caries. Se coordina próximo control semestral.',
 '{"pa": "114/72", "pulso": 70, "temperatura": 36.5}'::jsonb,
 '2026-10-13',
 true, '2026-09-21 09:12:00-05:00', '2026-09-21 09:45:00-05:00'),

-- P002 - Visita Histórica
(5, 1, 5, NULL, 3, 1,
 'Consulta y restauración de molestia en pieza 36.',
 'Pieza 36 con cavidad oclusal que retiene placa. Vitalidad pulpar positiva.',
 'K02.1 Caries de la dentina en pieza 36.',
 'Restauración con resina compuesta oclusal en pieza 36.',
 'Aislamiento relativo, eliminación de caries en pieza 36, grabado ácido, adhesivo y resina nanohíbrida.',
 'Ninguna prescripción requerida.',
 'Higiene oral adecuada tras cada alimento.',
 'Obturación realizada exitosamente.',
 '{"pa": "120/80", "pulso": 74, "temperatura": 36.6}'::jsonb,
 '2026-10-14',
 true, '2026-09-08 10:10:00-05:00', '2026-09-08 10:50:00-05:00'),

-- P003 - Visita Histórica
(6, 1, 6, NULL, 3, 1,
 'Examen clínico anual y limpieza bucal.',
 'Piezas dentales íntegras, encías sanas, leve presencia de tártaro anteroinferior.',
 'Z01.2 Examen odontológico de rutina sin hallazgos patológicos activos.',
 'Profilaxis dental y control preventivo semestral.',
 'Destartraje supragingival ultrasónico y profilaxis con pasta abrasiva.',
 'Ninguna prescripción requerida.',
 'Uso diario de hilo dental.',
 'Paciente conforme con procedimiento.',
 '{"pa": "112/70", "pulso": 66, "temperatura": 36.3}'::jsonb,
 '2026-10-15',
 true, '2026-09-15 11:10:00-05:00', '2026-09-15 11:55:00-05:00');

-- =========================================================================================
-- 12. INSERT 6 VERSIONES DE ODONTOGRAMA CONFIRMADAS
-- P001: V1 (03/08), V2 (17/08), V3 (07/09), V4 (21/09)
-- P002: V1 (08/09)
-- P003: V1 (15/09)
-- =========================================================================================

INSERT INTO version_odontograma (
    id, clinica_id, paciente_id, version_anterior_id, firmado_por, numero_version, estado, fecha_creacion, fecha_confirmacion
) OVERRIDING SYSTEM VALUE VALUES
(1, 1, 1, NULL, 2, 1, 'CONFIRMADO', '2026-08-03 09:15:00-05:00', '2026-08-03 09:50:00-05:00'),
(2, 1, 1, 1,    2, 2, 'CONFIRMADO', '2026-08-17 09:40:00-05:00', '2026-08-17 09:48:00-05:00'),
(3, 1, 1, 2,    2, 3, 'CONFIRMADO', '2026-09-07 09:42:00-05:00', '2026-09-07 09:50:00-05:00'),
(4, 1, 1, 3,    2, 4, 'CONFIRMADO', '2026-09-21 09:30:00-05:00', '2026-09-21 09:42:00-05:00'),
(5, 1, 2, NULL, 3, 1, 'CONFIRMADO', '2026-09-08 10:40:00-05:00', '2026-09-08 10:45:00-05:00'),
(6, 1, 3, NULL, 3, 1, 'CONFIRMADO', '2026-09-15 11:40:00-05:00', '2026-09-15 11:45:00-05:00');

-- =========================================================================================
-- 13. INSERT 9 HALLAZGOS DE ODONTOGRAMA (CARIES / RESTAURACIONES)
-- =========================================================================================

INSERT INTO hallazgo_odontograma (
    id, clinica_id, version_odontograma_id, codigo_pieza_dental, codigo_superficie_cara, codigo_condicion, nota_observacion
) OVERRIDING SYSTEM VALUE VALUES
-- P001 - V1: Caries 16 Oclusal y 26 Oclusal
(1, 1, 1, '16', 'OCLUSAL', 'CARIES', 'Caries oclusal media en esmalte y dentina'),
(2, 1, 1, '26', 'OCLUSAL', 'CARIES', 'Caries oclusal incipiente en fosas y fisuras'),

-- P001 - V2: 16 Restaurado, 26 sigue Caries
(3, 1, 2, '16', 'OCLUSAL', 'RESTAURADO', 'Resina compuesta nanohíbrida oclusal adaptada'),
(4, 1, 2, '26', 'OCLUSAL', 'CARIES', 'Caries oclusal pendiente de restauración'),

-- P001 - V3: 16 Restaurado, 26 Restaurado
(5, 1, 3, '16', 'OCLUSAL', 'RESTAURADO', 'Resina oclusal 16 en excelente estado'),
(6, 1, 3, '26', 'OCLUSAL', 'RESTAURADO', 'Resina compuesta oclusal 26 recién realizada'),

-- P001 - V4: 16 y 26 Restaurado (Control estable)
(7, 1, 4, '16', 'OCLUSAL', 'RESTAURADO', 'Sellado marginal óptimo y asintomático'),
(8, 1, 4, '26', 'OCLUSAL', 'RESTAURADO', 'Sellado marginal óptimo y asintomático'),

-- P002 - V1: 36 Restaurado
(9, 1, 5, '36', 'OCLUSAL', 'RESTAURADO', 'Restauración oclusal con resina en primer molar inferior');

-- =========================================================================================
-- 14. VALIDACION AUTOMATICA ESTRICTA ANTES DE CONFIRMAR COMMIT
-- =========================================================================================

DO $$
DECLARE
    v_usuarios BIGINT;
    v_membresias BIGINT;
    v_pacientes BIGINT;
    v_citas BIGINT;
    v_atenciones BIGINT;
    v_versiones_clinicas BIGINT;
    v_versiones_odontograma BIGINT;
    v_hallazgos BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_usuarios FROM usuario;
    SELECT COUNT(*) INTO v_membresias FROM usuario_clinica;
    SELECT COUNT(*) INTO v_pacientes FROM paciente;
    SELECT COUNT(*) INTO v_citas FROM cita;
    SELECT COUNT(*) INTO v_atenciones FROM atencion_clinica;
    SELECT COUNT(*) INTO v_versiones_clinicas FROM version_atencion_clinica;
    SELECT COUNT(*) INTO v_versiones_odontograma FROM version_odontograma;
    SELECT COUNT(*) INTO v_hallazgos FROM hallazgo_odontograma;

    IF v_usuarios != 9 OR v_membresias != 9 OR v_pacientes != 5 OR v_citas != 11 
       OR v_atenciones != 6 OR v_versiones_clinicas != 6 OR v_versiones_odontograma != 6 OR v_hallazgos != 9 THEN
        RAISE EXCEPTION 'VALIDACION FALLIDA: Conteo inconsistente (u=%, m=%, p=%, c=%, a=%, vc=%, vo=%, h=%)',
            v_usuarios, v_membresias, v_pacientes, v_citas, v_atenciones, v_versiones_clinicas, v_versiones_odontograma, v_hallazgos;
    END IF;

    RAISE NOTICE 'VALIDACION EXITOSA: Los 9 usuarios, 9 membresias, 5 pacientes, 11 citas, 6 atenciones, 6 versiones clinicas y 6 odontogramas fueron validados.';
END $$;

-- 15. SINCRONIZACION DE SECUENCIAS IDENTITY A MAX(ID)
SELECT setval(pg_get_serial_sequence('clinica', 'id'), COALESCE((SELECT MAX(id) FROM clinica), 1));
SELECT setval(pg_get_serial_sequence('usuario', 'id'), COALESCE((SELECT MAX(id) FROM usuario), 1));
SELECT setval(pg_get_serial_sequence('usuario_clinica', 'id'), COALESCE((SELECT MAX(id) FROM usuario_clinica), 1));
SELECT setval(pg_get_serial_sequence('paciente', 'id'), COALESCE((SELECT MAX(id) FROM paciente), 1));
SELECT setval(pg_get_serial_sequence('cita', 'id'), COALESCE((SELECT MAX(id) FROM cita), 1));
SELECT setval(pg_get_serial_sequence('atencion_clinica', 'id'), COALESCE((SELECT MAX(id) FROM atencion_clinica), 1));
SELECT setval(pg_get_serial_sequence('version_atencion_clinica', 'id'), COALESCE((SELECT MAX(id) FROM version_atencion_clinica), 1));
SELECT setval(pg_get_serial_sequence('version_odontograma', 'id'), COALESCE((SELECT MAX(id) FROM version_odontograma), 1));
SELECT setval(pg_get_serial_sequence('hallazgo_odontograma', 'id'), COALESCE((SELECT MAX(id) FROM hallazgo_odontograma), 1));

COMMIT;

-- Final del script
