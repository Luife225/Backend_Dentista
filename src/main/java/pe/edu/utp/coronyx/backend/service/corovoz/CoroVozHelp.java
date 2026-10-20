package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Respuestas verificadas contra los módulos y permisos del sistema. */
@Service
public class CoroVozHelp {
    public record PatientChoice(String id, String name, String documentHint) {}
    public record Answer(String intent, String answer, String route, String targetLabel, boolean understood,
                         List<PatientChoice> patients, String patientId, String patientName) {
        public Answer(String intent, String answer, String route, String label, boolean understood) {
            this(intent, answer, route, label, understood, List.of(), null, null);
        }
        public Answer(String intent, String answer, String route, String label, boolean understood, List<PatientChoice> patients, String patientId) {
            this(intent, answer, route, label, understood, patients, patientId, null);
        }
    }
    private record Article(String roles, String route, String label, String title, String answer) {}
    private final IntentClassifier classifier;
    private final Map<String, Article> articles = new LinkedHashMap<>();

    public CoroVozHelp() {
        try {
            for (String line : lines("corovoz/knowledge.tsv")) {
                String[] p = line.split("\\|", 6);
                if (p.length != 6 || articles.put(p[0], new Article(p[1], p[2], p[3], p[4], p[5])) != null)
                    throw new IllegalStateException("Artículo duplicado o incompleto: " + line);
            }
            List<IntentClassifier.Example> examples = lines("corovoz/training.tsv").stream()
                    .map(l -> l.split("\\|", 2)).map(p -> new IntentClassifier.Example(p[0], p[1])).toList();
            if (examples.stream().anyMatch(e -> !articles.containsKey(e.intent())) ||
                    articles.keySet().stream().anyMatch(i -> examples.stream().noneMatch(e -> i.equals(e.intent()))))
                throw new IllegalStateException("Los artículos y el corpus de CoroVoz no coinciden");
            classifier = new IntentClassifier(examples);
        } catch (IOException e) { throw new IllegalStateException("No se pudo cargar CoroVoz", e); }
    }

    private List<String> lines(String path) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        }
    }

    public Answer answer(String text, String role) {
        if (!Set.of("ODONTOLOGO", "RECEPCIONISTA").contains(role))
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado para CoroVoz");
        var prediction = classifier.predict(text);
        Article article = articles.get(prediction.intent());
        if (article == null || prediction.similarity() < 0.36 || prediction.margin() < 0.035 || prediction.coverage() < 0.45)
            return new Answer("UNKNOWN", "No encontré una respuesta suficientemente clara. Puedo ayudarte con citas, pacientes, documentos, odontogramas, usuarios y permisos, acceso o problemas de micrófono. Para datos reales prueba «¿Cuándo es la próxima cita del paciente [nombre o DNI]?». Si buscas instrucciones, indica la tarea que necesitas hacer.", "", "", false);
        if (!article.roles().equals("*") && !List.of(article.roles().split(",")).contains(role))
            return new Answer(prediction.intent(), "Esta función está reservada a " + article.roles() + ". " + article.answer(), "", "", true);
        return new Answer(prediction.intent(), article.answer(), article.route(), article.label(), true);
    }
}
