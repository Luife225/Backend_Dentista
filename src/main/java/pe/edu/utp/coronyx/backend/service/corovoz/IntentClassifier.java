package pe.edu.utp.coronyx.backend.service.corovoz;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Clasificador supervisado de centroides TF-IDF. Se entrena una vez al arrancar. */
public final class IntentClassifier {
    public record Example(String intent, String text) {}
    public record Prediction(String intent, double similarity, double margin, double coverage) {}
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "algo", "ayuda", "ayudame", "como", "con", "corovoz", "cual", "de", "del", "donde",
            "el", "ella", "en", "es", "esta", "este", "esto", "hacer", "hay", "la", "las", "lo", "los",
            "me", "mi", "mis", "necesito", "no", "para", "por", "puedo", "que", "quiero", "se", "si",
            "sistema", "sobre", "su", "tengo", "un", "una", "unos", "unas", "ver", "y", "yo");
    private final Map<String, Double> idf = new HashMap<>();
    private final Map<String, Map<String, Double>> centroids = new LinkedHashMap<>();

    public IntentClassifier(List<Example> examples) {
        if (examples.isEmpty()) throw new IllegalArgumentException("Faltan ejemplos para CoroVoz");
        List<Set<String>> documents = examples.stream().map(e -> tokens(e.text())).toList();
        Map<String, Integer> frequency = new HashMap<>();
        documents.forEach(d -> d.forEach(t -> frequency.merge(t, 1, Integer::sum)));
        frequency.forEach((t, count) -> idf.put(t, Math.log((1.0 + examples.size()) / (1.0 + count)) + 1));
        for (int i = 0; i < examples.size(); i++) {
            Map<String, Double> center = centroids.computeIfAbsent(examples.get(i).intent(), k -> new HashMap<>());
            vector(documents.get(i)).forEach((t, v) -> center.merge(t, v, Double::sum));
        }
        centroids.values().forEach(IntentClassifier::normalize);
    }

    public Prediction predict(String text) {
        Set<String> terms = tokens(text);
        Map<String, Double> query = vector(terms);
        String best = "UNKNOWN";
        double first = 0, second = 0;
        for (var entry : centroids.entrySet()) {
            double score = query.entrySet().stream()
                    .mapToDouble(t -> t.getValue() * entry.getValue().getOrDefault(t.getKey(), 0.0)).sum();
            if (score > first) { second = first; first = score; best = entry.getKey(); }
            else if (score > second) second = score;
        }
        double coverage = terms.isEmpty() ? 0 : (double) query.size() / terms.size();
        return new Prediction(best, first, first - second, coverage);
    }

    public static String plain(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> tokens(String text) {
        return Arrays.stream(plain(text).split(" ")).filter(t -> t.length() > 1 && !STOP_WORDS.contains(t))
                .map(t -> t.length() > 4 && t.endsWith("s") ? t.substring(0, t.length() - 1) : t)
                .collect(Collectors.toSet());
    }

    private Map<String, Double> vector(Set<String> terms) {
        Map<String, Double> result = new HashMap<>();
        terms.stream().filter(idf::containsKey).forEach(t -> result.put(t, idf.get(t)));
        normalize(result);
        return result;
    }

    private static void normalize(Map<String, Double> values) {
        double length = Math.sqrt(values.values().stream().mapToDouble(v -> v * v).sum());
        if (length > 0) values.replaceAll((t, v) -> v / length);
    }
}
