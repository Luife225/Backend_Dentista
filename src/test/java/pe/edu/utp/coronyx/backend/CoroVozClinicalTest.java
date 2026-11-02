package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.*;
import pe.edu.utp.coronyx.backend.service.corovoz.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoroVozClinicalTest {
    static { CoroVozPythonTestServer.ensureStarted(); }
    private final PatientService patients = mock(PatientService.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final ClinicalAttentionService history = mock(ClinicalAttentionService.class);
    private final OdontogramService odontograms = mock(OdontogramService.class);
    private final CoroVozClinicalQueries clinical = new CoroVozClinicalQueries(attachments, history, odontograms);
    private final CoroVozPatientQueries service = new CoroVozPatientQueries(patients, appointments, new PythonCoroVozClient(), clinical);

    private UserPrincipal session(String role) {
        var principal = mock(UserPrincipal.class);
        when(principal.getRol()).thenReturn(role); when(principal.hasOperativeClinic()).thenReturn(true);
        when(principal.getClinicaId()).thenReturn(8L); return principal;
    }
    private PatientDto patient(String id, String first, String last) {
        var p = new PatientDto(); p.setId(id); p.setNombres(first); p.setApellidos(last);
        p.setAlergias("Penicilina"); p.setMedicamentos("Registro de medicación"); return p;
    }
    private PatientDto prepare(UserPrincipal principal) {
        var patient = patient("1", "Carlos", "Gamboa");
        when(patients.getAllPatients(principal)).thenReturn(List.of(patient));
        when(patients.getPatientById(1L, principal)).thenReturn(patient); return patient;
    }

    @Test void doctorReadsActualAllergiesAndMissingMeansUnrecorded() {
        var principal = session("ODONTOLOGO"); var p = prepare(principal);
        var answer = service.answer("Carlos Gamboa tiene alguna alergia registrada", null, principal);
        assertEquals("1", answer.patientId()); assertTrue(answer.answer().contains("Penicilina"));
        p.setAlergias("");
        assertTrue(service.answer("y sus alergias", "1", principal).answer().contains("no hay información registrada"));
        verifyNoInteractions(appointments, attachments, history);
    }
    @Test void receptionCannotReadClinicalFieldsEvenWithExplicitSelection() {
        var principal = session("RECEPCIONISTA");
        for (String field : List.of("alergias", "radiografías", "antecedentes", "historia clínica", "medicamentos", "odontograma")) {
            var answer = service.answer(field + " de Carlos Gamboa", "1", principal, true);
            assertTrue(answer.answer().contains("rol odontólogo")); assertNull(answer.patientId());
        }
        verifyNoInteractions(patients, appointments, attachments, history, odontograms);
    }
    @Test void pronounsUseOnlyTheSelectedPatientAndNamesCanChangeIt() {
        var principal = session("ODONTOLOGO"); prepare(principal);
        for (String question : List.of("ella tiene alergias", "y él presenta alergias", "alergias de esa misma paciente")) {
            var answer = service.answer(question, "1", principal);
            assertEquals("1", answer.patientId());
            assertTrue(answer.answer().contains("Penicilina"));
        }
        verify(patients, never()).getAllPatients(principal);
        assertEquals("PACIENTE_ELEGIR", service.answer("ella tiene alergias", null, principal).intent());
        var other = patient("2", "Ana", "Pérez");
        when(patients.getAllPatients(principal)).thenReturn(List.of(other));
        when(patients.getPatientById(2L, principal)).thenReturn(other);
        assertEquals("2", service.answer("alergias de Ana Pérez", "1", principal).patientId());
    }
    @Test void reportsRadiographPresenceAndAbsenceFromAuthorizedAttachments() {
        var principal = session("ODONTOLOGO"); prepare(principal);
        var xray = new AttachmentResponseDto(); xray.setTipoArchivo("RADIOGRAFIA"); xray.setNombreOriginal("Panoramica.pdf");
        var consent = new AttachmentResponseDto(); consent.setTipoArchivo("CONSENTIMIENTO"); consent.setNombreOriginal("Consentimiento.pdf");
        when(attachments.listByPatient(1L, principal)).thenReturn(List.of(xray, consent));
        var answer = service.answer("Carlos Gamboa presenta radiografías", null, principal);
        assertTrue(answer.answer().contains("Sí, tiene 1 radiografías"));
        assertTrue(answer.answer().contains("Panoramica.pdf")); assertFalse(answer.answer().contains("Consentimiento.pdf"));
        when(attachments.listByPatient(1L, principal)).thenReturn(List.of(consent));
        assertTrue(service.answer("y sus radiografías", "1", principal).answer().contains("No hay radiografías"));
        verifyNoInteractions(appointments, history);
    }
    @Test void administrativeDocumentsUseTheSameAuthorizedAttachmentService() {
        var principal = session("RECEPCIONISTA"); prepare(principal);
        when(attachments.listByPatient(1L, principal)).thenReturn(List.of());
        assertTrue(service.answer("documentos de Carlos Gamboa", null, principal).answer().contains("No hay archivos"));
        verify(attachments).listByPatient(1L, principal);
    }
    @Test void surnamesAndTranscriptionErrorsAlwaysAskBeforeReadingClinicalRecords() {
        var principal = session("ODONTOLOGO");
        var sofia = patient("2", "Sofía", "Castillo");
        when(patients.getAllPatients(principal)).thenReturn(List.of(sofia));
        when(patients.getPatientById(2L, principal)).thenReturn(sofia);
        for (String question : List.of("alergias de Castillo", "alergias de Sofia Castilo", "Quería saber las alergias de Sofía Castillo mientras reviso todo")) {
            var choice = service.answer(question, null, principal);
            assertEquals("PACIENTE_ELEGIR", choice.intent()); assertEquals("2", choice.patients().getFirst().id());
            assertFalse(choice.answer().contains("Penicilina"));
            assertTrue(service.answer(question, "2", principal, true).answer().contains("Penicilina"));
        }
        assertEquals("2", service.suggestName("Castillo", principal).patients().getFirst().id());
    }
    @Test void forgedSelectionCannotCrossClinicBoundary() {
        var principal = session("ODONTOLOGO");
        when(patients.getPatientById(99L, principal)).thenThrow(new NoSuchElementException());
        assertEquals("PACIENTE_ELEGIR", service.answer("sus radiografías", "99", principal, true).intent());
        verifyNoInteractions(attachments, history, appointments);
    }
    @Test void historyUsesConfirmedVersionAndClearlyIdentifiesSource() {
        var principal = session("ODONTOLOGO"); prepare(principal);
        var draft = new ClinicalVersionResponseDto(); draft.setEstaConfirmado(false); draft.setDiagnostico("BORRADOR PRIVADO");
        var signed = new ClinicalVersionResponseDto(); signed.setEstaConfirmado(true); signed.setDiagnostico("Diagnóstico registrado");
        signed.setNumeroVersion(1); signed.setAtencionClinicaId(3L); signed.setFechaConfirmacion(OffsetDateTime.parse("2026-10-20T15:00:00Z"));
        var encounter = new ClinicalEncounterResponseDto(); encounter.setVersiones(List.of(draft, signed));
        when(history.getEncountersByPatient(1L)).thenReturn(List.of(encounter));
        var answer = service.answer("historial de Carlos Gamboa", null, principal);
        assertTrue(answer.answer().contains("Diagnóstico registrado")); assertTrue(answer.answer().contains("atención 3, versión 1"));
        assertFalse(answer.answer().contains("BORRADOR PRIVADO"));
    }
    @Test void multipleRequestedFieldsAreAnsweredTogether() {
        var principal = session("ODONTOLOGO"); prepare(principal);
        var answer = service.answer("alergias y medicamentos de Carlos Gamboa", null, principal);
        assertTrue(answer.answer().contains("Penicilina")); assertTrue(answer.answer().contains("Registro de medicación"));
    }
    @Test void appointmentIsOnlyAReviewableDraftWithSelectedPatient() {
        var principal = session("ODONTOLOGO"); prepare(principal);
        var answer = service.answer("Agenda una cita para Carlos Gamboa mañana a las 15:00 por limpieza", null, principal);
        assertEquals("CITA_BORRADOR", answer.intent()); assertEquals("15:00", answer.appointmentDraft().time());
        assertEquals("1", answer.appointmentDraft().patientId()); assertEquals("limpieza", answer.appointmentDraft().reason());
        verifyNoInteractions(appointments, attachments, history);
        assertEquals("CITA_ACLARAR", service.answer("Agenda una cita mañana a las 3", null, principal).intent());
    }
}
