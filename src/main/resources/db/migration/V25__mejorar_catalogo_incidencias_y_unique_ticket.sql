-- V25: Agregar restricción UNIQUE explícita al id INT de mesa_ayuda_ticket y catalogo_incidencia,
-- y actualizar los incidentes 1, 5, 6, 11 y 12 según recomendación docente.

DO $$
BEGIN
    -- 1. Agregar restricción UNIQUE explícita en mesa_ayuda_ticket(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_mesa_ayuda_ticket_id'
    ) THEN
        ALTER TABLE mesa_ayuda_ticket
            ADD CONSTRAINT uq_mesa_ayuda_ticket_id UNIQUE (id);
    END IF;

    -- 2. Agregar restricción UNIQUE explícita en catalogo_incidencia(id)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_catalogo_incidencia_id'
    ) THEN
        ALTER TABLE catalogo_incidencia
            ADD CONSTRAINT uq_catalogo_incidencia_id UNIQUE (id);
    END IF;
END $$;

-- 3. Actualizar Incidente 1 (INC-PAC-001): Reprogramación con 24 horas de anticipación
UPDATE catalogo_incidencia
SET titulo = 'Reprogramación de cita odontológica por imprevisto (con 24 horas de anticipación)',
    descripcion = 'El paciente solicita cambiar la fecha u hora de su cita programada dentro del plazo de 24 horas previas al turno.',
    tipo_itil = 'Asistencia',
    impacto = 3,
    probabilidad = 2,
    prioridad = 'ALTO',
    responsable_rol = 'RECEPCIONISTA',
    sla_minutos = 20,
    ruta_accion = '/app/agenda',
    accion_label = '📅 Ir a Cita en Agenda'
WHERE id = 'INC-PAC-001';

-- 4. Actualizar Incidente 5 (INC-PAC-005): Bloqueo de cuenta o error de contraseña en Portal del Paciente
UPDATE catalogo_incidencia
SET titulo = 'Bloqueo de cuenta o error de contraseña al ingresar al Portal del Paciente',
    descripcion = 'El paciente no puede acceder a su portal personal por bloqueo de credenciales y requiere restablecimiento de acceso.',
    tipo_itil = 'Acceso',
    impacto = 2,
    probabilidad = 2,
    prioridad = 'MEDIO',
    responsable_rol = 'ADMIN_CLINICA',
    sla_minutos = 30,
    ruta_accion = '/app/consultorio',
    accion_label = '🔑 Restablecer Acceso'
WHERE id = 'INC-PAC-005';

-- 5. Actualizar Incidente 6 (INC-PAC-006): Omisión o registro incorrecto de alergia a medicamentos en Historia Clínica
UPDATE catalogo_incidencia
SET titulo = 'Omisión o registro incorrecto de alergia a medicamentos en la Historia Clínica',
    descripcion = 'El paciente detecta que su alergia medicamentosa o antecedente médico crítico no figura en su ficha clínica.',
    tipo_itil = 'Incidencia',
    impacto = 3,
    probabilidad = 2,
    prioridad = 'ALTO',
    responsable_rol = 'ODONTOLOGO',
    sla_minutos = 20,
    ruta_accion = '/app/pacientes',
    accion_label = '🦷 Actualizar Alergia en Ficha'
WHERE id = 'INC-PAC-006';

-- 6. Actualizar Incidente 11 (INC-REC-001): Anulación urgente de cargo o cobro con monto erróneo en Caja
UPDATE catalogo_incidencia
SET titulo = 'Anulación urgente de cargo o cobro registrado con monto erróneo en Caja',
    descripcion = 'Recepción registró un cobro con importe equivocado y requiere que Administración ejecute la anulación autorizada.',
    tipo_itil = 'Incidencia',
    impacto = 3,
    probabilidad = 2,
    prioridad = 'ALTO',
    responsable_rol = 'ADMIN_CLINICA',
    sla_minutos = 15,
    ruta_accion = '/app/caja',
    accion_label = '💳 Ir a Caja y Anular'
WHERE id = 'INC-REC-001';

-- 7. Actualizar Incidente 12 (INC-REC-002): Bloqueo urgente de agenda por ausencia médica imprevista del odontólogo
UPDATE catalogo_incidencia
SET titulo = 'Bloqueo urgente de agenda por ausencia o emergencia médica del odontólogo de turno',
    descripcion = 'El odontólogo reporta emergencia imprevista; Recepción solicita bloquear su disponibilidad y reprogramar pacientes.',
    tipo_itil = 'Incidencia',
    impacto = 3,
    probabilidad = 2,
    prioridad = 'ALTO',
    responsable_rol = 'ADMIN_CLINICA',
    sla_minutos = 15,
    ruta_accion = '/app/consultorio',
    accion_label = '⚙️ Registrar Ausencia Médica'
WHERE id = 'INC-REC-002';

-- 8. Sincronizar los títulos de los tickets existentes en mesa_ayuda_ticket que apuntan a estos códigos
UPDATE mesa_ayuda_ticket
SET solicitud = 'Reprogramación de cita odontológica por imprevisto (con 24 horas de anticipación)'
WHERE catalogo_id = 'INC-PAC-001';

