package pe.edu.utp.coronyx.backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    public static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final SolicitudReprogramacionRepository solicitudReprogramacionRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final AusenciaOdontologoRepository ausenciaRepository;
    private final NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              PatientRepository patientRepository,
                              ClinicRepository clinicRepository,
                              ClinicUserRepository clinicUserRepository,
                              SolicitudReprogramacionRepository solicitudReprogramacionRepository,
                              HorarioAtencionRepository horarioRepository,
                              AusenciaOdontologoRepository ausenciaRepository,
                              NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.solicitudReprogramacionRepository = solicitudReprogramacionRepository;
        this.horarioRepository = horarioRepository;
        this.ausenciaRepository = ausenciaRepository;
        this.notificationService = notificationService;
    }

    /**
     * Adquiere bloqueos de asesoría a nivel de transacción de PostgreSQL (pg_advisory_xact_lock)
     * para serializar peticiones concurrentes y garantizar la exclusión mutua de agenda.
     */
    private void acquireConcurrencyLocks(Long odontologoId, Long clinicaId, String consultorio, Long pacienteId) {
        if (odontologoId != null) {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CONCAT('DOCTOR:', ?1))::bigint)")
                    .setParameter(1, odontologoId.toString())
                    .getResultList();
        }
        if (clinicaId != null && consultorio != null && !consultorio.isBlank()) {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CONCAT('CONSULTORIO:', ?1, ':', LOWER(?2)))::bigint)")
                    .setParameter(1, clinicaId.toString())
                    .setParameter(2, consultorio.trim())
                    .getResultList();
        }
        if (pacienteId != null) {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CONCAT('PATIENT:', ?1))::bigint)")
                    .setParameter(1, pacienteId.toString())
                    .getResultList();
        }
    }

    @Transactional
    public AppointmentResponseDto createAppointment(AppointmentRequestDto dto, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        Clinic clinica = principal.getClinic();
        if (dto.getClinicaId() != null && !dto.getClinicaId().isBlank()) {
            if (!dto.getClinicaId().equals(clinica.getId().toString())) {
                throw new AccessDeniedException("Acceso denegado: no tiene autorización para agendar citas en otra clínica");
            }
        }

        // Creador del principal sin fallback
        ClinicUser creadoPor = principal.getMembership();
        if (creadoPor == null) {
            throw new AccessDeniedException("Acceso denegado: no se encontró membresía de clínica activa para el usuario creador.");
        }

        // 1. Validar paciente activo y de la misma clínica
        Long pacienteId;
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            // El paciente no puede suplantar patientId; se resuelve estrictamente del principal
            if (principal.getPatient() == null) {
                throw new AccessDeniedException("Acceso denegado: su cuenta de usuario no tiene una ficha de paciente asociada en esta clínica");
            }
            pacienteId = principal.getPatient().getId();
        } else {
            pacienteId = Long.parseLong(dto.getPacienteId());
        }

        Patient paciente = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));

        if (!paciente.getClinica().getId().equals(clinica.getId())) {
            throw new NoSuchElementException("Paciente no encontrado en la clínica activa");
        }
        if (!"ACTIVO".equalsIgnoreCase(paciente.getEstado())) {
            throw new IllegalStateException("No se puede agendar citas para un paciente en estado " + paciente.getEstado() + ".");
        }

        // 2. Validar odontólogo activo del rol correcto en la misma clínica
        Long odontologoId = Long.parseLong(dto.getOdontologoId());
        ClinicUser odontologo = clinicUserRepository.findById(odontologoId)
                .orElseThrow(() -> new NoSuchElementException("Odontólogo no encontrado con ID: " + dto.getOdontologoId()));

        if (!odontologo.getClinica().getId().equals(clinica.getId())) {
            throw new NoSuchElementException("El odontólogo no pertenece a la clínica activa");
        }
        if (odontologo.getRol() == null || !"ODONTOLOGO".equalsIgnoreCase(odontologo.getRol().getCodigo())) {
            throw new IllegalArgumentException("El usuario seleccionado no cuenta con el rol de ODONTOLOGO");
        }
        if (!"ACTIVO".equalsIgnoreCase(odontologo.getEstado()) ||
                (odontologo.getUsuario() != null && !"ACTIVO".equalsIgnoreCase(odontologo.getUsuario().getEstado()))) {
            throw new IllegalStateException("El odontólogo seleccionado no se encuentra en estado ACTIVO.");
        }

        String consultorio = (dto.getConsultorio() != null && !dto.getConsultorio().isBlank())
                ? dto.getConsultorio().trim() : "Sillón 1";
        String doctorNombre = "Dr. " + odontologo.getUsuario().getNombres() + " " + odontologo.getUsuario().getApellidos();

        // 3. Adquirir bloqueos de concurrencia y validar solapamiento/horarios Lima
        acquireConcurrencyLocks(odontologoId, clinica.getId(), consultorio, pacienteId);

        validateAppointmentSchedule(
                dto.getInicioEn(),
                dto.getFinEn(),
                odontologoId,
                consultorio,
                clinica.getId(),
                pacienteId,
                null,
                doctorNombre
        );

        // 4. Crear y persistir la cita
        Appointment appointment = new Appointment();
        appointment.setClinica(clinica);
        appointment.setPaciente(paciente);
        appointment.setOdontologo(odontologo);
        appointment.setCreadoPor(creadoPor);
        appointment.setInicioEn(dto.getInicioEn());
        appointment.setFinEn(dto.getFinEn());
        appointment.setModalidad(dto.getModalidad() != null ? dto.getModalidad() : "PRESENCIAL");
        appointment.setEstado("PROGRAMADA");
        appointment.setMotivo(dto.getMotivo());
        appointment.setConsultorio(consultorio);
        appointment.setEnlaceTeleconsulta(dto.getEnlaceTeleconsulta());

        Appointment saved = appointmentRepository.save(appointment);

        if (paciente.getUsuario() != null) {
            notificationService.createNotification(
                    clinica,
                    paciente.getUsuario(),
                    "CITA_CREADA",
                    "Cita confirmada",
                    "Su cita ha sido programada para el " + saved.getInicioEn() + " con el " + doctorNombre + "."
            );
        }
        if (odontologo.getUsuario() != null) {
            notificationService.createNotification(
                    clinica,
                    odontologo.getUsuario(),
                    "CITA_ASIGNADA",
                    "Nueva cita asignada",
                    "Se le ha asignado una cita con el paciente " + paciente.getNombres() + " " + paciente.getApellidos() + " para el " + saved.getInicioEn() + "."
            );
        }

        return toDto(saved);
    }

    /**
     * Agendamiento seguro para el portal del paciente (/me/citas) sin exponer pacienteId al cliente.
     */
    @Transactional
    public AppointmentResponseDto createMyAppointment(PatientAppointmentRequestDto dto, UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if (!"PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: este endpoint está reservado para pacientes");
        }
        if (principal.getPatient() == null) {
            throw new AccessDeniedException("Acceso denegado: su cuenta no tiene una ficha de paciente vinculada en esta clínica");
        }

        AppointmentRequestDto req = new AppointmentRequestDto();
        req.setClinicaId(principal.getClinicaId() != null ? principal.getClinicaId().toString() : null);
        req.setPacienteId(principal.getPatient().getId().toString());
        req.setOdontologoId(dto.getOdontologoId());
        req.setInicioEn(dto.getInicioEn());
        req.setFinEn(dto.getFinEn());
        req.setModalidad(dto.getModalidad() != null ? dto.getModalidad() : "PRESENCIAL");
        req.setMotivo(dto.getMotivo());
        req.setConsultorio(dto.getConsultorio());
        req.setEnlaceTeleconsulta(dto.getEnlaceTeleconsulta());

        return createAppointment(req, principal);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getMyAppointments(UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if (!"PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: este endpoint está reservado para pacientes");
        }
        if (principal.getPatient() == null) {
            throw new AccessDeniedException("Acceso denegado: su cuenta no tiene una ficha de paciente vinculada en esta clínica");
        }

        return appointmentRepository.findByPacienteIdOrderByInicioEnDesc(principal.getPatient().getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByRange(Long requestedClinicaId,
                                                               OffsetDateTime desde,
                                                               OffsetDateTime hasta,
                                                               UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente debe consultar su propio historial a través de /me/citas");
        }

        Long userClinicId = principal.getClinicaId();
        if (requestedClinicaId != null && !requestedClinicaId.equals(userClinicId)) {
            throw new AccessDeniedException("Acceso denegado: no tiene autorización para consultar citas de otra clínica");
        }

        OffsetDateTime start = (desde != null) ? desde : OffsetDateTime.now(LIMA_ZONE).minusDays(1);
        OffsetDateTime end = (hasta != null) ? hasta : OffsetDateTime.now(LIMA_ZONE).plusDays(30);

        return appointmentRepository.findByClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(userClinicId, start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByOdontologo(Long odontologoId,
                                                                    OffsetDateTime desde,
                                                                    OffsetDateTime hasta,
                                                                    UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no tiene acceso a la agenda global del odontólogo");
        }

        // Si es odontólogo, solo puede consultar su propia agenda
        if ("ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            if (principal.getUsuarioClinicaId() == null || !principal.getUsuarioClinicaId().equals(odontologoId)) {
                throw new AccessDeniedException("Acceso denegado: un odontólogo solo puede consultar su propia agenda clínica");
            }
        }

        ClinicUser odontologo = clinicUserRepository.findById(odontologoId)
                .orElseThrow(() -> new NoSuchElementException("Odontólogo no encontrado con ID: " + odontologoId));

        if (!odontologo.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("El odontólogo no pertenece a la clínica activa");
        }

        OffsetDateTime start = (desde != null) ? desde : OffsetDateTime.now(LIMA_ZONE).minusDays(1);
        OffsetDateTime end = (hasta != null) ? hasta : OffsetDateTime.now(LIMA_ZONE).plusDays(30);

        return appointmentRepository.findByOdontologoIdAndClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(
                        odontologoId, principal.getClinicaId(), start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByPaciente(Long pacienteId, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPatient() == null || !principal.getPatient().getId().equals(pacienteId)) {
                throw new AccessDeniedException("Acceso denegado: no tiene autorización para consultar citas de otro paciente");
            }
        } else {
            Patient paciente = patientRepository.findById(pacienteId)
                    .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));
            if (!paciente.getClinica().getId().equals(principal.getClinicaId())) {
                throw new NoSuchElementException("Paciente no encontrado en esta clínica");
            }
        }

        return appointmentRepository.findByPacienteIdOrderByInicioEnDesc(pacienteId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDto getAppointmentById(Long id, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        if (!appointment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Cita no encontrada con ID: " + id);
        }

        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPatient() == null || !appointment.getPaciente().getId().equals(principal.getPatient().getId())) {
                throw new AccessDeniedException("Acceso denegado: no tiene autorización para consultar citas de otro paciente");
            }
        }

        return toDto(appointment);
    }

    @Transactional
    public AppointmentResponseDto updateEstado(Long id, AppointmentEstadoDto dto, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        if (!appointment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Cita no encontrada con ID: " + id);
        }

        String nuevoEstado = dto.getEstado().toUpperCase().trim();

        // Control de paciente: el paciente solo puede confirmar intención de asistencia (CONFIRMADA) o cancelar (CANCELADA)
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPatient() == null || !appointment.getPaciente().getId().equals(principal.getPatient().getId())) {
                throw new AccessDeniedException("Acceso denegado: no puede modificar citas de otro paciente");
            }
            if (!"CANCELADA".equals(nuevoEstado) && !"CONFIRMADA".equals(nuevoEstado)) {
                throw new AccessDeniedException("Acceso denegado: un paciente solo puede confirmar su asistencia o cancelar su cita");
            }
        }

        // Si se está reactivando una cita cancelada a estado activo, verificar concurrencia
        if ("CANCELADA".equalsIgnoreCase(appointment.getEstado()) && !"CANCELADA".equalsIgnoreCase(nuevoEstado)) {
            acquireConcurrencyLocks(
                    appointment.getOdontologo().getId(),
                    appointment.getClinica().getId(),
                    appointment.getConsultorio(),
                    appointment.getPaciente().getId()
            );

            String doctorNombre = "Dr. " + appointment.getOdontologo().getUsuario().getNombres();
            validateAppointmentSchedule(
                    appointment.getInicioEn(),
                    appointment.getFinEn(),
                    appointment.getOdontologo().getId(),
                    appointment.getConsultorio(),
                    appointment.getClinica().getId(),
                    appointment.getPaciente().getId(),
                    appointment.getId(),
                    doctorNombre
            );
        }

        appointment.setEstado(nuevoEstado);

        if ("CANCELADA".equals(nuevoEstado) && dto.getMotivoCancelacion() != null) {
            String motivoActual = appointment.getMotivo() != null ? appointment.getMotivo() : "";
            appointment.setMotivo(motivoActual + " [Cancelación: " + dto.getMotivoCancelacion() + "]");
        }

        Appointment updated = appointmentRepository.save(appointment);

        if ("CANCELADA".equals(nuevoEstado)) {
            if (updated.getPaciente() != null && updated.getPaciente().getUsuario() != null) {
                notificationService.createNotification(
                        updated.getClinica(),
                        updated.getPaciente().getUsuario(),
                        "CITA_CANCELADA",
                        "Cita cancelada",
                        "Su cita del " + updated.getInicioEn() + " ha sido cancelada."
                );
            }
        }

        return toDto(updated);
    }

    @Transactional
    public AppointmentResponseDto updateAsistencia(Long id, AppointmentAsistenciaDto dto, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        // Estricto: un paciente NO puede registrar asistencia física (ASISTIO / NO_SHOW)
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: el control de asistencia física está reservado al personal clínico y de recepción");
        }

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        if (!appointment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Cita no encontrada con ID: " + id);
        }

        String asistencia = dto.getEstadoAsistencia().toUpperCase().trim();
        if (!"ASISTIO".equals(asistencia) && !"NO_SHOW".equals(asistencia)) {
            throw new IllegalArgumentException("El estado de asistencia debe ser ASISTIO o NO_SHOW");
        }

        appointment.setEstadoAsistencia(asistencia);

        if ("ASISTIO".equals(asistencia)) {
            appointment.setEstado("FINALIZADA");
        } else if ("NO_SHOW".equals(asistencia)) {
            appointment.setEstado("NO_ASISTIO");
        }

        Appointment updated = appointmentRepository.save(appointment);
        return toDto(updated);
    }

    @Transactional
    public AppointmentResponseDto rescheduleAppointment(Long id,
                                                         OffsetDateTime nuevoInicio,
                                                         OffsetDateTime nuevoFin,
                                                         UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        // Reprogramación directa reservada para personal administrativo y clínico.
        // Los pacientes deben utilizar el flujo de solicitudes de reprogramación.
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: los pacientes deben solicitar reprogramación mediante el flujo de solicitudes");
        }

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        if (!appointment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Cita no encontrada con ID: " + id);
        }

        String doctorNombre = (appointment.getOdontologo() != null && appointment.getOdontologo().getUsuario() != null)
                ? "Dr. " + appointment.getOdontologo().getUsuario().getNombres() + " " + appointment.getOdontologo().getUsuario().getApellidos()
                : "el odontólogo asignado";

        // Adquirir bloqueos de concurrencia y validar reglas sobre nuevo horario
        acquireConcurrencyLocks(
                appointment.getOdontologo().getId(),
                appointment.getClinica().getId(),
                appointment.getConsultorio(),
                appointment.getPaciente().getId()
        );

        validateAppointmentSchedule(
                nuevoInicio,
                nuevoFin,
                appointment.getOdontologo().getId(),
                appointment.getConsultorio(),
                appointment.getClinica().getId(),
                appointment.getPaciente().getId(),
                appointment.getId(),
                doctorNombre
        );

        appointment.setInicioEn(nuevoInicio);
        appointment.setFinEn(nuevoFin);
        appointment.setEstado("REPROGRAMADA");

        Appointment updated = appointmentRepository.save(appointment);

        if (updated.getPaciente() != null && updated.getPaciente().getUsuario() != null) {
            notificationService.createNotification(
                    updated.getClinica(),
                    updated.getPaciente().getUsuario(),
                    "CITA_REPROGRAMADA",
                    "Cita reprogramada",
                    "Su cita ha sido reprogramada para el " + updated.getInicioEn() + "."
            );
        }

        return toDto(updated);
    }

    // ── Solicitudes de Reprogramación (Persistidas y Atómicas) ─────────────────

    @Transactional
    public SolicitudReprogramacionResponseDto solicitarReprogramacion(Long citaId,
                                                                      CreateSolicitudReprogramacionDto dto,
                                                                      UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        Appointment appointment = appointmentRepository.findById(citaId)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + citaId));

        if (!appointment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Cita no encontrada con ID: " + citaId);
        }

        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPatient() == null || !appointment.getPaciente().getId().equals(principal.getPatient().getId())) {
                throw new AccessDeniedException("Acceso denegado: no puede solicitar reprogramar citas de otro paciente");
            }
        }

        if ("CANCELADA".equalsIgnoreCase(appointment.getEstado()) || "FINALIZADA".equalsIgnoreCase(appointment.getEstado())) {
            throw new IllegalStateException("No se puede solicitar reprogramación para una cita en estado " + appointment.getEstado());
        }

        // Validar propuesta en horario Lima
        validateProposedHoursLima(dto.getNuevaFechaHoraInicio(), dto.getNuevaFechaHoraFin());

        SolicitudReprogramacion solicitud = new SolicitudReprogramacion();
        solicitud.setCita(appointment);
        solicitud.setSolicitanteUsuario(principal.getUser());
        solicitud.setNuevaFechaHoraInicio(dto.getNuevaFechaHoraInicio());
        solicitud.setNuevaFechaHoraFin(dto.getNuevaFechaHoraFin());
        solicitud.setMotivo(dto.getMotivo());
        solicitud.setEstado("PENDIENTE");

        SolicitudReprogramacion saved = solicitudReprogramacionRepository.save(solicitud);
        return toSolicitudDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SolicitudReprogramacionResponseDto> getSolicitudesReprogramacion(Long clinicaId,
                                                                                String estado,
                                                                                UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: use /me/citas/solicitudes para consultar sus propias solicitudes");
        }

        Long targetClinicId = principal.getClinicaId();
        return solicitudReprogramacionRepository.findByClinicaIdAndEstado(targetClinicId, estado != null && !estado.isBlank() ? estado.toUpperCase() : null)
                .stream()
                .map(this::toSolicitudDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SolicitudReprogramacionResponseDto> getMySolicitudesReprogramacion(UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if (!"PACIENTE".equalsIgnoreCase(principal.getRol()) || principal.getPatient() == null) {
            throw new AccessDeniedException("Acceso denegado: endpoint exclusivo para pacientes");
        }

        return solicitudReprogramacionRepository.findByPacienteIdOrderByFechaSolicitudDesc(principal.getPatient().getId())
                .stream()
                .map(this::toSolicitudDto)
                .collect(Collectors.toList());
    }

    /**
     * Resolución atómica de solicitud de reprogramación.
     * Si es APROBADA: adquiere bloqueos, valida solapamientos, actualiza cita y solicitud en una sola transacción.
     */
    @Transactional
    public SolicitudReprogramacionResponseDto resolverSolicitudReprogramacion(Long solicitudId,
                                                                              ResolverSolicitudReprogramacionDto dto,
                                                                              UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el personal autorizado de la clínica puede resolver solicitudes");
        }

        SolicitudReprogramacion solicitud = solicitudReprogramacionRepository.findById(solicitudId)
                .orElseThrow(() -> new NoSuchElementException("Solicitud de reprogramación no encontrada con ID: " + solicitudId));

        Appointment cita = solicitud.getCita();
        if (!cita.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Solicitud no encontrada en esta clínica");
        }

        if (!"PENDIENTE".equalsIgnoreCase(solicitud.getEstado())) {
            throw new IllegalStateException("La solicitud ya fue procesada y se encuentra en estado: " + solicitud.getEstado());
        }

        String resolucion = dto.getEstado().toUpperCase().trim();

        if ("APROBADA".equals(resolucion)) {
            // Adquirir bloqueos para garantizar atomicidad y evitar race conditions
            acquireConcurrencyLocks(
                    cita.getOdontologo().getId(),
                    cita.getClinica().getId(),
                    cita.getConsultorio(),
                    cita.getPaciente().getId()
            );

            String doctorNombre = "Dr. " + cita.getOdontologo().getUsuario().getNombres() + " " + cita.getOdontologo().getUsuario().getApellidos();

            validateAppointmentSchedule(
                    solicitud.getNuevaFechaHoraInicio(),
                    solicitud.getNuevaFechaHoraFin(),
                    cita.getOdontologo().getId(),
                    cita.getConsultorio(),
                    cita.getClinica().getId(),
                    cita.getPaciente().getId(),
                    cita.getId(),
                    doctorNombre
            );

            // Modificar cita
            cita.setInicioEn(solicitud.getNuevaFechaHoraInicio());
            cita.setFinEn(solicitud.getNuevaFechaHoraFin());
            cita.setEstado("REPROGRAMADA");
            appointmentRepository.save(cita);

            solicitud.setEstado("APROBADA");
        } else {
            solicitud.setEstado("RECHAZADA");
        }

        solicitud.setResolucionUsuario(principal.getUser());
        solicitud.setResolucionMotivo(dto.getMotivoResolucion());
        solicitud.setFechaResolucion(OffsetDateTime.now());

        SolicitudReprogramacion updated = solicitudReprogramacionRepository.save(solicitud);

        if (updated.getSolicitanteUsuario() != null) {
            notificationService.createNotification(
                    cita.getClinica(),
                    updated.getSolicitanteUsuario(),
                    "SOLICITUD_REPROGRAMACION_" + resolucion,
                    "Solicitud de reprogramación " + resolucion.toLowerCase(),
                    "Su solicitud de reprogramación para la cita ha sido " + resolucion.toLowerCase() + "."
            );
        }

        return toSolicitudDto(updated);
    }

    @Transactional
    public SolicitudReprogramacionResponseDto cancelarSolicitudReprogramacion(Long solicitudId, UserPrincipal principal) {
        validateSecurityPrincipal(principal);

        SolicitudReprogramacion solicitud = solicitudReprogramacionRepository.findById(solicitudId)
                .orElseThrow(() -> new NoSuchElementException("Solicitud de reprogramación no encontrada con ID: " + solicitudId));

        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (!solicitud.getSolicitanteUsuario().getId().equals(principal.getUser().getId())) {
                throw new AccessDeniedException("Acceso denegado: solo el solicitante puede cancelar su solicitud");
            }
        }

        if (!"PENDIENTE".equalsIgnoreCase(solicitud.getEstado())) {
            throw new IllegalStateException("Solo se pueden cancelar solicitudes en estado PENDIENTE.");
        }

        solicitud.setEstado("CANCELADA");
        solicitud.setFechaResolucion(OffsetDateTime.now());

        SolicitudReprogramacion updated = solicitudReprogramacionRepository.save(solicitud);
        return toSolicitudDto(updated);
    }

    // ── Validaciones de Horario y Reglas de Negocio en Hora Lima ───────────────

    private void validateProposedHoursLima(OffsetDateTime inicioEn, OffsetDateTime finEn) {
        if (inicioEn == null || finEn == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias.");
        }
        if (!finEn.isAfter(inicioEn)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
        }

        ZonedDateTime inicioLima = inicioEn.atZoneSameInstant(LIMA_ZONE);
        ZonedDateTime finLima = finEn.atZoneSameInstant(LIMA_ZONE);

        if (!inicioLima.toLocalDate().equals(finLima.toLocalDate())) {
            throw new IllegalArgumentException("La cita no puede abarcar múltiples días calendario.");
        }

        ZonedDateTime ahoraLima = ZonedDateTime.now(LIMA_ZONE);
        if (inicioLima.isBefore(ahoraLima.minusMinutes(10))) {
            throw new IllegalArgumentException("No es posible programar citas en fechas u horas pasadas.");
        }
        if (inicioLima.isAfter(ahoraLima.plusDays(90))) {
            throw new IllegalArgumentException("No se pueden agendar citas con más de 90 días de anticipación.");
        }

        long duracionMinutos = Duration.between(inicioLima, finLima).toMinutes();
        if (duracionMinutos < 15 || duracionMinutos > 180) {
            throw new IllegalArgumentException("La duración de la cita debe estar entre 15 y 180 minutos.");
        }
    }

    private void validateAppointmentSchedule(
            OffsetDateTime inicioEn,
            OffsetDateTime finEn,
            Long odontologoId,
            String consultorio,
            Long clinicaId,
            Long pacienteId,
            Long excludeAppointmentId,
            String doctorNombre) {

        validateProposedHoursLima(inicioEn, finEn);

        ZonedDateTime inicioLima = inicioEn.atZoneSameInstant(LIMA_ZONE);
        ZonedDateTime finLima = finEn.atZoneSameInstant(LIMA_ZONE);
        LocalTime horaInicio = inicioLima.toLocalTime();
        LocalTime horaFin = finLima.toLocalTime();
        int diaSemana = inicioLima.getDayOfWeek().getValue();

        // 1. Validar horario configurado en BD (A09 / R05)
        var horarioOpt = (odontologoId != null)
                ? horarioRepository.findDoctorScheduleForDay(odontologoId, diaSemana)
                : java.util.Optional.<HorarioAtencion>empty();
        if (horarioOpt.isEmpty() && clinicaId != null) {
            horarioOpt = horarioRepository.findClinicScheduleForDay(clinicaId, diaSemana);
        }

        if (horarioOpt.isPresent()) {
            HorarioAtencion h = horarioOpt.get();
            if (!Boolean.TRUE.equals(h.getActivo())) {
                throw new IllegalStateException("El día seleccionado no es laborable según la configuración de horarios.");
            }
            if (horaInicio.isBefore(h.getHoraApertura()) || horaFin.isAfter(h.getHoraCierre())) {
                throw new IllegalStateException("La cita está fuera del horario de atención configurado (" +
                        h.getHoraApertura() + " a " + h.getHoraCierre() + ").");
            }
        } else {
            DayOfWeek dia = inicioLima.getDayOfWeek();
            if (dia == DayOfWeek.SUNDAY) {
                throw new IllegalArgumentException("La clínica dental no atiende los días domingo.");
            }
            if (dia == DayOfWeek.SATURDAY) {
                if (horaInicio.isBefore(LocalTime.of(8, 0)) || horaFin.isAfter(LocalTime.of(13, 0))) {
                    throw new IllegalArgumentException("Los días sábado el horario de atención es de 08:00 a 13:00.");
                }
            } else {
                if (horaInicio.isBefore(LocalTime.of(8, 0)) || horaFin.isAfter(LocalTime.of(18, 0))) {
                    throw new IllegalArgumentException("El horario de atención es de 08:00 a 18:00 (Lunes a Viernes).");
                }
            }
        }

        // 2. Ausencia médica del especialista (A09 / R05)
        if (odontologoId != null && ausenciaRepository.existsOverlappingAusencia(odontologoId, inicioEn, finEn)) {
            throw new IllegalStateException("El " + doctorNombre + " tiene una ausencia registrada (licencia o vacaciones) en el horario solicitado.");
        }

        // 1. Conflicto de Odontólogo
        boolean conflictoDoctor = appointmentRepository.existsOverlapping(
                odontologoId, inicioEn, finEn, excludeAppointmentId);
        if (conflictoDoctor) {
            throw new IllegalStateException("Conflicto de agenda: " + doctorNombre + " ya tiene una cita activa en este rango de horario.");
        }

        // 2. Conflicto de Consultorio
        if (consultorio != null && !consultorio.isBlank()) {
            boolean conflictoConsultorio = appointmentRepository.existsConsultorioOverlapping(
                    clinicaId, consultorio, inicioEn, finEn, excludeAppointmentId);
            if (conflictoConsultorio) {
                throw new IllegalStateException("El consultorio '" + consultorio + "' ya se encuentra ocupado en este horario por otra cita.");
            }
        }

        // 3. Conflicto de Paciente
        if (pacienteId != null) {
            boolean conflictoPaciente = appointmentRepository.existsPacienteOverlapping(
                    pacienteId, inicioEn, finEn, excludeAppointmentId);
            if (conflictoPaciente) {
                throw new IllegalStateException("El paciente ya cuenta con otra cita programada en este mismo horario.");
            }
        }
    }

    private void validateSecurityPrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin()) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo para agendar o gestionar citas");
        }
        if (principal.getClinic() == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica operativa asignada");
        }
    }

    public AppointmentResponseDto toDto(Appointment a) {
        AppointmentResponseDto dto = new AppointmentResponseDto();
        dto.setId(a.getId() != null ? a.getId().toString() : null);

        if (a.getClinica() != null) {
            dto.setClinicaId(a.getClinica().getId() != null ? a.getClinica().getId().toString() : null);
            dto.setClinicaNombre(a.getClinica().getNombre());
        }

        if (a.getPaciente() != null) {
            dto.setPacienteId(a.getPaciente().getId() != null ? a.getPaciente().getId().toString() : null);
            dto.setPacienteNombreCompleto(a.getPaciente().getNombres() + " " + a.getPaciente().getApellidos());
            dto.setPacienteNumeroDocumento(a.getPaciente().getNumeroDocumento());
            dto.setPacienteTelefono(a.getPaciente().getTelefono());
        }

        if (a.getOdontologo() != null) {
            dto.setOdontologoId(a.getOdontologo().getId() != null ? a.getOdontologo().getId().toString() : null);
            if (a.getOdontologo().getUsuario() != null) {
                dto.setOdontologoNombreCompleto("Dr. " + a.getOdontologo().getUsuario().getNombres() + " " + a.getOdontologo().getUsuario().getApellidos());
                dto.setOdontologoCorreo(a.getOdontologo().getUsuario().getCorreo());
            }
        }

        if (a.getCreadoPor() != null) {
            dto.setCreadoPorId(a.getCreadoPor().getId() != null ? a.getCreadoPor().getId().toString() : null);
            if (a.getCreadoPor().getUsuario() != null) {
                dto.setCreadoPorNombreCompleto(a.getCreadoPor().getUsuario().getNombres() + " " + a.getCreadoPor().getUsuario().getApellidos());
            }
        }

        dto.setInicioEn(a.getInicioEn());
        dto.setFinEn(a.getFinEn());
        dto.setModalidad(a.getModalidad());
        dto.setEstado(a.getEstado());
        dto.setEstadoAsistencia(a.getEstadoAsistencia());
        dto.setMotivo(a.getMotivo());
        dto.setConsultorio(a.getConsultorio());
        dto.setEnlaceTeleconsulta(a.getEnlaceTeleconsulta());
        dto.setFechaCreacion(a.getFechaCreacion());
        dto.setFechaActualizacion(a.getFechaActualizacion());
        return dto;
    }

    public SolicitudReprogramacionResponseDto toSolicitudDto(SolicitudReprogramacion s) {
        SolicitudReprogramacionResponseDto dto = new SolicitudReprogramacionResponseDto();
        dto.setId(s.getId() != null ? s.getId().toString() : null);

        if (s.getCita() != null) {
            dto.setCitaId(s.getCita().getId() != null ? s.getCita().getId().toString() : null);
            dto.setFechaActualCitaInicio(s.getCita().getInicioEn());
            dto.setFechaActualCitaFin(s.getCita().getFinEn());

            if (s.getCita().getPaciente() != null) {
                dto.setPacienteId(s.getCita().getPaciente().getId() != null ? s.getCita().getPaciente().getId().toString() : null);
                dto.setPacienteNombreCompleto(s.getCita().getPaciente().getNombres() + " " + s.getCita().getPaciente().getApellidos());
            }
            if (s.getCita().getOdontologo() != null && s.getCita().getOdontologo().getUsuario() != null) {
                dto.setOdontologoId(s.getCita().getOdontologo().getId() != null ? s.getCita().getOdontologo().getId().toString() : null);
                dto.setOdontologoNombreCompleto("Dr. " + s.getCita().getOdontologo().getUsuario().getNombres() + " " + s.getCita().getOdontologo().getUsuario().getApellidos());
            }
        }

        if (s.getSolicitanteUsuario() != null) {
            dto.setSolicitanteUsuarioId(s.getSolicitanteUsuario().getId() != null ? s.getSolicitanteUsuario().getId().toString() : null);
            dto.setSolicitanteNombreCompleto(s.getSolicitanteUsuario().getNombres() + " " + s.getSolicitanteUsuario().getApellidos());
        }

        dto.setNuevaFechaHoraInicio(s.getNuevaFechaHoraInicio());
        dto.setNuevaFechaHoraFin(s.getNuevaFechaHoraFin());
        dto.setMotivo(s.getMotivo());
        dto.setEstado(s.getEstado());

        if (s.getResolucionUsuario() != null) {
            dto.setResolucionUsuarioId(s.getResolucionUsuario().getId() != null ? s.getResolucionUsuario().getId().toString() : null);
            dto.setResolucionUsuarioNombre(s.getResolucionUsuario().getNombres() + " " + s.getResolucionUsuario().getApellidos());
        }

        dto.setResolucionMotivo(s.getResolucionMotivo());
        dto.setFechaSolicitud(s.getFechaSolicitud());
        dto.setFechaResolucion(s.getFechaResolucion());
        return dto;
    }

    /**
     * Catálogo asistencial de odontólogos activos de la clínica del usuario autenticado.
     * Permite a RECEPCIONISTA, ODONTOLOGO, PACIENTE y ADMIN_CLINICA consultar doctores para citas.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getClinicDentists(UserPrincipal principal) {
        validateSecurityPrincipal(principal);
        Long clinicaId = principal.getClinicaId();
        return clinicUserRepository.findByClinicaId(clinicaId).stream()
                .filter(m -> m.getRol() != null && "ODONTOLOGO".equalsIgnoreCase(m.getRol().getCodigo()))
                .filter(m -> "ACTIVO".equalsIgnoreCase(m.getEstado()) &&
                        (m.getUsuario() == null || "ACTIVO".equalsIgnoreCase(m.getUsuario().getEstado())))
                .map(m -> {
                    User user = m.getUsuario();
                    UserDto dto = new UserDto();
                    dto.setId(user != null ? user.getId().toString() : m.getId().toString());
                    dto.setUsuarioClinicaId(m.getId().toString());
                    dto.setCorreo(user != null ? user.getCorreo() : "");
                    dto.setNombres(user != null ? user.getNombres() : "Odontólogo");
                    dto.setApellidos(user != null ? user.getApellidos() : "");
                    dto.setEstado(m.getEstado());
                    dto.setRol("ODONTOLOGO");
                    dto.setClinicaNombre(m.getClinica() != null ? m.getClinica().getNombre() : "");
                    return dto;
                })
                .toList();
    }
}

