package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.AppointmentResponseDto;
import pe.edu.utp.coronyx.backend.dto.PatientAppointmentRequestDto;
import pe.edu.utp.coronyx.backend.dto.SolicitudReprogramacionResponseDto;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AppointmentService;
import pe.edu.utp.coronyx.backend.service.PatientService;

import java.util.List;

/**
 * Controlador REST para el portal de paciente (/api/v1/me).
 * Provee rutas directas para citas y solicitudes del paciente autenticado,
 * garantizando que la identidad y pertenencia se resuelven exclusivamente desde el principal.
 */
@RestController
@RequestMapping("/api/v1/me")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class MeAppointmentsController {

    private final AppointmentService appointmentService;
    private final PatientService patientService;

    public MeAppointmentsController(AppointmentService appointmentService, PatientService patientService) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
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
     * GET /api/v1/me/citas - Listar citas del paciente autenticado.
     */
    @GetMapping("/citas")
    public ResponseEntity<List<AppointmentResponseDto>> getMyAppointments(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getMyAppointments(resolvePrincipal(principal)));
    }

    /**
     * POST /api/v1/me/citas - Agendar cita para el paciente autenticado (sin pacienteId expuesto).
     */
    @PostMapping("/citas")
    public ResponseEntity<AppointmentResponseDto> createMyAppointment(@Valid @RequestBody PatientAppointmentRequestDto dto,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        AppointmentResponseDto created = appointmentService.createMyAppointment(dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/me/solicitudes-reprogramacion - Listar solicitudes de reprogramación del paciente autenticado.
     */
    @GetMapping("/solicitudes-reprogramacion")
    public ResponseEntity<List<SolicitudReprogramacionResponseDto>> getMySolicitudesReprogramacion(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getMySolicitudesReprogramacion(resolvePrincipal(principal)));
    }

    /**
     * GET /api/v1/me/paciente - Proyección de expediente/ficha del paciente autenticado.
     */
    @GetMapping("/paciente")
    public ResponseEntity<PatientDto> getMyPatientProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.getMyPatientProfile(resolvePrincipal(principal)));
    }

    /**
     * PUT /api/v1/me/paciente - Edición de campos administrativos propios del paciente autenticado (V14).
     */
    @PutMapping("/paciente")
    public ResponseEntity<PatientDto> updateMyPatientProfile(@Valid @RequestBody pe.edu.utp.coronyx.backend.dto.UpdateMyProfileDto dto,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.updateMyPatientProfile(dto, resolvePrincipal(principal)));
    }
}
