-- V20: Sincronización oficial del catálogo de 16 incidencias y solicitudes bajo ITIL 4
-- Normaliza los 16 tipos de incidencia distribuidos por rol solicitante y resolutor

INSERT INTO catalogo_incidencia (id, solicitante_rol, titulo, descripcion, tipo_itil, probabilidad, impacto, prioridad, responsable_rol, sla_minutos, ruta_accion, accion_label)
VALUES
    -- ── 1. PACIENTE (6 ítems)
    ('INC-PAC-001', 'PACIENTE', 'Reprogramación urgente de cita odontológica (falta < 2 horas)', 'El paciente solicita cambio de horario de emergencia y no puede esperar a cancelarla en ventanilla.', 'Asistencia', 3, 2, 'ALTO', 'RECEPCIONISTA', 20, '/app/agenda', '📅 Ir a Cita en Agenda'),
    ('INC-PAC-002', 'PACIENTE', 'Corrección de DNI o Nombre por error de digitación en admisión', 'El paciente detecta que sus datos de identidad fueron mal ingresados y por norma legal no puede editarlos él mismo.', 'Asistencia', 2, 2, 'MEDIO', 'ADMIN_CLINICA', 60, '/app/pacientes', '👤 Ir a Ficha de Paciente'),
    ('INC-PAC-003', 'PACIENTE', 'Radiografía o receta médica no visible en portal del paciente', 'El paciente tuvo consulta pero los archivos adjuntos permanecen ocultos en su vista móvil.', 'Incidencia', 2, 2, 'MEDIO', 'ODONTOLOGO', 30, '/app/pacientes', '📁 Ir a Archivos de Paciente'),
    ('INC-PAC-004', 'PACIENTE', 'Falla de conexión, video o audio en sala de Teleodontología', 'No se establece enlace WebRTC o token Jitsi da error en sesión en vivo con paciente conectado.', 'Incidencia', 3, 3, 'CRITICO', 'SOPORTE_TECNICO', 10, '/app/teleodontologia', '📹 Ir a Sala Virtual'),
    ('INC-PAC-005', 'PACIENTE', 'Consulta sobre horarios de atención de fin de semana o precios', 'Consulta informativa previa sobre turnos disponibles y aranceles de la sede Trujillo.', 'Información', 2, 1, 'BAJO', 'RECEPCIONISTA', 120, '/app/agenda', 'ℹ️ Ver Horarios de Sede'),
    ('INC-PAC-006', 'PACIENTE', 'Consulta médica sobre dosificación de antibiótico post-tratamiento', 'Paciente refiere duda clínica urgente sobre posología de medicamentos tras exodoncia o endodoncia.', 'Información', 2, 3, 'ALTO', 'ODONTOLOGO', 30, '/app/pacientes', '🦷 Ver Ficha Clínica'),

    -- ── 2. ODONTOLOGO (4 ítems)
    ('INC-ODO-001', 'ODONTOLOGO', 'Reasignación urgente de Sillón dental por retraso en cirugía previa', 'Procedimiento de extracción compleja se extiende y el siguiente paciente está por ingresar.', 'Incidencia', 2, 3, 'ALTO', 'RECEPCIONISTA', 15, '/app/agenda', '📅 Reubicar Sillón en Agenda'),
    ('INC-ODO-002', 'ODONTOLOGO', 'Error 413 al cargar archivo binario pesado de radiografía (> 25MB)', 'La imagen DICOM o panorámica supera el umbral estándar del servidor y falla la subida.', 'Incidencia', 2, 2, 'MEDIO', 'SOPORTE_TECNICO', 30, '/app/pacientes', '📁 Subida Manual'),
    ('INC-ODO-003', 'ODONTOLOGO', 'Solicitud de adenda médica en historia clínica cerrada por auditoría', 'Necesidad de incorporar observación diagnóstica complementaria posterior a la atención.', 'Asistencia', 1, 2, 'BAJO', 'ADMIN_CLINICA', 120, '/app/pacientes', '🦷 Ver Historia Clínica'),
    ('INC-ODO-004', 'ODONTOLOGO', 'Capacitación técnica en llenado de historia clínica y odontograma digital', 'Inducción para nuevos especialistas en el software odontológico y guardado de piezas dentales.', 'Información', 2, 1, 'BAJO', 'SOPORTE_TECNICO', 120, '/app/pacientes', '🦷 Ver Odontograma'),

    -- ── 3. RECEPCIONISTA (2 ítems)
    ('INC-REC-001', 'RECEPCIONISTA', 'Liberación de turno y sillón por inasistencia física (Paciente No-Show)', 'Paciente no se presentó en tolerancia de 15 minutos; se requiere liberar el sillón y marcar penalidad.', 'Incidencia', 3, 2, 'ALTO', 'RECEPCIONISTA', 15, '/app/agenda', '📅 Gestionar No-Show'),
    ('INC-REC-002', 'RECEPCIONISTA', 'Solicitud de turno de paciente fuera del horario laboral de la sede', 'Paciente insiste en turno nocturno o feriado; requiere autorización administrativa de sede.', 'Información', 2, 1, 'BAJO', 'ADMIN_CLINICA', 120, '/app/agenda', '⚙️ Ver Franja Laboral'),

    -- ── 4. ADMIN_CLINICA (2 ítems)
    ('INC-ADM-001', 'ADMIN_CLINICA', 'Bloqueo urgente de credenciales por desvinculación laboral de personal', 'Cese de contrato de recepcionista u odontólogo con acceso activo a historias clínicas.', 'Incidencia', 1, 3, 'ALTO', 'ADMIN_CLINICA', 15, '/app/consultorio', '👥 Desactivar Credenciales'),
    ('INC-ADM-003', 'ADMIN_CLINICA', 'Solicitud de creación de credenciales de acceso para nueva odontóloga especialista', 'Alta de cuenta, membresía de clínica y perfil RBAC para personal odontológico de nuevo ingreso.', 'Acceso', 2, 2, 'MEDIO', 'ADMIN_CLINICA', 60, '/app/consultorio', '👥 Crear Usuario'),

    -- ── 5. SOPORTE_TECNICO / SUPER_ADMIN (1 ítem)
    ('INC-TI-001', 'SUPER_ADMIN', 'Caída de base de datos o error 500 en microservicios de Railway Cloud', 'Indisponibilidad del backend o lentitud crítica en consultas de facturación y reportes.', 'Incidencia', 1, 3, 'CRITICO', 'SOPORTE_TECNICO', 10, '/app/super-admin', '🖥️ Monitoreo Railway'),

    -- ── 6. CASO GENERAL / OTROS (1 ítem transversal)
    ('INC-GEN-099', 'TODOS', 'Otros requerimientos o fallas no contempladas en el catálogo (Con Captura)', 'Caja de texto libre para detallar el problema y adjuntar captura de pantalla de evidencia.', 'Asistencia', 2, 2, 'MEDIO', 'ADMIN_CLINICA', 60, NULL, '📋 Triaje Administrativo')
ON CONFLICT (id) DO UPDATE SET
    solicitante_rol = EXCLUDED.solicitante_rol,
    titulo = EXCLUDED.titulo,
    descripcion = EXCLUDED.descripcion,
    tipo_itil = EXCLUDED.tipo_itil,
    probabilidad = EXCLUDED.probabilidad,
    impacto = EXCLUDED.impacto,
    prioridad = EXCLUDED.prioridad,
    responsable_rol = EXCLUDED.responsable_rol,
    sla_minutos = EXCLUDED.sla_minutos,
    ruta_accion = EXCLUDED.ruta_accion,
    accion_label = EXCLUDED.accion_label;
