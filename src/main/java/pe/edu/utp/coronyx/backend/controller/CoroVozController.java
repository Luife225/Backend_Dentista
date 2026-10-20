package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp;
import pe.edu.utp.coronyx.backend.service.corovoz.DentalCommandParser;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozPatientQueries;

@RestController
@RequestMapping("/api/v1/corovoz")
public class CoroVozController {
    private final CoroVozHelp help;
    private final DentalCommandParser parser;
    private final CoroVozPatientQueries patients;

    public CoroVozController(CoroVozHelp help, DentalCommandParser parser, CoroVozPatientQueries patients) {
        this.help = help;
        this.parser = parser;
        this.patients = patients;
    }

    public record Prompt(@NotBlank @Size(max = 600) String text,
                         @Pattern(regexp = "[1-9][0-9]{0,17}") String patientId) {
        public Prompt(String text) { this(text, null); }
    }

    @PostMapping("/general")
    public CoroVozHelp.Answer general(@Valid @RequestBody Prompt prompt,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, "ODONTOLOGO", "RECEPCIONISTA");
        if (patients.handles(prompt.text())) return patients.answer(prompt.text(), prompt.patientId(), principal);
        return help.answer(prompt.text(), principal.getRol());
    }

    @PostMapping("/odontograma/interpretar")
    public DentalCommandParser.Result interpret(@Valid @RequestBody Prompt prompt,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, "ODONTOLOGO");
        return parser.parse(prompt.text());
    }

    private void requireRole(UserPrincipal principal, String... roles) {
        if (principal != null && principal.hasOperativeClinic()) {
            for (String role : roles) if (role.equals(principal.getRol())) return;
        }
        throw new AccessDeniedException("Este modo de CoroVoz no está disponible para tu sesión");
    }
}
