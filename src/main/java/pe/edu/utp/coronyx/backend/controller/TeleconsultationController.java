package pe.edu.utp.coronyx.backend.controller;

import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.TeleconsultationDtos.*;
import pe.edu.utp.coronyx.backend.service.TeleconsultationService;

@RestController
@RequestMapping("/api/v1/teleconsulta")
public class TeleconsultationController {

    private final TeleconsultationService service;

    public TeleconsultationController(TeleconsultationService service) {
        this.service = service;
    }

    @PostMapping("/sesion/{citaId}")
    public TeleconsultationJoinResponse join(@PathVariable Long citaId) {
        return service.joinSession(citaId);
    }

    @PostMapping("/sesion/{citaId}/cerrar")
    public TeleconsultationSessionResponse close(@PathVariable Long citaId) {
        return service.closeSession(citaId);
    }

    @GetMapping("/sesion/{citaId}")
    public TeleconsultationSessionResponse getStatus(@PathVariable Long citaId) {
        return service.getSessionStatus(citaId);
    }

    @PostMapping("/verificar")
    public VerifyTokenResponse verify(@RequestBody VerifyTokenRequest request) {
        return service.verifyRoomToken(request.salaId(), request.token());
    }
}
