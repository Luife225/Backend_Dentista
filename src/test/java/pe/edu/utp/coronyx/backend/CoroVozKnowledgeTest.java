package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp;
import pe.edu.utp.coronyx.backend.service.corovoz.DentalCommandParser;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CoroVozKnowledgeTest {
    @Test void evaluatesIndependentQuestionsAndUnknownTopics() throws Exception {
        var help = new CoroVozHelp();
        try (var input = getClass().getResourceAsStream("/corovoz/evaluation.tsv")) {
            assertNotNull(input);
            var failures = new java.util.ArrayList<String>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var fields = line.split("\\|", 2);
                var result = help.answer(fields[1], "ODONTOLOGO");
                if (!fields[0].equals(result.intent())) failures.add(fields[1] + " => " + result.intent() + " (esperado " + fields[0] + ")");
            }
            assertTrue(failures.isEmpty(), String.join("\n", failures));
        }
    }

    @Test void explainsRestrictedAccountsWithoutGivingRestrictedNavigation() {
        var help = new CoroVozHelp();
        for (String role : new String[]{"ODONTOLOGO", "RECEPCIONISTA"}) {
            var answer = help.answer("cómo veo usuarios inactivos", role);
            assertEquals("USUARIOS_INACTIVOS", answer.intent());
            assertTrue(answer.answer().contains("ADMIN_CLINICA"));
            assertTrue(answer.answer().contains("INACTIVO"));
            assertEquals("", answer.route());
        }
    }

    @Test void identifiesAllSpokenPermanentFdiTeethAndKeepsIncompleteFragments() {
        var parser = new DentalCommandParser();
        String[] first = {"once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho"};
        String[] units = {"uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho"};
        for (int q = 1; q <= 4; q++) for (int i = 1; i <= 8; i++) {
            String spoken = q == 1 ? first[i - 1] : (q == 2 ? "veinte" : q == 3 ? "treinta" : "cuarenta") + " y " + units[i - 1];
            var partial = parser.parse("pieza " + spoken);
            assertTrue(partial.incomplete(), spoken);
            assertTrue(partial.fragment().contains(Integer.toString(q * 10 + i)), partial.fragment());
            var full = parser.parse(partial.fragment() + " corona");
            assertTrue(full.understood(), full.message());
            assertEquals(q * 10 + i, full.proposal().piece());
        }
        assertTrue(parser.parse("caries cara oclusal").incomplete());
        assertFalse(parser.parse("no hay caries en pieza catorce").incomplete());
    }
}
