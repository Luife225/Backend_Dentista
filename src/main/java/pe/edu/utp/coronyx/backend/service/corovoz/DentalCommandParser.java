package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Gramática acotada: extrae datos dictados, sin inferir diagnósticos ni escribir en BD. */
@Service
public class DentalCommandParser {
    public record Proposal(int piece, String surface, String condition, String note) {}
    public record Result(boolean understood, String message, Proposal proposal) {}
    private static final Set<Integer> TEETH = Set.of(
            11, 12, 13, 14, 15, 16, 17, 18, 21, 22, 23, 24, 25, 26, 27, 28,
            31, 32, 33, 34, 35, 36, 37, 38, 41, 42, 43, 44, 45, 46, 47, 48);
    private static final Map<String, String> CONDITIONS = Map.of(
            "CARIES", "caries|cariado|cariada",
            "RESTAURADO", "restaurado|restaurada|restauracion|obturacion|obturado|obturada",
            "CORONA", "corona",
            "EXTRAIDO", "extraido|extraida|extraccion",
            "ENDODONCIA", "endodoncia|endodontico|endodontica",
            "IMPLANTE", "implante",
            "FRACTURA", "fractura|fracturado|fracturada");
    private static final Map<String, String> SURFACES = Map.of(
            "OCLUSAL", "oclusal", "VESTIBULAR", "vestibular|bucal", "LINGUAL", "lingual|palatina|palatino",
            "MESIAL", "mesial", "DISTAL", "distal", "GENERAL", "general|completo|completa");
    private static final Map<String, String> NUMBERS = new LinkedHashMap<>();
    static {
        String[] units = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho"};
        String[] teens = {"once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho"};
        String[] twenties = {"veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho"};
        for (int i = 1; i <= 8; i++) {
            NUMBERS.put("cuarenta y " + units[i], "4" + i);
            NUMBERS.put("treinta y " + units[i], "3" + i);
            NUMBERS.put(teens[i - 1], "1" + i);
            NUMBERS.put(twenties[i - 1], "2" + i);
            for (int q = 1; q <= 4; q++) NUMBERS.put(units[q] + " " + units[i], "" + q + i);
        }
    }

    public Result parse(String input) {
        if (input == null || input.isBlank() || input.length() > 600) return unclear("Escribe o dicta una pieza y su condición.");
        String[] parts = input.split("(?iu)\\b(?:observaci[oó]n|nota)\\s*:?\\s*", 2);
        String command = IntentClassifier.plain(parts[0]);
        String note = parts.length > 1 ? parts[1].trim() : "";
        if (note.length() > 300) return unclear("La observación puede tener hasta 300 caracteres.");
        if (Pattern.compile("\\b(no|sin|posible|sospecha|descartar|descarta|elimina|eliminar|borra|borrar|quita|quitar|necesita|requiere|pendiente|planificar)\\b").matcher(command).find()) {
            return unclear("La frase contiene una negación o duda. Dicta solo el hallazgo que deseas registrar.");
        }
        for (var number : NUMBERS.entrySet()) command = command.replaceAll("\\b" + number.getKey() + "\\b", number.getValue());
        // También admite «pieza 1 4» producido por algunos reconocedores.
        command = command.replaceAll("\\b([1-4]) ([1-8])\\b", "$1$2");
        var matcher = Pattern.compile("\\b\\d+\\b").matcher(command);
        List<String> numbers = matcher.results().map(m -> m.group()).toList();
        if (numbers.size() != 1 || numbers.getFirst().length() != 2) {
            return unclear("Indica una sola pieza permanente con numeración FDI, por ejemplo «pieza 14».");
        }
        int piece = Integer.parseInt(numbers.getFirst());
        if (!TEETH.contains(piece)) return unclear("Esa pieza no está disponible en el odontograma actual. Usa 11–18, 21–28, 31–38 o 41–48.");
        List<String> conditions = matches(command, CONDITIONS);
        if (conditions.size() != 1) return unclear("Indica una sola condición: caries, restauración, corona, extraído, endodoncia, implante o fractura.");
        List<String> surfaces = matches(command, SURFACES);
        if (surfaces.size() > 1) return unclear("Dicta una sola cara por vez: oclusal, vestibular, lingual, mesial, distal o general.");
        String remainder = command;
        for (String aliases : CONDITIONS.values()) remainder = remainder.replaceAll("\\b(?:" + aliases + ")\\b", " ");
        for (String aliases : SURFACES.values()) remainder = remainder.replaceAll("\\b(?:" + aliases + ")\\b", " ");
        remainder = remainder.replaceAll("\\b(?:\\d+|pieza|dental|diente|numero|en|el|la|cara|superficie|con|un|una|de|del|al|coro|voz|corovoz|registrar|registra|marcar|marca|agregar|agrega|poner|tiene|presenta|hay|por|favor|odontograma)\\b", " ").trim();
        if (!remainder.isBlank()) return unclear("Hay palabras que no pude interpretar. Usa pieza, condición y una cara válida; agrega detalles después de «observación».");
        String surface = surfaces.isEmpty() ? "GENERAL" : surfaces.getFirst();
        return new Result(true, "Revisa la propuesta antes de agregarla.", new Proposal(piece, surface, conditions.getFirst(), note));
    }

    private List<String> matches(String text, Map<String, String> aliases) {
        return aliases.entrySet().stream().filter(e -> Pattern.compile("\\b(?:" + e.getValue() + ")\\b")
                .matcher(text).find()).map(Map.Entry::getKey).toList();
    }

    private Result unclear(String message) { return new Result(false, message, null); }
}
