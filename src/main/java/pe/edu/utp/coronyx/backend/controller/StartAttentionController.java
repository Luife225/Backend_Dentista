package pe.edu.utp.coronyx.backend.controller;

import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.ClinicalEncounterResponseDto;
import pe.edu.utp.coronyx.backend.service.ClinicalAttentionService;

@RestController
@RequestMapping("/api/v1/citas")
public class StartAttentionController {
    private final ClinicalAttentionService service;

    public StartAttentionController(ClinicalAttentionService service) { this.service = service; }

    @PostMapping("/{id}/iniciar-atencion")
    public ClinicalEncounterResponseDto start(@PathVariable Long id) {
        return service.startAppointment(id);
    }
}
