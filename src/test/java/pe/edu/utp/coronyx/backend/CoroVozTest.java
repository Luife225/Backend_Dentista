package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.utp.coronyx.backend.controller.CoroVozController;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp;
import pe.edu.utp.coronyx.backend.service.corovoz.DentalCommandParser;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Sin base de datos ni llamadas externas. Frases de evaluación distintas del corpus. */
class CoroVozTest {
    private final CoroVozHelp help = new CoroVozHelp();
    private final DentalCommandParser parser = new DentalCommandParser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Quisiera dar de alta a un paciente nuevo|PACIENTE_NUEVO",
            "Necesito encontrar la ficha de un paciente|PACIENTE_BUSCAR",
            "Dónde puedo reservar una cita para mañana|CITA_NUEVA",
            "Debo cambiar la fecha de una cita|CITA_CAMBIO",
            "Quiero cargar un documento adjunto|DOCUMENTOS",
            "Cómo reviso mis avisos pendientes|NOTIFICACIONES",
            "Quiero marcar una corona en el odontograma|ODONTOGRAMA",
            "Dónde abro la historia clínica del paciente|HISTORIA",
            "Cómo puedo activar el micrófono del asistente|COROVOZ",
            "Dónde encuentro los indicadores del dashboard|DASHBOARD",
            "Cómo consulto el resumen de caja|CAJA",
            "Necesito iniciar una consulta virtual|TELEODONTOLOGIA",
            "Como agiendo una cita|CITA_NUEVA",
            "Como puedo programar una cita|CITA_NUEVA"
    })
    void recognizesHeldOutQuestions(String question, String intent) {
        var answer = help.answer(question, "ODONTOLOGO");
        assertTrue(answer.understood(), question + ": " + answer);
        assertEquals(intent, answer.intent());
    }

    @Test void refusesUnknownAndClinicalAdvice() {
        for (String text : new String[]{"dime el clima de mañana", "qué medicina tomo para dolor de muela", "asdasdf xyz"}) {
            var answer = help.answer(text, "ODONTOLOGO");
            assertFalse(answer.understood(), text);
            assertEquals("", answer.route());
        }
    }

    @Test void receptionistCannotNavigateToClinicalHelp() {
        var answer = help.answer("cómo llenar el odontograma", "RECEPCIONISTA");
        assertEquals("", answer.route());
        assertTrue(answer.answer().contains("reservada"));
        assertEquals("", help.answer("ver ingresos y egresos", "RECEPCIONISTA").route());
        assertEquals("", help.answer("abrir inventario", "ODONTOLOGO").route());
        assertEquals("/app/inventario", help.answer("quiero consultar el stock bajo de materiales", "RECEPCIONISTA").route());
    }

    @Test void parsesSpokenNumbersAndOnlyDictatedNotes() {
        var result = parser.parse("Caries en diente treinta y seis cara oclusal. Observación: control en 2 semanas.");
        assertTrue(result.understood());
        assertEquals(36, result.proposal().piece());
        assertEquals("CARIES", result.proposal().condition());
        assertEquals("OCLUSAL", result.proposal().surface());
        assertEquals("control en 2 semanas.", result.proposal().note());
    }

    @Test void acceptsBoundariesAndMixedNumbersObservedInRealTranscription() {
        var crown = parser.parse("Corona empieza a catorce.");
        assertTrue(crown.understood(), crown.message());
        assertEquals(14, crown.proposal().piece());
        assertEquals("CORONA", crown.proposal().condition());
        var caries = parser.parse("Caries empieza 30 y 6 carooclusal.");
        assertTrue(caries.understood(), caries.message());
        assertEquals(36, caries.proposal().piece());
        assertEquals("OCLUSAL", caries.proposal().surface());
        assertEquals(36, parser.parse("caries en pieza 30 y seis cara oclusal").proposal().piece());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Corona en pieza catorce|14", "Implante en pieza cuatro ocho|48", "Fractura en diente 2 1|21", "Restauración en pieza veintiséis|26"})
    void acceptsFdiNumberVariants(String text, int piece) {
        var result = parser.parse(text);
        assertTrue(result.understood(), result.message());
        assertEquals(piece, result.proposal().piece());
        assertEquals("GENERAL", result.proposal().surface());
    }

    @Test void selectionDoesNotCreateAClinicalFinding() {
        for (String text : List.of("Selecciona el catorce", "Elige la pieza 14", "Selecciona el diente número uno cuatro")) {
            var result = parser.parse(text);
            assertTrue(result.understood(), result.message());
            assertEquals(14, result.selectedPiece());
            assertNull(result.proposal());
        }
        for (String text : List.of("Selecciona el diente dos", "Selecciona el diente 3")) {
            var result = parser.parse(text);
            assertFalse(result.understood());
            assertNull(result.selectedPiece());
        }
        var correction = parser.parse("No, no era el catorce, era el trece");
        assertTrue(correction.understood(), correction.message());
        assertEquals(13, correction.selectedPiece());
        assertEquals(14, correction.correctedFrom());
        assertNull(correction.proposal());
    }

    @Test void rejectsAmbiguityInvalidTeethNegationsAndDeletion() {
        for (String text : new String[]{"caries en 14 y 15", "corona en 99", "caries sin pieza", "no hay caries en 14", "caries y corona en 14", "caries en 14 mesial y distal", "elimina caries en 14", "necesita corona en 14", "caries en 14 cara incisal", "caries profunda en 14", "caries en 14 superficie rara"}) {
            var result = parser.parse(text);
            assertFalse(result.understood(), text);
            assertNull(result.proposal());
        }
    }

    @Test void checksRolesAndClinicBeforeInterpreting() {
        var controller = new CoroVozController(help, parser, mock(pe.edu.utp.coronyx.backend.service.corovoz.CoroVozPatientQueries.class));
        var reception = mock(UserPrincipal.class);
        when(reception.getRol()).thenReturn("RECEPCIONISTA");
        when(reception.hasOperativeClinic()).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> controller.interpret(new CoroVozController.Prompt("corona en 14"), reception));
        assertThrows(AccessDeniedException.class, () -> controller.general(new CoroVozController.Prompt("agenda"), null));
        var doctor = mock(UserPrincipal.class);
        when(doctor.getRol()).thenReturn("ODONTOLOGO");
        when(doctor.hasOperativeClinic()).thenReturn(true);
        assertTrue(controller.interpret(new CoroVozController.Prompt("corona en 14"), doctor).understood());
        when(doctor.hasOperativeClinic()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.interpret(new CoroVozController.Prompt("corona en 14"), doctor));
    }
}
