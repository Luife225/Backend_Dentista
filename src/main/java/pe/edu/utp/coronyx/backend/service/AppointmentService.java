package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.model.Appointment;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;

import java.time.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final ClinicUserRepository clinicUserRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              PatientRepository patientRepository,
                              ClinicRepository clinicRepository,
                              ClinicUserRepository clinicUserRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
        this.clinicUserRepository = clinicUserRepository;
    }

    @Transactional
    public AppointmentResponseDto createAppointment(AppointmentRequestDto dto) {
        // 1. Validar que el paciente exista y esté activo
        Patient paciente = patientRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + dto.getPacienteId()));
        if ("ARCHIVADO".equals(paciente.getEstado())) {
            throw new IllegalStateException("No se puede agendar citas para un paciente archivado.");
        }

        // 2. Validar clínica
        Clinic clinica;
        if (dto.getClinicaId() != null) {
            clinica = clinicRepository.findById(dto.getClinicaId()).orElse(paciente.getClinica());
        } else {
            clinica = paciente.getClinica();
        }

        // 3. Validar odontólogo (usuario_clinica)
        ClinicUser odontologo = clinicUserRepository.findById(dto.getOdontologoId())
                .orElseThrow(() -> new NoSuchElementException("Odontólogo no encontrado con ID: " + dto.getOdontologoId()));
        if (!"ACTIVO".equals(odontologo.getEstado())) {
            throw new IllegalStateException("El odontólogo seleccionado no se encuentra activo.");
        }

        // 4. Validar usuario creador (default al odontólogo si no viene)
        ClinicUser creadoPor = odontologo;
        if (dto.getCreadoPorId() != null) {
            creadoPor = clinicUserRepository.findById(dto.getCreadoPorId()).orElse(odontologo);
        }

        String doctorNombre = "Dr. " + odontologo.getUsuario().getNombres() + " " + odontologo.getUsuario().getApellidos();
        String consultorio = (dto.getConsultorio() != null && !dto.getConsultorio().isBlank()) 
                ? dto.getConsultorio().trim() : "Sillón 1";

        // 5. VALIDACIONES COMPLETAS DE REGLAS DE NEGOCIO (Horarios, días, pasado, solapamiento)
        validateAppointmentSchedule(
                dto.getInicioEn(),
                dto.getFinEn(),
                odontologo.getId(),
                consultorio,
                clinica.getId(),
                null,
                doctorNombre
        );

        // 6. Crear y persistir la cita
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
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByRange(UUID clinicaId, OffsetDateTime desde, OffsetDateTime hasta) {
        OffsetDateTime start = (desde != null) ? desde : OffsetDateTime.now().minusDays(1);
        OffsetDateTime end = (hasta != null) ? hasta : OffsetDateTime.now().plusDays(30);

        UUID targetClinicId = clinicaId;
        if (targetClinicId == null) {
            targetClinicId = clinicRepository.findFirstByOrderByFechaCreacionAsc()
                    .map(Clinic::getId).orElse(null);
        }

        if (targetClinicId == null) {
            return List.of();
        }

        return appointmentRepository.findByClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(targetClinicId, start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByOdontologo(UUID odontologoId, OffsetDateTime desde, OffsetDateTime hasta) {
        OffsetDateTime start = (desde != null) ? desde : OffsetDateTime.now().minusDays(1);
        OffsetDateTime end = (hasta != null) ? hasta : OffsetDateTime.now().plusDays(30);

        return appointmentRepository.findByOdontologoIdAndInicioEnBetweenOrderByInicioEnAsc(odontologoId, start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDto> getAppointmentsByPaciente(UUID pacienteId) {
        return appointmentRepository.findByPacienteIdOrderByInicioEnDesc(pacienteId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDto getAppointmentById(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));
        return toDto(appointment);
    }

    @Transactional
    public AppointmentResponseDto updateEstado(UUID id, AppointmentEstadoDto dto) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        String nuevoEstado = dto.getEstado().toUpperCase().trim();
        appointment.setEstado(nuevoEstado);

        if ("CANCELADA".equals(nuevoEstado) && dto.getMotivoCancelacion() != null) {
            String motivoActual = appointment.getMotivo() != null ? appointment.getMotivo() : "";
            appointment.setMotivo(motivoActual + " [Cancelación: " + dto.getMotivoCancelacion() + "]");
        }

        Appointment updated = appointmentRepository.save(appointment);
        return toDto(updated);
    }

    @Transactional
    public AppointmentResponseDto updateAsistencia(UUID id, AppointmentAsistenciaDto dto) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        String asistencia = dto.getEstadoAsistencia().toUpperCase().trim();
        appointment.setEstadoAsistencia(asistencia);

        if ("ASISTIO".equals(asistencia) && "PROGRAMADA".equals(appointment.getEstado())) {
            appointment.setEstado("CONFIRMADA");
        }

        Appointment updated = appointmentRepository.save(appointment);
        return toDto(updated);
    }

    @Transactional
    public AppointmentResponseDto rescheduleAppointment(UUID id, OffsetDateTime nuevoInicio, OffsetDateTime nuevoFin) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + id));

        String doctorNombre = (appointment.getOdontologo() != null && appointment.getOdontologo().getUsuario() != null)
                ? "Dr. " + appointment.getOdontologo().getUsuario().getNombres() + " " + appointment.getOdontologo().getUsuario().getApellidos()
                : "el odontólogo asignado";

        // Validar reglas completas sobre el nuevo horario
        validateAppointmentSchedule(
                nuevoInicio,
                nuevoFin,
                appointment.getOdontologo().getId(),
                appointment.getConsultorio(),
                appointment.getClinica().getId(),
                appointment.getId(), // Excluye la cita actual para no colisionar consigo misma
                doctorNombre
        );

        appointment.setInicioEn(nuevoInicio);
        appointment.setFinEn(nuevoFin);
        appointment.setEstado("PROGRAMADA");

        Appointment updated = appointmentRepository.save(appointment);
        return toDto(updated);
    }

    /**
     * Valida exhaustivamente todas las reglas de negocio de la agenda odontológica:
     * - Fechas no nulas y fin posterior a inicio.
     * - Mismo día calendario.
     * - Prohibición de fechas u horas pasadas.
     * - Máxima anticipación de 90 días.
     * - Duración entre 15 y 180 minutos.
     * - Horarios de atención: Lunes a Viernes 08:00 a 18:00, Sábados 08:00 a 13:00, Domingos no laborables.
     * - Anti-solapamiento de Odontólogo (Anti-Double Booking).
     * - Anti-solapamiento de Consultorio/Sillón.
     */
    private void validateAppointmentSchedule(
            OffsetDateTime inicioEn,
            OffsetDateTime finEn,
            UUID odontologoId,
            String consultorio,
            UUID clinicaId,
            UUID excludeAppointmentId,
            String doctorNombre) {

        if (inicioEn == null || finEn == null) {
            throw new IllegalArgumentException("Las fechas y horas de inicio y fin son obligatorias.");
        }

        // 1. Rango cronológico coherente
        if (!finEn.isAfter(inicioEn)) {
            throw new IllegalArgumentException("La hora de fin (" + finEn.toLocalTime() + 
                    ") debe ser posterior a la hora de inicio (" + inicioEn.toLocalTime() + ").");
        }

        // 2. Mismo día calendario (una cita dental no cruza medianoche)
        if (!inicioEn.toLocalDate().isEqual(finEn.toLocalDate())) {
            throw new IllegalArgumentException("La cita debe iniciar y terminar dentro del mismo día calendario.");
        }

        // 3. Prohibición de citas en el pasado (con tolerancia técnica de 5 minutos)
        OffsetDateTime ahora = OffsetDateTime.now();
        if (inicioEn.isBefore(ahora.minusMinutes(5))) {
            throw new IllegalArgumentException("No se pueden agendar citas en fechas u horarios pasados. Por favor seleccione una fecha y hora futura.");
        }

        // 4. Límite de anticipación (Máximo 90 días hacia adelante)
        if (inicioEn.isAfter(ahora.plusDays(90))) {
            throw new IllegalArgumentException("No se pueden agendar citas con más de 90 días de anticipación (3 meses).");
        }

        // 5. Duración de la cita (Mínimo 15 min, Máximo 180 min)
        long duracionMinutos = Duration.between(inicioEn, finEn).toMinutes();
        if (duracionMinutos < 15) {
            throw new IllegalArgumentException("La duración mínima de una cita odontológica es de 15 minutos.");
        }
        if (duracionMinutos > 180) {
            throw new IllegalArgumentException("La duración máxima de una cita odontológica no puede superar las 3 horas (180 minutos).");
        }

        // 6. Horarios de atención comercial por día de la semana
        DayOfWeek diaSemana = inicioEn.getDayOfWeek();
        LocalTime horaInicio = inicioEn.toLocalTime();
        LocalTime horaFin = finEn.toLocalTime();

        if (diaSemana == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("La clínica no atiende los días domingo. Por favor seleccione un turno de lunes a sábado.");
        }

        if (diaSemana == DayOfWeek.SATURDAY) {
            // Sábado: 08:00 a 13:00
            LocalTime aperturaSabado = LocalTime.of(8, 0);
            LocalTime cierreSabado = LocalTime.of(13, 0);
            if (horaInicio.isBefore(aperturaSabado) || horaFin.isAfter(cierreSabado) || horaInicio.isAfter(cierreSabado)) {
                throw new IllegalArgumentException("Horario no permitido: Los sábados la atención es de 08:00 a 13:00 hrs. El rango solicitado (" +
                        horaInicio + " - " + horaFin + ") excede el horario de atención.");
            }
        } else {
            // Lunes a Viernes: 08:00 a 18:00
            LocalTime aperturaSemana = LocalTime.of(8, 0);
            LocalTime cierreSemana = LocalTime.of(18, 0);
            if (horaInicio.isBefore(aperturaSemana) || horaFin.isAfter(cierreSemana) || horaInicio.isAfter(cierreSemana)) {
                throw new IllegalArgumentException("Horario no permitido: De lunes a viernes la atención es de 08:00 a 18:00 hrs. El rango solicitado (" +
                        horaInicio + " - " + horaFin + ") excede el horario de atención.");
            }
        }

        // 7. Anti-solapamiento de Odontólogo (Anti-Double Booking)
        boolean hasOverlap = appointmentRepository.existsOverlapping(
                odontologoId, inicioEn, finEn, excludeAppointmentId);
        if (hasOverlap) {
            throw new IllegalStateException("Conflicto de horario: El " + doctorNombre + 
                    " ya tiene una cita activa en ese rango de horario.");
        }

        // 8. Anti-solapamiento de Consultorio/Sillón
        if (consultorio != null && !consultorio.isBlank()) {
            boolean chairOverlap = appointmentRepository.existsConsultorioOverlapping(
                    clinicaId, consultorio.trim(), inicioEn, finEn, excludeAppointmentId);
            if (chairOverlap) {
                throw new IllegalStateException("Conflicto de espacio: El '" + consultorio.trim() + 
                        "' ya se encuentra reservado por otra cita en ese horario.");
            }
        }
    }

    public AppointmentResponseDto toDto(Appointment a) {
        AppointmentResponseDto dto = new AppointmentResponseDto();
        dto.setId(a.getId());
        dto.setClinicaId(a.getClinica() != null ? a.getClinica().getId() : null);
        dto.setClinicaNombre(a.getClinica() != null ? a.getClinica().getNombre() : null);

        if (a.getPaciente() != null) {
            dto.setPacienteId(a.getPaciente().getId());
            dto.setPacienteNombreCompleto(a.getPaciente().getNombres() + " " + a.getPaciente().getApellidos());
            dto.setPacienteNumeroDocumento(a.getPaciente().getNumeroDocumento());
            dto.setPacienteTelefono(a.getPaciente().getTelefono());
        }

        if (a.getOdontologo() != null && a.getOdontologo().getUsuario() != null) {
            dto.setOdontologoId(a.getOdontologo().getId());
            dto.setOdontologoNombreCompleto(
                    a.getOdontologo().getUsuario().getNombres() + " " + a.getOdontologo().getUsuario().getApellidos());
            dto.setOdontologoCorreo(a.getOdontologo().getUsuario().getCorreo());
        }

        if (a.getCreadoPor() != null && a.getCreadoPor().getUsuario() != null) {
            dto.setCreadoPorId(a.getCreadoPor().getId());
            dto.setCreadoPorNombreCompleto(
                    a.getCreadoPor().getUsuario().getNombres() + " " + a.getCreadoPor().getUsuario().getApellidos());
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
}
