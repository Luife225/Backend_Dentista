package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.AppointmentResponseDto;
import pe.edu.utp.coronyx.backend.dto.DashboardSummaryDto;
import pe.edu.utp.coronyx.backend.model.Appointment;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-PE"));

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;

    public DashboardService(AppointmentRepository appointmentRepository,
                            PatientRepository patientRepository,
                            AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.appointmentService = appointmentService;
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("No se encontró una sesión activa con clínica asignada");
        }
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: este panel es exclusivo para el personal de la clínica");
        }
    }

    /**
     * A11 / V10: Resumen real desde la base de datos para el dashboard.
     * Calcula métricas y citas del día en zona horaria America/Lima.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary(UserPrincipal principal) {
        validatePrincipal(principal);
        Long clinicaId = principal.getClinicaId();

        LocalDate todayLima = LocalDate.now(LIMA_ZONE);
        OffsetDateTime startOfDay = todayLima.atStartOfDay(LIMA_ZONE).toOffsetDateTime();
        OffsetDateTime endOfDay = todayLima.plusDays(1).atStartOfDay(LIMA_ZONE).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now(LIMA_ZONE);

        List<Appointment> allToday;
        if ("ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            Long doctorId = principal.getUsuarioClinicaId();
            allToday = appointmentRepository.findByOdontologoIdAndInicioEnBetweenOrderByInicioEnAsc(
                    doctorId, startOfDay, endOfDay);
        } else {
            allToday = appointmentRepository.findByClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(
                    clinicaId, startOfDay, endOfDay);
        }

        DashboardSummaryDto dto = new DashboardSummaryDto();
        dto.setFechaHoyLima(todayLima.format(DATE_FMT));
        dto.setTotalCitasHoy(allToday.size());

        int confirmadas = 0;
        int pendientes = 0;
        int atendidas = 0;
        int canceladas = 0;

        for (Appointment a : allToday) {
            String est = a.getEstado() != null ? a.getEstado().toUpperCase() : "PROGRAMADA";
            switch (est) {
                case "CONFIRMADA" -> confirmadas++;
                case "PROGRAMADA", "REPROGRAMADA" -> pendientes++;
                case "EN_ATENCION", "FINALIZADA" -> atendidas++;
                case "CANCELADA", "NO_ASISTIO" -> canceladas++;
                default -> pendientes++;
            }
        }

        dto.setCitasConfirmadasHoy(confirmadas);
        dto.setCitasPendientesHoy(pendientes);
        dto.setCitasAtendidasHoy(atendidas);
        dto.setCitasCanceladasHoy(canceladas);

        dto.setTotalPacientes(patientRepository.countByClinicaId(clinicaId));

        // Mapear citas
        List<AppointmentResponseDto> dtos = allToday.stream()
                .map(appointmentService::toDto)
                .collect(Collectors.toList());
        dto.setCitasHoy(dtos);

        // Próxima cita de hoy (la primera cuya hora de inicio sea igual o posterior a now y no cancelada/finalizada)
        Appointment nextAppt = allToday.stream()
                .filter(a -> !"CANCELADA".equalsIgnoreCase(a.getEstado()) && !"FINALIZADA".equalsIgnoreCase(a.getEstado()))
                .filter(a -> a.getInicioEn().isAfter(now.minusMinutes(15)))
                .min(Comparator.comparing(Appointment::getInicioEn))
                .orElse(null);

        if (nextAppt != null) {
            dto.setProximaCita(appointmentService.toDto(nextAppt));
        }

        return dto;
    }
}
