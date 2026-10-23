package pe.edu.utp.coronyx.backend.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.AppointmentResponseDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.service.AppointmentService;

@RestController
@RequestMapping("/api/v1/citas")
public class TeleconsultationLinkController {
    public record LinkRequest(@NotBlank @Size(max=500) String enlace) {}
    private final AppointmentService service;
    public TeleconsultationLinkController(AppointmentService service) { this.service=service; }
    @PutMapping("/{id}/teleconsulta")
    public AppointmentResponseDto update(@PathVariable Long id, @Valid @RequestBody LinkRequest dto) {
        return service.updateTeleconsultationLink(id,dto.enlace(),SecurityUtils.getCurrentPrincipal());
    }
}
