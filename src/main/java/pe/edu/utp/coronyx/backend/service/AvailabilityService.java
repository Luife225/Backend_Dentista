package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.AusenciaDto;
import pe.edu.utp.coronyx.backend.dto.HorarioAtencionDto;
import pe.edu.utp.coronyx.backend.dto.SlotDto;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioAtencionRepository horarioRepository;
    private final AusenciaOdontologoRepository ausenciaRepository;
    private final ClinicRepository clinicRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final AppointmentRepository appointmentRepository;

    public AvailabilityService(HorarioAtencionRepository horarioRepository,
                               AusenciaOdontologoRepository ausenciaRepository,
                               ClinicRepository clinicRepository,
                               ClinicUserRepository clinicUserRepository,
                               AppointmentRepository appointmentRepository) {
        this.horarioRepository = horarioRepository;
        this.ausenciaRepository = ausenciaRepository;
        this.clinicRepository = clinicRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.appointmentRepository = appointmentRepository;
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("No se encontró una sesión activa o clínica asignada");
        }
    }

    private ClinicUser requireClinicDoctor(Long id, UserPrincipal principal) {
        ClinicUser doctor = clinicUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Odontólogo no encontrado"));
        if (!doctor.getClinica().getId().equals(principal.getClinicaId())
                || doctor.getRol() == null || !Boolean.TRUE.equals(doctor.getRol().getActivo()) || !"ODONTOLOGO".equals(doctor.getRol().getCodigo())
                || !"ACTIVO".equals(doctor.getEstado())
                || doctor.getUsuario() == null || !"ACTIVO".equals(doctor.getUsuario().getEstado())) {
            throw new NoSuchElementException("Odontólogo no disponible en esta clínica");
        }
        return doctor;
    }

    private void requireAbsenceAccess(UserPrincipal principal, Long doctorId) {
        if ("ADMIN_CLINICA".equals(principal.getRol()) || "RECEPCIONISTA".equals(principal.getRol())) return;
        if ("ODONTOLOGO".equals(principal.getRol()) && doctorId.equals(principal.getUsuarioClinicaId())) return;
        throw new AccessDeniedException("Solo el personal autorizado puede gestionar ausencias");
    }

    /**
     * Consulta horarios base de la clínica.
     */
    @Transactional(readOnly = true)
    public List<HorarioAtencionDto> getClinicSchedule(Long clinicaId, UserPrincipal principal) {
        validatePrincipal(principal);
        Long targetClinicId = (clinicaId != null) ? clinicaId : principal.getClinicaId();
        if (!targetClinicId.equals(principal.getClinicaId()) && !principal.isSuperAdmin()) {
            throw new AccessDeniedException("Acceso denegado: no puede consultar horarios de otra clínica");
        }
        return horarioRepository.findByClinicaIdOrderByDiaSemanaAsc(targetClinicId)
                .stream()
                .filter(h -> h.getOdontologo() == null)
                .map(this::toHorarioDto)
                .collect(Collectors.toList());
    }

    /**
     * A09: Actualización del horario de la clínica por ADMIN_CLINICA.
     */
    @Transactional
    public List<HorarioAtencionDto> updateClinicSchedule(Long clinicaId, List<HorarioAtencionDto> dtos, UserPrincipal principal) {
        validatePrincipal(principal);
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el administrador de clínica puede configurar los horarios");
        }
        Long targetClinicId = (clinicaId != null) ? clinicaId : principal.getClinicaId();
        if (!targetClinicId.equals(principal.getClinicaId())) {
            throw new AccessDeniedException("No puede modificar horarios de otra clínica");
        }
        Clinic clinic = clinicRepository.findById(targetClinicId)
                .orElseThrow(() -> new NoSuchElementException("Clínica no encontrada con ID: " + targetClinicId));

        List<HorarioAtencion> result = new ArrayList<>();
        for (HorarioAtencionDto dto : dtos) {
            if (dto.getDiaSemana() == null || dto.getDiaSemana() < 1 || dto.getDiaSemana() > 7
                    || dto.getHoraApertura() == null || dto.getHoraCierre() == null
                    || !dto.getHoraCierre().isAfter(dto.getHoraApertura())
                    || (dto.getDuracionTurnoMinutos() != null && (dto.getDuracionTurnoMinutos() < 5 || dto.getDuracionTurnoMinutos() > 240))) {
                throw new IllegalArgumentException("Horario inválido: revise día, apertura, cierre y duración del turno");
            }
            Optional<HorarioAtencion> existing = horarioRepository.findClinicScheduleForDay(targetClinicId, dto.getDiaSemana());
            HorarioAtencion h;
            if (existing.isPresent()) {
                h = existing.get();
                h.setHoraApertura(dto.getHoraApertura());
                h.setHoraCierre(dto.getHoraCierre());
                h.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
                h.setDuracionTurnoMinutos(dto.getDuracionTurnoMinutos() != null ? dto.getDuracionTurnoMinutos() : 30);
            } else {
                h = new HorarioAtencion();
                h.setClinica(clinic);
                h.setDiaSemana(dto.getDiaSemana());
                h.setHoraApertura(dto.getHoraApertura());
                h.setHoraCierre(dto.getHoraCierre());
                h.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
                h.setDuracionTurnoMinutos(dto.getDuracionTurnoMinutos() != null ? dto.getDuracionTurnoMinutos() : 30);
            }
            result.add(horarioRepository.save(h));
        }
        return result.stream().map(this::toHorarioDto).collect(Collectors.toList());
    }

    /**
     * Consulta ausencias registradas para un odontólogo.
     */
    @Transactional(readOnly = true)
    public List<AusenciaDto> getDoctorAbsences(Long odontologoId, UserPrincipal principal) {
        validatePrincipal(principal);
        requireAbsenceAccess(principal, odontologoId);
        requireClinicDoctor(odontologoId, principal);
        return ausenciaRepository.findByOdontologoIdOrderByInicioEnAsc(odontologoId)
                .stream()
                .map(this::toAusenciaDto)
                .collect(Collectors.toList());
    }

    /**
     * Registro de ausencia (vacaciones, permiso, licencia) para un odontólogo.
     */
    @Transactional
    public AusenciaDto createDoctorAbsence(Long odontologoId, AusenciaDto dto, UserPrincipal principal) {
        validatePrincipal(principal);
        requireAbsenceAccess(principal, odontologoId);
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede registrar ausencias médicas");
        }

        ClinicUser odontologo = requireClinicDoctor(odontologoId, principal);

        if (!odontologo.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("El odontólogo no pertenece a la clínica activa");
        }

        if (dto.getInicioEn() == null || dto.getFinEn() == null || !dto.getFinEn().isAfter(dto.getInicioEn())) {
            throw new IllegalArgumentException("La fecha de fin de la ausencia debe ser posterior a la fecha de inicio");
        }

        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CONCAT('DOCTOR:', ?1))::bigint)")
                .setParameter(1, odontologoId.toString()).getResultList();
        if (appointmentRepository.existsOverlapping(odontologoId,dto.getInicioEn(),dto.getFinEn(),null)) {
            throw new IllegalStateException("Hay citas reservadas durante la ausencia. Reprográmelas o cancélelas antes de bloquear el horario.");
        }
        String absenceType=dto.getTipo()==null?"VACACIONES":dto.getTipo().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("VACACIONES","PERMISO","CAPACITACION","LICENCIA_MEDICA","OTRO").contains(absenceType)) {
            throw new IllegalArgumentException("Tipo de ausencia inválido");
        }
        AusenciaOdontologo ausencia = new AusenciaOdontologo();
        ausencia.setClinica(odontologo.getClinica());
        ausencia.setOdontologo(odontologo);
        ausencia.setInicioEn(dto.getInicioEn());
        ausencia.setFinEn(dto.getFinEn());
        ausencia.setMotivo(dto.getMotivo());
        ausencia.setTipo(absenceType);

        AusenciaOdontologo saved = ausenciaRepository.save(ausencia);
        return toAusenciaDto(saved);
    }

    /**
     * Eliminación de ausencia.
     */
    @Transactional
    public void deleteDoctorAbsence(Long ausenciaId, UserPrincipal principal) {
        validatePrincipal(principal);
        AusenciaOdontologo ausencia = ausenciaRepository.findById(ausenciaId)
                .orElseThrow(() -> new NoSuchElementException("Ausencia no encontrada con ID: " + ausenciaId));
        if (!ausencia.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Ausencia no encontrada");
        }
        requireAbsenceAccess(principal, ausencia.getOdontologo().getId());
        ausenciaRepository.delete(ausencia);
    }

    /**
     * R05 / A09: Cálculo de slots libres reales basados en horario configurado,
     * citas existentes y ausencias médicas.
     */
    @Transactional(readOnly = true)
    public List<SlotDto> getAvailableSlots(Long odontologoId, LocalDate fecha, UserPrincipal principal) {
        validatePrincipal(principal);

        ClinicUser odontologo = requireClinicDoctor(odontologoId, principal);

        Long clinicaId = odontologo.getClinica().getId();
        int diaSemana = fecha.getDayOfWeek().getValue(); // 1: Lunes .. 7: Domingo

        // 1. Obtener horario para el día (prioridad horario de doctor, fallback horario de clínica)
        Optional<HorarioAtencion> horarioOpt = horarioRepository.findDoctorScheduleForDay(odontologoId, diaSemana);
        if (horarioOpt.isEmpty()) {
            horarioOpt = horarioRepository.findClinicScheduleForDay(clinicaId, diaSemana);
        }

        HorarioAtencion horario = horarioOpt.orElseGet(() -> {
            HorarioAtencion defaults = new HorarioAtencion();
            defaults.setHoraApertura(LocalTime.of(8,0)); defaults.setHoraCierre(LocalTime.of(diaSemana==6?13:18,0));
            defaults.setDuracionTurnoMinutos(30); defaults.setActivo(diaSemana!=7);
            return defaults;
        });
        if (!Boolean.TRUE.equals(horario.getActivo())) return new ArrayList<>();
        LocalTime apertura = horario.getHoraApertura();
        LocalTime cierre = horario.getHoraCierre();
        int duracion = horario.getDuracionTurnoMinutos() > 0 ? horario.getDuracionTurnoMinutos() : 30;

        // 2. Cargar citas del doctor en ese rango
        OffsetDateTime diaInicio = fecha.atTime(apertura).atZone(LIMA_ZONE).toOffsetDateTime();
        OffsetDateTime diaFin = fecha.atTime(cierre).atZone(LIMA_ZONE).toOffsetDateTime();

        List<Appointment> appts = appointmentRepository.findByOdontologoIdAndInicioEnBetweenOrderByInicioEnAsc(
                odontologoId, fecha.atStartOfDay(LIMA_ZONE).toOffsetDateTime(), fecha.plusDays(1).atStartOfDay(LIMA_ZONE).toOffsetDateTime())
                .stream()
                .filter(a -> !"CANCELADA".equalsIgnoreCase(a.getEstado()))
                .collect(Collectors.toList());

        // 3. Iterar y generar slots
        List<SlotDto> slots = new ArrayList<>();
        LocalTime cursor = apertura;

        while (cursor.plusMinutes(duracion).isAfter(cursor) && cursor.plusMinutes(duracion).compareTo(cierre) <= 0) {
            LocalTime slotFin = cursor.plusMinutes(duracion);
            OffsetDateTime slotStart = fecha.atTime(cursor).atZone(LIMA_ZONE).toOffsetDateTime();
            OffsetDateTime slotEnd = fecha.atTime(slotFin).atZone(LIMA_ZONE).toOffsetDateTime();

            // Verificar si hay cita solapada
            boolean solapaCita = appts.stream().anyMatch(a ->
                    a.getInicioEn().isBefore(slotEnd) && a.getFinEn().isAfter(slotStart));

            // Verificar si hay ausencia médica solapada
            boolean solapaAusencia = ausenciaRepository.existsOverlappingAusencia(odontologoId, slotStart, slotEnd);

            boolean disponible = slotStart.isAfter(OffsetDateTime.now()) && !solapaCita && !solapaAusencia;
            String motivo = !disponible ? (solapaAusencia ? "Especialista ausente" : "Horario reservado") : null;

            slots.add(new SlotDto(
                    cursor.format(TIME_FMT),
                    slotFin.format(TIME_FMT),
                    disponible,
                    motivo
            ));

            cursor = slotFin;
        }

        return slots;
    }

    private HorarioAtencionDto toHorarioDto(HorarioAtencion h) {
        HorarioAtencionDto dto = new HorarioAtencionDto();
        dto.setId(h.getId() != null ? h.getId().toString() : null);
        dto.setClinicaId(h.getClinica() != null && h.getClinica().getId() != null ? h.getClinica().getId().toString() : null);
        dto.setOdontologoId(h.getOdontologo() != null && h.getOdontologo().getId() != null ? h.getOdontologo().getId().toString() : null);
        dto.setDiaSemana(h.getDiaSemana());
        dto.setHoraApertura(h.getHoraApertura());
        dto.setHoraCierre(h.getHoraCierre());
        dto.setActivo(h.getActivo());
        dto.setDuracionTurnoMinutos(h.getDuracionTurnoMinutos());
        return dto;
    }

    private AusenciaDto toAusenciaDto(AusenciaOdontologo a) {
        AusenciaDto dto = new AusenciaDto();
        dto.setId(a.getId() != null ? a.getId().toString() : null);
        dto.setClinicaId(a.getClinica() != null && a.getClinica().getId() != null ? a.getClinica().getId().toString() : null);
        dto.setOdontologoId(a.getOdontologo() != null && a.getOdontologo().getId() != null ? a.getOdontologo().getId().toString() : null);
        if (a.getOdontologo() != null && a.getOdontologo().getUsuario() != null) {
            dto.setOdontologoNombre("Dr. " + a.getOdontologo().getUsuario().getNombres() + " " + a.getOdontologo().getUsuario().getApellidos());
        }
        dto.setInicioEn(a.getInicioEn());
        dto.setFinEn(a.getFinEn());
        dto.setMotivo(a.getMotivo());
        dto.setTipo(a.getTipo());
        return dto;
    }
}
