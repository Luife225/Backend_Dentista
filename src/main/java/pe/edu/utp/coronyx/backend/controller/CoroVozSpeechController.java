package pe.edu.utp.coronyx.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.corovoz.PythonCoroVozClient;

import java.io.IOException;
import java.util.Set;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/corovoz")
public class CoroVozSpeechController {
    private static final int MAX_AUDIO_BYTES = 960_044;
    private final PythonCoroVozClient speech;
    public CoroVozSpeechController(PythonCoroVozClient speech) { this.speech = speech; }
    public record Status(int version, boolean speechReady) {}
    public record Transcript(String text) {}

    @GetMapping("/status")
    public Status status(@AuthenticationPrincipal UserPrincipal principal) {
        authorize(principal);
        var status = speech.get("/status");
        return new Status(status.path("version").asInt(), status.path("speechReady").asBoolean());
    }

    @PostMapping(value = "/transcribir", consumes = "audio/wav")
    public Transcript transcribe(HttpServletRequest request, @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        authorize(principal);
        byte[] bytes = request.getInputStream().readNBytes(MAX_AUDIO_BYTES + 1);
        if (bytes.length > MAX_AUDIO_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El audio supera 30 segundos");
        return new Transcript(speech.audio(bytes).path("text").asText());
    }

    private void authorize(UserPrincipal principal) {
        if (principal == null || !principal.hasOperativeClinic() || !Set.of("ODONTOLOGO", "RECEPCIONISTA").contains(principal.getRol())) {
            throw new AccessDeniedException("Sesión no autorizada para CoroVoz");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> speechError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason() == null ? "No se pudo procesar la voz" : error.getReason()));
    }
}
