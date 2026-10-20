package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Appointment;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.PatientService;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozPatientQueries;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoroVozPatientTest {
    private final PatientService patients = mock(PatientService.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final CoroVozPatientQueries service = new CoroVozPatientQueries(patients, appointments);
    private UserPrincipal principal(String role) {
        var p = mock(UserPrincipal.class);
        when(p.getRol()).thenReturn(role); when(p.hasOperativeClinic()).thenReturn(true); when(p.getClinicaId()).thenReturn(8L);
        return p;
    }
    private PatientDto patient(String id, String name) {
        var p = new PatientDto(); p.setId(id); p.setNombres(name); p.setApellidos("Pérez"); p.setNumeroDocumento("1234567" + id);
        p.setTelefono("5551234"); p.setCorreo("demo@example.test"); p.setEstado("ACTIVO"); p.setAlergias("DATO CLÍNICO PRIVADO");
        return p;
    }
    private Appointment appointment(String date, String state) {
        var a = new Appointment(); a.setInicioEn(OffsetDateTime.parse(date)); a.setEstado(state); a.setModalidad("PRESENCIAL"); return a;
    }

    @Test void routesAdministrativeQuestionsAndLeavesInstructionsToKnowledgeModel() {
        for (String text : List.of("próxima cita de Ana Pérez", "¿cuándo será su próxima cita?", "teléfono del paciente Ana", "información del paciente Juan", "y su correo")) assertTrue(service.handles(text), text);
        for (String text : List.of("qué es coronix", "qué es corovoz", "cómo veo usuarios inactivos", "cómo cambiar teléfono del paciente", "cómo registro un paciente", "cómo consulto historia clínica", "última versión del odontograma", "teléfono de la clínica")) assertFalse(service.handles(text), text);
    }

    @Test void nextAppointmentIsActualNearestActiveRecordScopedToClinicForBothRoles() {
        for (String role : List.of("ODONTOLOGO", "RECEPCIONISTA")) {
            var session = principal(role); var patient = patient("1", "Ana");
            when(patients.getAllPatients(session)).thenReturn(List.of(patient)); when(patients.getPatientById(1L, session)).thenReturn(patient);
            when(appointments.findByPacienteIdAndClinicaIdOrderByInicioEnDesc(1L, 8L)).thenReturn(List.of(
                    appointment("2099-05-06T15:00:00Z", "PROGRAMADA"), appointment("2099-05-01T15:00:00Z", "CANCELADA"),
                    appointment("2099-05-02T15:00:00Z", "REPROGRAMADA"), appointment("2099-05-03T15:00:00Z", "CONFIRMADA")));
            var answer = service.answer("a qué hora será la próxima cita del paciente Ana Pérez", null, session);
            assertEquals("PACIENTE_DATOS", answer.intent()); assertEquals("1", answer.patientId());
            assertTrue(answer.answer().contains("3 de mayo de 2099 a las 10:00"), answer.answer());
            assertFalse(answer.answer().contains("DATO CLÍNICO"));
        }
    }

    @Test void ambiguousNamesRequireSelectionAndMissingIdentityDoesNotListEveryone() {
        var session = principal("RECEPCIONISTA");
        var missing = service.answer("¿cuándo será su próxima cita?", null, session);
        assertEquals("PACIENTE_ELEGIR", missing.intent()); verifyNoInteractions(patients, appointments);
        when(patients.getAllPatients(session)).thenReturn(List.of(patient("1", "Ana"), patient("2", "Ana")));
        var choices = service.answer("próxima cita de Ana", null, session);
        assertEquals(2, choices.patients().size()); assertNull(choices.patientId()); verifyNoInteractions(appointments);
        assertFalse(choices.patients().getFirst().documentHint().contains("12345671"));
    }

    @Test void selectedPatientIsRevalidatedAndNewNameDoesNotReusePreviousPerson() {
        var session = principal("ODONTOLOGO"); var ana = patient("1", "Ana"); var luis = patient("2", "Luis");
        when(patients.getPatientById(1L, session)).thenReturn(ana); when(patients.getPatientById(2L, session)).thenReturn(luis);
        when(patients.getAllPatients(session)).thenReturn(List.of(ana, luis));
        assertEquals("1", service.answer("y su teléfono", "1", session).patientId());
        assertEquals("2", service.answer("teléfono del paciente Luis", "1", session).patientId());
        when(patients.getPatientById(99L, session)).thenThrow(new NoSuchElementException());
        assertEquals("PACIENTE_ELEGIR", service.answer("y su teléfono", "99", session).intent());
        verifyNoInteractions(appointments);
    }

    @Test void noNextAppointmentDoesNotInventOneAndClinicalDataIsNeverReturned() {
        var session = principal("RECEPCIONISTA"); var ana = patient("1", "Ana");
        when(patients.getPatientById(1L, session)).thenReturn(ana);
        var answer = service.answer("su próxima cita", "1", session);
        assertTrue(answer.answer().contains("No tiene una próxima cita activa"));
        var clinical = service.answer("alergias del paciente Ana", null, session);
        assertFalse(clinical.answer().contains("DATO CLÍNICO")); assertEquals("", clinical.route());
        assertThrows(AccessDeniedException.class, () -> service.answer("teléfono de Ana", null, principal("PACIENTE")));
        assertThrows(AccessDeniedException.class, () -> service.answer("teléfono de Ana", null, null));
    }
}
