package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Typed bridge for the Python knowledge model. */
@Service
public class CoroVozHelp {
    public record PatientChoice(String id, String name, String documentHint) {}
    public record Suggestion(String intent, String label) {}
    public record AppointmentDraft(String date, String time, String patientId, String reason, String modality) {}
    public record Answer(String intent, String answer, String route, String targetLabel, boolean understood,
                         List<PatientChoice> patients, String patientId, String patientName, List<Suggestion> suggestions, AppointmentDraft appointmentDraft) {
        public Answer(String intent, String answer, String route, String label, boolean understood, List<PatientChoice> patients, String patientId, String patientName, List<Suggestion> suggestions) {
            this(intent, answer, route, label, understood, patients, patientId, patientName, suggestions, null);
        }
        public Answer(String intent, String answer, String route, String label, boolean understood) {
            this(intent, answer, route, label, understood, List.of(), null, null, List.of());
        }
        public Answer(String intent, String answer, String route, String label, boolean understood, List<PatientChoice> patients, String patientId) {
            this(intent, answer, route, label, understood, patients, patientId, null, List.of());
        }
        public Answer(String intent, String answer, String route, String label, boolean understood, List<PatientChoice> patients, String patientId, String patientName) {
            this(intent, answer, route, label, understood, patients, patientId, patientName, List.of());
        }
    }
    private final PythonCoroVozClient client;
    public CoroVozHelp() { this(new PythonCoroVozClient()); }
    @Autowired public CoroVozHelp(PythonCoroVozClient client) { this.client = client; }

    public Answer answer(String text, String role) {
        return answer(text, role, null, null, null);
    }

    public Answer answer(String text, String role, String selectedIntent) {
        return answer(text, role, selectedIntent, null, null);
    }

    public Answer answer(String text, String role, String selectedIntent, Long userId, Long clinicId) {
        try {
            var request = new HashMap<String, Object>(Map.of("text", text, "role", role));
            if (selectedIntent != null) request.put("selectedIntent", selectedIntent);
            if (userId != null) request.put("userId", userId);
            if (clinicId != null) request.put("clinicId", clinicId);
            var node = client.post("/general", request);
            return new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(node, Answer.class);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Respuesta inválida de CoroVoz", e); }
    }
}
