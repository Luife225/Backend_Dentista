package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.ClinicUserDto;
import pe.edu.utp.coronyx.backend.dto.CreateClinicUserDto;
import pe.edu.utp.coronyx.backend.dto.StatusUpdateDto;
import pe.edu.utp.coronyx.backend.dto.UpdateClinicUserDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.ClinicUserService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinic/users")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
@PreAuthorize("hasRole('ADMIN_CLINICA')")
public class ClinicUserController {

    private final ClinicUserService clinicUserService;

    public ClinicUserController(ClinicUserService clinicUserService) {
        this.clinicUserService = clinicUserService;
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
        throw new AccessDeniedException("No se encontró una sesión activa con permisos de ADMIN_CLINICA.");
    }

    /** GET /api/v1/clinic/users - Lista cuentas y membresías de la clínica del administrador */
    @GetMapping
    public ResponseEntity<List<ClinicUserDto>> getClinicUsers(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicUserService.getClinicUsers(resolvePrincipal(principal)));
    }

    /** GET /api/v1/clinic/users/{id} - Detalle de cuenta y membresía por ID */
    @GetMapping("/{id}")
    public ResponseEntity<ClinicUserDto> getClinicUserById(@PathVariable Long id,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicUserService.getClinicUserById(id, resolvePrincipal(principal)));
    }

    /** POST /api/v1/clinic/users - Da de alta una nueva cuenta y membresía en la clínica */
    @PostMapping
    public ResponseEntity<ClinicUserDto> createClinicUser(@Valid @RequestBody CreateClinicUserDto dto,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        ClinicUserDto created = clinicUserService.createClinicUser(dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/v1/clinic/users/{id} - Edita datos, rol o estado de la membresía */
    @PutMapping("/{id}")
    public ResponseEntity<ClinicUserDto> updateClinicUser(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateClinicUserDto dto,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicUserService.updateClinicUser(id, dto, resolvePrincipal(principal)));
    }

    /** PATCH /api/v1/clinic/users/{id}/status - Activa o desactiva la membresía/cuenta */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ClinicUserDto> updateClinicUserStatus(@PathVariable Long id,
                                                                @Valid @RequestBody StatusUpdateDto dto,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clinicUserService.updateClinicUserStatus(id, dto.getEstado(), resolvePrincipal(principal)));
    }
}
