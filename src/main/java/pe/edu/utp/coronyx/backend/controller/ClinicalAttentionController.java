package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.service.ClinicalAttentionService;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/clinical-encounters", "/api/v1/atenciones-clinicas"})
@PreAuthorize("hasRole('ODONTOLOGO')")
public class ClinicalAttentionController {

    private final ClinicalAttentionService clinicalAttentionService;

    public ClinicalAttentionController(ClinicalAttentionService clinicalAttentionService) {
        this.clinicalAttentionService = clinicalAttentionService;
    }

    /**
     * D03: Crea un nuevo encuentro clínico en borrador con su versión inicial 1.
     */
    @PostMapping
    public ResponseEntity<ClinicalEncounterResponseDto> createDraft(
            @Valid @RequestBody CreateClinicalEncounterDto dto) {
        ClinicalEncounterResponseDto created = clinicalAttentionService.createDraft(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * D03: Modifica el borrador activo de una atención clínica aún no confirmada.
     */
    @PutMapping({"/{id}/draft", "/{id}/borrador"})
    public ResponseEntity<ClinicalEncounterResponseDto> updateDraft(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateClinicalDraftDto dto) {
        ClinicalEncounterResponseDto updated = clinicalAttentionService.updateDraft(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * D04 / G05: Confirma la versión exacta de una atención clínica de forma idempotente.
     */
    @PostMapping({"/{id}/confirm", "/{id}/confirmar"})
    public ResponseEntity<ClinicalEncounterResponseDto> confirmVersion(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ConfirmVersionDto dto) {
        ClinicalEncounterResponseDto confirmed = clinicalAttentionService.confirmVersion(id, dto != null ? dto : new ConfirmVersionDto());
        return ResponseEntity.ok(confirmed);
    }

    /**
     * D04 / G05: Confirmar versión específica vía PUT por path.
     */
    @PutMapping({"/{id}/versions/{versionId}/confirm", "/{id}/versiones/{versionId}/confirmar"})
    public ResponseEntity<ClinicalEncounterResponseDto> confirmSpecificVersion(
            @PathVariable("id") Long id,
            @PathVariable("versionId") Long versionId) {
        ConfirmVersionDto dto = new ConfirmVersionDto(versionId, null);
        ClinicalEncounterResponseDto confirmed = clinicalAttentionService.confirmVersion(id, dto);
        return ResponseEntity.ok(confirmed);
    }

    /**
     * D06: Crea una nueva versión inmutable de corrección.
     */
    @PostMapping({"/{id}/versions", "/{id}/versiones"})
    public ResponseEntity<ClinicalEncounterResponseDto> createCorrectionVersion(
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateClinicalVersionDto dto) {
        ClinicalEncounterResponseDto corrected = clinicalAttentionService.createCorrectionVersion(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(corrected);
    }

    /**
     * D01 / D05: Consulta el historial de atenciones clínicas de un paciente.
     */
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<ClinicalEncounterResponseDto>> getEncountersByPatient(
            @PathVariable("pacienteId") Long pacienteId) {
        List<ClinicalEncounterResponseDto> list = clinicalAttentionService.getEncountersByPatient(pacienteId);
        return ResponseEntity.ok(list);
    }

    /**
     * D01 / D05: Consulta una atención clínica específica por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClinicalEncounterResponseDto> getEncounterById(
            @PathVariable("id") Long id) {
        ClinicalEncounterResponseDto encounter = clinicalAttentionService.getEncounterById(id);
        return ResponseEntity.ok(encounter);
    }

    /**
     * D05: Consulta el historial de versiones inmutables de una atención clínica.
     */
    @GetMapping({"/{id}/versions", "/{id}/versiones"})
    public ResponseEntity<List<ClinicalVersionResponseDto>> getVersionsByEncounterId(
            @PathVariable("id") Long id) {
        List<ClinicalVersionResponseDto> versions = clinicalAttentionService.getVersionsByEncounterId(id);
        return ResponseEntity.ok(versions);
    }
}
