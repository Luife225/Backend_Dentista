package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.ConfirmOdontogramaDto;
import pe.edu.utp.coronyx.backend.dto.OdontogramaVersionDto;
import pe.edu.utp.coronyx.backend.dto.SaveOdontogramaDraftDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.OdontogramService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/odontograma")
public class OdontogramController {

    private final OdontogramService odontogramService;

    public OdontogramController(OdontogramService odontogramService) {
        this.odontogramService = odontogramService;
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

    @GetMapping({"/paciente/{pacienteId}", "/paciente/{pacienteId}/actual"})
    public ResponseEntity<OdontogramaVersionDto> getCurrentOdontogram(
            @PathVariable Long pacienteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(odontogramService.getCurrentOdontogram(pacienteId, resolvePrincipal(principal)));
    }

    @GetMapping("/paciente/{pacienteId}/historial")
    public ResponseEntity<List<OdontogramaVersionDto>> getHistory(
            @PathVariable Long pacienteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(odontogramService.getHistory(pacienteId, resolvePrincipal(principal)));
    }

    @PostMapping({"/paciente/{pacienteId}/draft", "/paciente/{pacienteId}/borrador"})
    public ResponseEntity<OdontogramaVersionDto> saveDraft(
            @PathVariable Long pacienteId,
            @Valid @RequestBody SaveOdontogramaDraftDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        OdontogramaVersionDto saved = odontogramService.saveDraft(pacienteId, dto, resolvePrincipal(principal));
        return ResponseEntity.ok(saved);
    }

    @PutMapping({"/borrador/{versionId}", "/draft/{versionId}", "/version/{versionId}/borrador", "/versiones/{versionId}/borrador"})
    public ResponseEntity<OdontogramaVersionDto> updateDraftByVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody SaveOdontogramaDraftDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        OdontogramaVersionDto saved = odontogramService.updateDraftByVersion(versionId, dto, resolvePrincipal(principal));
        return ResponseEntity.ok(saved);
    }

    @PostMapping({"/paciente/{pacienteId}/nueva-version", "/paciente/{pacienteId}/new-version"})
    public ResponseEntity<OdontogramaVersionDto> createNewVersionForPatient(
            @PathVariable Long pacienteId,
            @RequestBody(required = false) SaveOdontogramaDraftDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        OdontogramaVersionDto created = odontogramService.createNewVersionForPatient(pacienteId, dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping({"/{id}/confirm", "/{id}/confirmar", "/version/{id}/confirmar", "/versiones/{id}/confirmar"})
    public ResponseEntity<OdontogramaVersionDto> confirmVersion(
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmOdontogramaDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        OdontogramaVersionDto confirmed = odontogramService.confirmVersion(
                id, dto != null ? dto : new ConfirmOdontogramaDto(), resolvePrincipal(principal));
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping({"/{id}/versions", "/{id}/versiones", "/version/{id}/nueva-version", "/versiones/{id}/nueva-version"})
    public ResponseEntity<OdontogramaVersionDto> createCorrectionVersion(
            @PathVariable Long id,
            @Valid @RequestBody SaveOdontogramaDraftDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        OdontogramaVersionDto created = odontogramService.createCorrectionVersion(id, dto, resolvePrincipal(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
