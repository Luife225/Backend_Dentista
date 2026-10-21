package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.AusenciaDto;
import pe.edu.utp.coronyx.backend.dto.HorarioAtencionDto;
import pe.edu.utp.coronyx.backend.dto.SlotDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AvailabilityService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/disponibilidad")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) return principal;
        try {
            UserPrincipal current = SecurityUtils.getCurrentPrincipal();
            if (current != null) return current;
        } catch (Exception ignored) {
        }
        throw new AccessDeniedException("No se encontró una sesión activa o autorizada.");
    }

    @GetMapping("/horarios")
    public ResponseEntity<List<HorarioAtencionDto>> getClinicSchedule(
            @RequestParam(required = false) Long clinicaId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.getClinicSchedule(clinicaId, resolvePrincipal(principal)));
    }

    @PutMapping("/horarios")
    public ResponseEntity<List<HorarioAtencionDto>> updateClinicSchedule(
            @RequestParam(required = false) Long clinicaId,
            @Valid @RequestBody List<HorarioAtencionDto> dtos,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.updateClinicSchedule(clinicaId, dtos, resolvePrincipal(principal)));
    }

    @GetMapping("/ausencias/{odontologoId}")
    public ResponseEntity<List<AusenciaDto>> getDoctorAbsences(
            @PathVariable Long odontologoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.getDoctorAbsences(odontologoId, resolvePrincipal(principal)));
    }

    @PostMapping("/ausencias/{odontologoId}")
    public ResponseEntity<AusenciaDto> createDoctorAbsence(
            @PathVariable Long odontologoId,
            @Valid @RequestBody AusenciaDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        AusenciaDto created = availabilityService.createDoctorAbsence(odontologoId, dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/ausencias/{ausenciaId}")
    public ResponseEntity<Void> deleteDoctorAbsence(
            @PathVariable Long ausenciaId,
            @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.deleteDoctorAbsence(ausenciaId, resolvePrincipal(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotDto>> getAvailableSlots(
            @RequestParam Long odontologoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.getAvailableSlots(odontologoId, fecha, resolvePrincipal(principal)));
    }
}
