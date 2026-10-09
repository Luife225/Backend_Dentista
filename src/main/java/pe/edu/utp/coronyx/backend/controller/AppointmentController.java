package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.service.AppointmentService;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

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

    /**
     * POST /api/v1/citas - Agendar nueva cita con validación anti-solapamiento.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponseDto> createAppointment(@Valid @RequestBody AppointmentRequestDto dto) {
        AppointmentResponseDto created = appointmentService.createAppointment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/citas - Listar citas por rango de fechas y/o clínica.
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponseDto>> getAppointments(
            @RequestParam(required = false) UUID clinicaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        List<AppointmentResponseDto> citas = appointmentService.getAppointmentsByRange(clinicaId, desde, hasta);
        return ResponseEntity.ok(citas);
    }

    /**
     * GET /api/v1/citas/{id} - Obtener detalle de una cita por su UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> getAppointmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    /**
     * GET /api/v1/citas/odontologo/{odontologoId} - Agenda de un odontólogo específico.
     */
    @GetMapping("/odontologo/{odontologoId}")
    public ResponseEntity<List<AppointmentResponseDto>> getAppointmentsByOdontologo(
            @PathVariable UUID odontologoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByOdontologo(odontologoId, desde, hasta));
    }

    /**
     * GET /api/v1/citas/paciente/{pacienteId} - Historial de citas de un paciente.
     */
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<AppointmentResponseDto>> getAppointmentsByPaciente(@PathVariable UUID pacienteId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPaciente(pacienteId));
    }

    /**
     * PATCH /api/v1/citas/{id}/estado - Cambiar estado de la cita (CONFIRMADA, CANCELADA, etc.).
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<AppointmentResponseDto> updateEstado(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentEstadoDto dto) {
        return ResponseEntity.ok(appointmentService.updateEstado(id, dto));
    }

    /**
     * PATCH /api/v1/citas/{id}/asistencia - Marcar asistencia (ASISTIO o NO_SHOW).
     */
    @PatchMapping("/{id}/asistencia")
    public ResponseEntity<AppointmentResponseDto> updateAsistencia(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentAsistenciaDto dto) {
        return ResponseEntity.ok(appointmentService.updateAsistencia(id, dto));
    }

    /**
     * PUT /api/v1/citas/{id}/reprogramar - Reprogramar fecha y hora de la cita.
     */
    @PutMapping("/{id}/reprogramar")
    public ResponseEntity<AppointmentResponseDto> rescheduleAppointment(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime nuevoInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime nuevoFin) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, nuevoInicio, nuevoFin));
    }

    /** Manejo de validaciones Bean Validation (@Valid) → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "Error de validación en los datos de la cita",
                "errors", errors
        ));
    }

    /** Manejo de parámetros ilegales → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    /** Manejo de solapamiento de citas o estado inválido → 409 Conflict */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /** Manejo de recursos no encontrados → 404 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
