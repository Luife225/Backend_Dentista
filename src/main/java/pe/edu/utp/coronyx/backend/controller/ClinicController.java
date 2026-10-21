package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.ClinicDto;
import pe.edu.utp.coronyx.backend.dto.UpdateClinicDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.ClinicService;

@RestController
@RequestMapping({"/api/v1/clinic/profile", "/api/v1/clinicas/me", "/api/v1/clinic"})
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
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

    @GetMapping
    public ResponseEntity<ClinicDto> getClinicProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicService.getClinicProfile(resolvePrincipal(principal)));
    }

    @PutMapping
    public ResponseEntity<ClinicDto> updateClinicProfile(
            @Valid @RequestBody UpdateClinicDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicService.updateClinicProfile(dto, resolvePrincipal(principal)));
    }
}
