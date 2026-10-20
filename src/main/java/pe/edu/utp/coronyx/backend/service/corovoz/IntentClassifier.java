package pe.edu.utp.coronyx.backend.service.corovoz;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** TF-IDF: combina el ejemplo más cercano por categoría y su centroide. */
public final class IntentClassifier {
    public record Example(String intent, String text) {}
    public record Prediction(String intent, double similarity, double margin, double coverage) {}
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "algo", "ayuda", "ayudame", "como", "con", "cual", "de", "del", "donde",
            "el", "ella", "en", "es", "esta", "este", "esto", "hacer", "hay", "la", "las", "lo", "los",
            "me", "mi", "mis", "necesito", "no", "para", "por", "puedo", "que", "quiero", "se", "si",
            "sistema", "sobre", "su", "tengo", "un", "una", "unos", "unas", "ver", "y", "yo",
            "podria", "quisiera", "favor", "explicame", "dime", "puedes", "veo", "hago", "cuales",
            "explicas", "explicar", "hace", "funcion", "funciones", "estan", "persona", "personas", "usa",
            "utilizar", "utilizaria", "antes", "mas", "cada", "porque", "logro", "ya", "he", "ningun", "ninguno", "manana", "hoy");
    private static final Map<String, String> LEMMAS = new HashMap<>();
    static {
        aliases("odontologo", "doctor doctores profesional profesionales dentista dentistas odontologos");
        aliases("abrir", "abro abre entro entrar accedo acceder iniciar inicio iniciar");
        aliases("buscar", "busco busca busqueda encontrar encuentro localizar");
        aliases("registrar", "registro registra registrado registrada registrar registrando anadir agregar crear creo crea");
        aliases("reservar", "reservo reserva reservar programar programo agendar agendo agiendo agendar");
        aliases("cita", "citas turno turnos consulta consultas");
        aliases("inactivo", "inactiva inactivas inactivos deshabilitado deshabilitada deshabilitados desactivado desactivada");
        aliases("archivado", "archivada archivadas archivados archivar");
        aliases("activar", "activo activa reactivo reactiva reactivar habilitar habilita");
        aliases("desactivar", "desactivo desactiva bloquear bloquea deshabilitar");
        aliases("editar", "edito edita actualizar actualizo actualiza corregir corrijo corregir modificar modifico cambio cambiar");
        aliases("costo", "cuesta cuestan dinero pago pagar pagos cuesta cobran cobra cobro");
        aliases("esperar", "espere espera esperar esperas");
        aliases("responder", "responde responda responder respuesta respuestas");
        aliases("rol", "roles tipo tipos");
        aliases("acceso", "ingreso ingresar entrar login sesion");
        aliases("expirada", "expirado expiro expira vencida vencido caducada");
        aliases("archivo", "archivos adjuntos documento documentos");
        aliases("subir", "subo subir cargar cargo carga adjuntar adjunto anexar");
        aliases("confirmado", "confirmada confirmadas confirmados confirmar");
        aliases("error", "rechaza rechazan rechazado rechazada invalido invalida error errores");
        aliases("guardado", "guardar guardo guarda guardados guardadas guardan");
        aliases("interrumpir", "interrumpe interrumpa interrumpiendo interrumpir corta corte cortar corta");
    }
    private static void aliases(String canonical, String forms) { for (String form : forms.split(" ")) LEMMAS.put(form, canonical); }
    private final Map<String, Double> idf = new HashMap<>();
    private final Map<String, Map<String, Double>> centroids = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Double>>> neighbors = new LinkedHashMap<>();

    public IntentClassifier(List<Example> examples) {
        if (examples.isEmpty()) throw new IllegalArgumentException("Faltan ejemplos para CoroVoz");
        List<Set<String>> documents = examples.stream().map(e -> tokens(e.text())).toList();
        Map<String, Integer> frequency = new HashMap<>();
        documents.forEach(d -> d.forEach(t -> frequency.merge(t, 1, Integer::sum)));
        frequency.forEach((t, count) -> idf.put(t, Math.log((1.0 + examples.size()) / (1.0 + count)) + 1));
        for (int i = 0; i < examples.size(); i++) {
            Map<String, Double> center = centroids.computeIfAbsent(examples.get(i).intent(), k -> new HashMap<>());
            var sample = vector(documents.get(i));
            neighbors.computeIfAbsent(examples.get(i).intent(), k -> new ArrayList<>()).add(sample);
            sample.forEach((t, v) -> center.merge(t, v, Double::sum));
        }
        centroids.values().forEach(IntentClassifier::normalize);
    }

    public Prediction predict(String text) {
        Set<String> terms = tokens(text).stream().map(this::correctSingleEdit).collect(Collectors.toSet());
        Map<String, Double> query = vector(terms);
        if (terms.size() > 1 && query.size() < 2) return new Prediction("UNKNOWN", 0, 0, 0);
        String best = "UNKNOWN";
        double first = 0, second = 0;
        for (var entry : centroids.entrySet()) {
            double nearest = neighbors.get(entry.getKey()).stream().mapToDouble(v -> dot(query, v)).max().orElse(0);
            double score = 0.8 * nearest + 0.2 * dot(query, entry.getValue());
            if (score > first) { second = first; first = score; best = entry.getKey(); }
            else if (score > second) second = score;
        }
        double coverage = terms.isEmpty() ? 0 : (double) query.size() / terms.size();
        return new Prediction(best, first, first - second, coverage);
    }

    public static String plain(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim()
                .replaceAll("\\bcoronix\\b", "coronyx").replaceAll("\\bcoro voz\\b", "corovoz");
    }

    private static double dot(Map<String, Double> query, Map<String, Double> sample) {
        return query.entrySet().stream().mapToDouble(t -> t.getValue() * sample.getOrDefault(t.getKey(), 0.0)).sum();
    }

    private String correctSingleEdit(String term) {
        if (term.length() < 5 || idf.containsKey(term)) return term;
        List<String> candidates = idf.keySet().stream().filter(known -> oneEditAway(term, known)).limit(2).toList();
        return candidates.size() == 1 ? candidates.getFirst() : term;
    }

    private static boolean oneEditAway(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) { i++; j++; }
            else {
                if (++edits > 1) return false;
                if (left.length() >= right.length()) i++;
                if (right.length() >= left.length()) j++;
            }
        }
        return edits + left.length() - i + right.length() - j == 1;
    }

    private static Set<String> tokens(String text) {
        String normalized = plain(text).replaceAll("\\bya (?:esta )?registrad[oa]\\b", "duplicado");
        return Arrays.stream(normalized.split(" ")).filter(t -> t.length() > 1 && !STOP_WORDS.contains(t))
                .map(t -> LEMMAS.getOrDefault(t, t))
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
