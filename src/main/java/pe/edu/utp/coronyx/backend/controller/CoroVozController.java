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
                         @Pattern(regexp = "[1-9][0-9]{0,17}") String patientId,
                         Integer contextPiece,
                         @Size(max = 80) String selectedIntent,
                         Boolean patientConfirmed) {
        public Prompt(String text) { this(text, null, null, null, false); }
        public Prompt(String text, String patientId, Integer contextPiece) { this(text, patientId, contextPiece, null, false); }
        public Prompt(String text, String patientId, Integer contextPiece, String selectedIntent) { this(text, patientId, contextPiece, selectedIntent, false); }
    }

    @PostMapping("/general")
    public CoroVozHelp.Answer general(@Valid @RequestBody Prompt prompt,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, "ODONTOLOGO", "RECEPCIONISTA");
        if (prompt.selectedIntent() != null) return help.answer(prompt.text(), principal.getRol(), prompt.selectedIntent(), principal.getUserId(), principal.getClinicaId());
        if (patients.handles(prompt.text())) return patients.answer(prompt.text(), prompt.patientId(), principal, Boolean.TRUE.equals(prompt.patientConfirmed()));
        if (Boolean.TRUE.equals(prompt.patientConfirmed())) return patients.answer("Resumen del paciente", prompt.patientId(), principal, true);
        var nameChoice = patients.suggestName(prompt.text(), principal);
        if (nameChoice != null) return nameChoice;
        return help.answer(prompt.text(), principal.getRol(), null, principal.getUserId(), principal.getClinicaId());
    }

    @PostMapping("/odontograma/interpretar")
    public DentalCommandParser.Result interpret(@Valid @RequestBody Prompt prompt,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, "ODONTOLOGO");
        return parser.parse(prompt.text(), prompt.contextPiece());
    }

    public record Revision(@NotBlank @Size(max = 600) String text,
                           @NotBlank @Pattern(regexp = "piece|condition|note|surface") String field,
                           @jakarta.validation.constraints.NotNull @Size(max = 4) java.util.Map<String, Object> draft) {}

    @PostMapping("/odontograma/editar")
    public DentalCommandParser.Result revise(@Valid @RequestBody Revision revision,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, "ODONTOLOGO");
        return parser.revise(revision.text(), revision.field(), revision.draft());
    }

    private void requireRole(UserPrincipal principal, String... roles) {
        if (principal != null && principal.hasOperativeClinic()) {
            for (String role : roles) if (role.equals(principal.getRol())) return;
        }
        throw new AccessDeniedException("Este modo de CoroVoz no está disponible para tu sesión");
    }
}