UPDATE mesa_ayuda_ticket
SET solicitud = 'Bloqueo de cuenta o error de contraseña al ingresar al Portal del Paciente',
    prioridad = 'MEDIO',
    impacto = 2,
    probabilidad = 2,
    responsable_rol = 'ADMIN_CLINICA',
    responsable_nombre = 'Carlos Administrador'
WHERE catalogo_id = 'INC-PAC-005';

UPDATE mesa_ayuda_ticket
SET solicitud = 'Omisión o registro incorrecto de alergia a medicamentos en la Historia Clínica',
    prioridad = 'ALTO',
    impacto = 3,
    probabilidad = 2,
    responsable_rol = 'ODONTOLOGO',
    responsable_nombre = 'Dr. Andrés Herrera'
WHERE catalogo_id = 'INC-PAC-006';

UPDATE mesa_ayuda_ticket
SET solicitud = 'Anulación urgente de cargo o cobro registrado con monto erróneo en Caja',
    prioridad = 'ALTO',
    impacto = 3,
    probabilidad = 2,
    responsable_rol = 'ADMIN_CLINICA',
    responsable_nombre = 'Carlos Administrador'
WHERE catalogo_id = 'INC-REC-001';

UPDATE mesa_ayuda_ticket
SET solicitud = 'Bloqueo urgente de agenda por ausencia o emergencia médica del odontólogo de turno',
    prioridad = 'ALTO',
    impacto = 3,
    probabilidad = 2,
    responsable_rol = 'ADMIN_CLINICA',
    responsable_nombre = 'Carlos Administrador'
WHERE catalogo_id = 'INC-REC-002';

-- 9. Asegurar que existan los tickets 1, 2 y 3 si fueron eliminados en pruebas previas para completar los 16 tickets (1..16)
INSERT INTO mesa_ayuda_ticket (
    id, clinica_id, catalogo_id, solicitud, solicitante_nombre, solicitante_rol,
    responsable_nombre, responsable_rol, probabilidad, impacto, prioridad,
    estado, tiempo_transcurrido, evidencia, fecha_registro, fecha_resolucion
)
SELECT 1, (SELECT id FROM clinica ORDER BY id LIMIT 1), 'INC-PAC-004',
       'Falla de conexión, video o audio en sala de Teleodontología',
       'Luis Francisco Gamboa Velasquez', 'PACIENTE',
       'Super Admin', 'SOPORTE_TECNICO', 3, 3, 'CRITICO',
       'ATENDIDO', '8 min', 'Se restableció la sesión de videollamada y se verificó el enlace con el paciente.',
       CURRENT_TIMESTAMP - INTERVAL '3 days 28 minutes', CURRENT_TIMESTAMP - INTERVAL '3 days 20 minutes'
WHERE NOT EXISTS (SELECT 1 FROM mesa_ayuda_ticket WHERE id = 1);

INSERT INTO mesa_ayuda_ticket (
    id, clinica_id, catalogo_id, solicitud, solicitante_nombre, solicitante_rol,
    responsable_nombre, responsable_rol, probabilidad, impacto, prioridad,
    estado, tiempo_transcurrido, evidencia, fecha_registro, fecha_resolucion
)
SELECT 2, (SELECT id FROM clinica ORDER BY id LIMIT 1), 'INC-ODO-001',
       'Reasignación urgente de Sillón dental por retraso en cirugía previa',
       'Dr. Andrés Herrera', 'ODONTOLOGO',
       'Paula Suárez', 'RECEPCIONISTA', 3, 2, 'ALTO',
       'ATENDIDO', '11 min', 'Se reubicó el turno siguiente en el Consultorio 2 disponible.',
       CURRENT_TIMESTAMP - INTERVAL '2 days 45 minutes', CURRENT_TIMESTAMP - INTERVAL '2 days 34 minutes'
WHERE NOT EXISTS (SELECT 1 FROM mesa_ayuda_ticket WHERE id = 2);

INSERT INTO mesa_ayuda_ticket (
    id, clinica_id, catalogo_id, solicitud, solicitante_nombre, solicitante_rol,
    responsable_nombre, responsable_rol, probabilidad, impacto, prioridad,
    estado, tiempo_transcurrido, evidencia, fecha_registro, fecha_resolucion
)
SELECT 3, (SELECT id FROM clinica ORDER BY id LIMIT 1), 'INC-REC-001',
       'Anulación urgente de cargo o cobro registrado con monto erróneo en Caja',
       'Paula Suárez', 'RECEPCIONISTA',
       'Carlos Administrador', 'ADMIN_CLINICA', 2, 3, 'ALTO',
       'ATENDIDO', '14 min', 'Se anuló el cargo duplicado en el módulo de Caja y se registró el importe correcto.',
       CURRENT_TIMESTAMP - INTERVAL '2 days 20 minutes', CURRENT_TIMESTAMP - INTERVAL '2 days 6 minutes'
WHERE NOT EXISTS (SELECT 1 FROM mesa_ayuda_ticket WHERE id = 3);

