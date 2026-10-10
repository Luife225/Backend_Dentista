package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AppointmentService;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/citas")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        try {
            UserPrincipal current = SecurityUtils.getCurrentPrincipal();
            if (current != null) {
                return current;
            }
        } catch (Exception ignored) {
        }
        throw new AccessDeniedException("No se encontró una sesión activa o autorizada. Acceso denegado.");
    }

    /**
     * POST /api/v1/citas - Agendar nueva cita en la clínica del usuario activo.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponseDto> createAppointment(@Valid @RequestBody AppointmentRequestDto dto,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        AppointmentResponseDto created = appointmentService.createAppointment(dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/citas/me - Citas del paciente autenticado (sin exponer pacienteId al cliente).
     */
    @GetMapping("/me")
    public ResponseEntity<List<AppointmentResponseDto>> getMyAppointments(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getMyAppointments(resolvePrincipal(principal)));
    }

    /**
     * POST /api/v1/citas/me - Agendamiento directo del paciente autenticado.
     */
    @PostMapping("/me")
    public ResponseEntity<AppointmentResponseDto> createMyAppointment(@Valid @RequestBody PatientAppointmentRequestDto dto,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        AppointmentResponseDto created = appointmentService.createMyAppointment(dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/citas - Listar citas de la clínica por rango de fechas (personal autorizado).
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponseDto>> getAppointments(
            @RequestParam(required = false) Long clinicaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AppointmentResponseDto> citas = appointmentService.getAppointmentsByRange(clinicaId, desde, hasta, resolvePrincipal(principal));
        return ResponseEntity.ok(citas);
    }

    /**
     * GET /api/v1/citas/{id} - Obtener detalle de una cita por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> getAppointmentById(@PathVariable Long id,
                                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id, resolvePrincipal(principal)));
    }

    /**
     * GET /api/v1/citas/odontologo/{odontologoId} - Agenda de un odontólogo específico.
     */
    @GetMapping("/odontologo/{odontologoId}")
    public ResponseEntity<List<AppointmentResponseDto>> getAppointmentsByOdontologo(
            @PathVariable Long odontologoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByOdontologo(odontologoId, desde, hasta, resolvePrincipal(principal)));
    }

    /**
     * GET /api/v1/citas/paciente/{pacienteId} - Historial de citas de un paciente.
     */
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<AppointmentResponseDto>> getAppointmentsByPaciente(
            @PathVariable Long pacienteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPaciente(pacienteId, resolvePrincipal(principal)));
    }

    /**
     * PATCH /api/v1/citas/{id}/estado - Cambiar estado de la cita.
     * Pacientes solo pueden cambiar a CONFIRMADA (intención de asistencia) o CANCELADA.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<AppointmentResponseDto> updateEstado(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentEstadoDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.updateEstado(id, dto, resolvePrincipal(principal)));
    }

    /**
     * PATCH /api/v1/citas/{id}/asistencia - Marcar asistencia física (ASISTIO o NO_SHOW).
     * Exclusivo para personal clínico y de recepción. Pacientes reciben 403 Forbidden.
     */
    @PreAuthorize("hasAnyRole('ADMIN_CLINICA', 'RECEPCIONISTA', 'ODONTOLOGO')")
    @PatchMapping("/{id}/asistencia")
    public ResponseEntity<AppointmentResponseDto> updateAsistencia(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentAsistenciaDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.updateAsistencia(id, dto, resolvePrincipal(principal)));
    }

    /**
     * PUT /api/v1/citas/{id}/reprogramar - Reprogramar fecha y hora de la cita directamente.
     * Exclusivo para personal administrativo y clínico. Pacientes deben usar solicitudes.
     */
    @PreAuthorize("hasAnyRole('ADMIN_CLINICA', 'RECEPCIONISTA', 'ODONTOLOGO')")
    @PutMapping("/{id}/reprogramar")
    public ResponseEntity<AppointmentResponseDto> rescheduleAppointment(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime nuevoInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime nuevoFin,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, nuevoInicio, nuevoFin, resolvePrincipal(principal)));
    }

    // ── Solicitudes de Reprogramación ─────────────────────────────────────────

    /**
     * POST /api/v1/citas/{id}/solicitar-reprogramacion - Crea solicitud de reprogramación.
     */
    @PostMapping("/{id}/solicitar-reprogramacion")
    public ResponseEntity<SolicitudReprogramacionResponseDto> solicitarReprogramacion(
            @PathVariable Long id,
            @Valid @RequestBody CreateSolicitudReprogramacionDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SolicitudReprogramacionResponseDto res = appointmentService.solicitarReprogramacion(id, dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * GET /api/v1/citas/solicitudes-reprogramacion - Listar solicitudes de reprogramación de la clínica.
     */
    @PreAuthorize("hasAnyRole('ADMIN_CLINICA', 'RECEPCIONISTA', 'ODONTOLOGO')")
    @GetMapping("/solicitudes-reprogramacion")
    public ResponseEntity<List<SolicitudReprogramacionResponseDto>> getSolicitudesReprogramacion(
            @RequestParam(required = false) Long clinicaId,
            @RequestParam(required = false) String estado,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getSolicitudesReprogramacion(clinicaId, estado, resolvePrincipal(principal)));
    }

    /**
     * GET /api/v1/citas/me/solicitudes - Listar solicitudes del paciente autenticado.
     */
    @GetMapping("/me/solicitudes")
    public ResponseEntity<List<SolicitudReprogramacionResponseDto>> getMySolicitudes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getMySolicitudesReprogramacion(resolvePrincipal(principal)));
    }

    /**
     * PATCH /api/v1/citas/solicitudes-reprogramacion/{id}/resolver - Resolver solicitud (APROBADA/RECHAZADA).
     * Atómico: si se aprueba, actualiza la cita y la solicitud en una sola transacción.
     */
    @PreAuthorize("hasAnyRole('ADMIN_CLINICA', 'RECEPCIONISTA')")
    @PatchMapping("/solicitudes-reprogramacion/{id}/resolver")
    public ResponseEntity<SolicitudReprogramacionResponseDto> resolverSolicitud(
            @PathVariable Long id,
            @Valid @RequestBody ResolverSolicitudReprogramacionDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.resolverSolicitudReprogramacion(id, dto, resolvePrincipal(principal)));
    }

    /**
     * PATCH /api/v1/citas/solicitudes-reprogramacion/{id}/cancelar - Cancelar solicitud pendiente.
     */
    @PatchMapping("/solicitudes-reprogramacion/{id}/cancelar")
    public ResponseEntity<SolicitudReprogramacionResponseDto> cancelarSolicitud(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.cancelarSolicitudReprogramacion(id, resolvePrincipal(principal)));
    }
}
