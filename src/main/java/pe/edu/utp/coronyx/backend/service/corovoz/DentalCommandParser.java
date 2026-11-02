package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/** Typed bridge for Python's conservative dental parser. */
@Service
public class DentalCommandParser {
    public record Proposal(int piece, String surface, String condition, String note) {}
    public record Suggestion(String value, String label) {}
    public record Draft(Integer piece, String condition, String surface, String note) {}
    public record Result(boolean understood, String message, Proposal proposal, boolean incomplete, String fragment, Integer selectedPiece, Integer correctedFrom, java.util.List<Suggestion> suggestions, Integer contextPiece, Draft draft) {}
    private final PythonCoroVozClient client;
    public DentalCommandParser() { this(new PythonCoroVozClient()); }
    @Autowired public DentalCommandParser(PythonCoroVozClient client) { this.client = client; }
    public Result parse(String text) { return parse(text, null); }
    public Result revise(String text, String field, Map<String, Object> draft) {
        try {
            var node = client.post("/odontograma/editar", Map.of("text", text, "field", field, "draft", draft, "role", "ODONTOLOGO"));
            return new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(node, Result.class);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo revisar la propuesta", e); }
    }
    public Result parse(String text, Integer contextPiece) {
        var request = new HashMap<String, Object>();
        request.put("text", text);
        request.put("contextPiece", contextPiece);
        request.put("role", "ODONTOLOGO");
        try {
            var node = client.post("/odontograma/interpretar", request);
            return new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(node, Result.class);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interpretación inválida de CoroVoz", e); }
    }
}
