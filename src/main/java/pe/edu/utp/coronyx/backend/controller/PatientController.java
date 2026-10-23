package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.PatientAdminDto;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.PatientService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
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

    /** GET /api/v1/patients - Lista pacientes de la clínica del usuario activo */
    @GetMapping
    public ResponseEntity<List<PatientDto>> getAllPatients(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.getAllPatients(resolvePrincipal(principal)));
    }

    /** GET /api/v1/patients/admin - Proyección estrictamente administrativa de pacientes para ADMIN_CLINICA y RECEPCIONISTA */
    @GetMapping("/admin")
    public ResponseEntity<List<PatientAdminDto>> getAdministrativePatients(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.getAllAdministrativePatients(resolvePrincipal(principal)));
    }

    /** GET /api/v1/patients/me - Ficha propia del paciente autenticado */
    @GetMapping("/me")
    public ResponseEntity<PatientDto> getMyPatientProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.getMyPatientProfile(resolvePrincipal(principal)));
    }

    /** PUT /api/v1/patients/me - Edición de campos administrativos propios del paciente autenticado (V14) */
    @PutMapping("/me")
    public ResponseEntity<PatientDto> updateMyPatientProfile(@Valid @RequestBody pe.edu.utp.coronyx.backend.dto.UpdateMyProfileDto dto,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.updateMyPatientProfile(dto, resolvePrincipal(principal)));
    }

    /** GET /api/v1/patients/{id} - Ficha de paciente validada por clínica y permisos */
    @GetMapping("/{id}")
    public ResponseEntity<PatientDto> getPatientById(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.getPatientById(id, resolvePrincipal(principal)));
    }

    /** POST /api/v1/patients - Registra un nuevo paciente en la clínica del usuario */
    @PostMapping
    public ResponseEntity<PatientDto> createPatient(@Valid @RequestBody PatientDto dto,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        PatientDto created = patientService.createPatient(dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/v1/patients/{id} - Actualiza la ficha del paciente */
    @PutMapping("/{id}")
    public ResponseEntity<PatientDto> updatePatient(@PathVariable Long id,
                                                    @RequestBody PatientDto dto,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.updatePatient(id, dto, resolvePrincipal(principal)));
    }

    /** PATCH /api/v1/patients/{id}/archive - Baja lógica: cambia estado a 'ARCHIVADO' */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<PatientDto> archivePatient(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(patientService.archivePatient(id, resolvePrincipal(principal)));
    }

    /** PATCH /api/v1/patients/{id}/link-user - Vincula cuenta de usuario a paciente sin equiparar paciente.id a usuario.id */
    @PatchMapping("/{id}/link-user")
    public ResponseEntity<PatientDto> linkUser(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        String uIdStr = body != null ? body.get("usuarioId") : null;
        Long uId = null;
        if (uIdStr != null && !uIdStr.isBlank() && !"null".equalsIgnoreCase(uIdStr.trim())) {
            if (!uIdStr.trim().matches("[1-9]\\d*")) throw new IllegalArgumentException("usuarioId debe ser un entero positivo");
            try {
                uId = Long.valueOf(uIdStr.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("usuarioId está fuera del rango permitido");
            }
        }
        return ResponseEntity.ok(patientService.linkUserAccount(id, uId, resolvePrincipal(principal)));
    }

    public ResponseEntity<List<PatientDto>> getAllPatients() {
        return getAllPatients(null);
    }

    public ResponseEntity<PatientDto> getPatientById(Long id) {
        return getPatientById(id, null);
    }

    public ResponseEntity<PatientDto> createPatient(PatientDto dto) {
        return createPatient(dto, null);
    }

    public ResponseEntity<PatientDto> updatePatient(Long id, PatientDto dto) {
        return updatePatient(id, dto, null);
    }

    public ResponseEntity<PatientDto> archivePatient(Long id) {
        return archivePatient(id, null);
    }
}
