package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import pe.edu.utp.coronyx.backend.controller.HelpdeskController;
import pe.edu.utp.coronyx.backend.dto.ClinicalEncounterResponseDto;
import pe.edu.utp.coronyx.backend.dto.ClinicalVersionResponseDto;
import pe.edu.utp.coronyx.backend.dto.HelpdeskBitacoraDto;
import pe.edu.utp.coronyx.backend.dto.HelpdeskCatalogDto;
import pe.edu.utp.coronyx.backend.dto.HelpdeskTicketDto;
import pe.edu.utp.coronyx.backend.service.AppointmentTransitions;
import pe.edu.utp.coronyx.backend.service.FileContentValidator;
import pe.edu.utp.coronyx.backend.service.TeleconsultationLinks;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HelpdeskServiceUnitTest {

    @Test
    void priorityMatrixCalculatesCorrectLevels() {
        assertEquals("CRITICO", HelpdeskController.calculatePriority(3, 3));
        assertEquals("ALTO", HelpdeskController.calculatePriority(3, 2));
        assertEquals("ALTO", HelpdeskController.calculatePriority(2, 3));
        assertEquals("MEDIO", HelpdeskController.calculatePriority(2, 2));
        assertEquals("MEDIO", HelpdeskController.calculatePriority(3, 1));
        assertEquals("BAJO", HelpdeskController.calculatePriority(1, 2));
        assertEquals("BAJO", HelpdeskController.calculatePriority(1, 1));
    }

    @Test
    void catalogContainsAllSixteenPredefinedIncidentsAndFiltersByRole() {
        HelpdeskController controller = new HelpdeskController(null);

        ResponseEntity<List<HelpdeskCatalogDto>> allResponse = controller.getCatalog(null);
        assertNotNull(allResponse.getBody());
        assertEquals(16, allResponse.getBody().size());

        ResponseEntity<List<HelpdeskCatalogDto>> pacienteCatalog = controller.getCatalog("PACIENTE");
        assertNotNull(pacienteCatalog.getBody());
        assertTrue(pacienteCatalog.getBody().stream().anyMatch(c -> c.id().equals("INC-PAC-001")));
        assertTrue(pacienteCatalog.getBody().stream().anyMatch(c -> c.id().equals("INC-GEN-099")));
    }

    @Test
    void ticketLifecycleTracksCreationAttentionResolutionAndBitacora() {
        HelpdeskController controller = new HelpdeskController(null);

        HelpdeskTicketDto request = new HelpdeskTicketDto(
                null,
                "INC-PAC-001",
                "Reprogramación urgente de cita odontológica",
                "Luis Francisco Gamboa",
                "PACIENTE",
                10L,
                null,
                null,
                null,
                2,
                3,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Solicitud de prueba unitaria CI",
                null,
                null,
                1L,
                null,
                null
        );

        ResponseEntity<HelpdeskTicketDto> createdRes = controller.createTicket(request);
        HelpdeskTicketDto created = createdRes.getBody();
        assertNotNull(created);
        assertEquals("TKT-001", created.id());
        assertEquals("ALTO", created.prioridad());
        assertEquals("PENDIENTE", created.estado());
        assertEquals("RECEPCIONISTA", created.responsableRol());

        // Transición a EN_PROCESO
        ResponseEntity<HelpdeskTicketDto> inProgressRes = controller.updateTicketStatus(
                "1",
                Map.of(
                        "estado", "EN_PROCESO",
                        "usuarioNombre", "Paula Suárez",
                        "usuarioRol", "RECEPCIONISTA",
                        "evidencia", "Iniciando reprogramación en agenda"
                )
        );
        assertNotNull(inProgressRes.getBody());
        assertEquals("EN_PROCESO", inProgressRes.getBody().estado());

        // Transición a ATENDIDO
        ResponseEntity<HelpdeskTicketDto> resolvedRes = controller.updateTicketStatus(
                "TKT-001",
                Map.of(
                        "estado", "ATENDIDO",
                        "usuarioNombre", "Paula Suárez",
                        "usuarioRol", "RECEPCIONISTA",
                        "evidencia", "Cita reprogramada exitosamente"
                )
        );
        assertNotNull(resolvedRes.getBody());
        assertEquals("ATENDIDO", resolvedRes.getBody().estado());

        // Verificar bitácora (3 eventos: CREACION, INICIO_ATENCION, RESOLUCION)
        ResponseEntity<List<HelpdeskBitacoraDto>> bitacoraRes = controller.getTicketBitacora("1");
        assertNotNull(bitacoraRes.getBody());
        assertEquals(3, bitacoraRes.getBody().size());
    }

    @Test
    void terminalAppointmentsCannotReopen() {
        for (String state : List.of("CANCELADA", "FINALIZADA", "NO_ASISTIO")) {
            assertThrows(IllegalStateException.class, () -> AppointmentTransitions.requireTransition(state, "EN_ATENCION"));
            assertThrows(IllegalStateException.class, () -> AppointmentTransitions.requireReschedulable(state));
        }
    }

    @Test
    void receptionAttendanceDoesNotGrantClinicalFinalization() {
        assertThrows(IllegalStateException.class, () -> AppointmentTransitions.requireTransition("CONFIRMADA", "FINALIZADA"));
        assertDoesNotThrow(() -> AppointmentTransitions.requireTransition("EN_ATENCION", "FINALIZADA"));
    }

    @Test
    void clinicalIdsRemainExactStrings() {
        var dto = new ClinicalEncounterResponseDto();
        dto.setId(9007199254740993L);
        dto.setCitaId(9007199254740995L);
        assertEquals("9007199254740993", dto.getId());
        assertEquals("9007199254740995", dto.getCitaId());

        var version = new ClinicalVersionResponseDto();
        version.setId(9007199254740997L);
        assertEquals("9007199254740997", version.getId());
    }

    @Test
    void disguisedHtmlCannotUploadAsPdf() {
        assertThrows(IllegalArgumentException.class, () -> FileContentValidator.detect(
                new MockMultipartFile("file", "image.pdf", "application/pdf", "<script>alert(1)</script>".getBytes())
        ));
        assertEquals("application/pdf", FileContentValidator.detect(
                new MockMultipartFile("file", "image.pdf", "text/plain", "%PDF-1.4\nvalid header".getBytes())
        ));
    }

    @Test
    void meetingLinksValidateGoogleMeetAndZoomCorrectly() {
        assertEquals("https://meet.google.com/abc-defg-hij", TeleconsultationLinks.validate("https://meet.google.com/abc-defg-hij"));
        assertNotNull(TeleconsultationLinks.validate("https://us02web.zoom.us/j/123456789?pwd=abc"));
        for (String bad : List.of("javascript:alert(1)", "https://meet.google.com/new", "http://meet.google.com/abc-defg-hij")) {
            assertThrows(IllegalArgumentException.class, () -> TeleconsultationLinks.validate(bad));
        }
    }
}
