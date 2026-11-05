package pe.edu.utp.coronyx.backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlBboxDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlFindingReviewItemDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlReviewRequestDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentResponseDto;
import pe.edu.utp.coronyx.backend.model.Attachment;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.repository.AttachmentRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AttachmentService;
import pe.edu.utp.coronyx.backend.service.MlInferenceClient;
import pe.edu.utp.coronyx.backend.service.MlServiceUnavailableException;
import pe.edu.utp.coronyx.backend.service.NotificationService;
import pe.edu.utp.coronyx.backend.service.storage.StorageService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias y de integración para la Fase I (Integración ML en Spring Boot).
 * Verifica control de acceso por rol y clínica, manejo de errores 400/404/503,
 * preservación inmutable de la predicción original y restricción CHECK de ia_estado_revision.
 */
class AttachmentMlIntegrationTest {

    private static final Set<String> VALID_DB_REVIEW_STATES = Set.of(
            "PENDIENTE", "ACEPTADO", "RECHAZADO", "CORREGIDO"
    );

    private Clinic clinic1;
    private Clinic clinic2;
    private User doctorUser;
    private ClinicUser doctorMembership;
    private Patient patient1;
    private Attachment radiographAttachment;

    private AttachmentRepository attachmentRepository;
    private StorageService storageService;
    private MlInferenceClient mlInferenceClient;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        clinic1 = new Clinic();
        clinic1.setId(10L);

        clinic2 = new Clinic();
        clinic2.setId(20L);

        doctorUser = new User("odontologo@coronyx.pe", "hash", "Sebastián", "Odontólogo");
        doctorUser.setId(100L);

        doctorMembership = new ClinicUser();
        // ID mayor a 2^53 para comprobar que se serializa como String decimal exacto
        doctorMembership.setId(9007199254740993L);
        doctorMembership.setClinica(clinic1);
        doctorMembership.setUsuario(doctorUser);

        patient1 = new Patient();
        patient1.setId(300L);
        patient1.setClinica(clinic1);
        patient1.setNombres("Juan");
        patient1.setApellidos("Pérez");

        radiographAttachment = new Attachment(
                clinic1,
                patient1,
                doctorUser,
                "RADIOGRAFIA",
                "clinica-10/paciente-300/panoramica.png",
                "panoramica.png",
                "image/png",
                120_000L,
                "PANORAMICA",
                false
        );
        radiographAttachment.setId(500L);
        radiographAttachment.setAmbito("CLINICO");

        attachmentRepository = mock(AttachmentRepository.class);
        storageService = mock(StorageService.class);
        mlInferenceClient = mock(MlInferenceClient.class);

        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

        attachmentService = new AttachmentService(
                attachmentRepository,
                mock(PatientRepository.class),
                mock(ClinicRepository.class),
                mock(UserRepository.class),
                mock(AppointmentRepository.class),
                storageService,
                mock(NotificationService.class),
                mlInferenceClient
        );
    }

    private UserPrincipal principalWithRole(String role, Clinic clinic) {
        return new UserPrincipal(doctorUser, doctorMembership, clinic, patient1, role);
    }

    private Map<String, Object> sampleMlInferencePayload() {
        Map<String, Object> f1Bbox = new LinkedHashMap<>();
        f1Bbox.put("xMin", 974.66);
        f1Bbox.put("yMin", 648.61);
        f1Bbox.put("xMax", 1185.15);
        f1Bbox.put("yMax", 974.09);

        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("finding_id", "hallazgo-1");
        f1.put("class_id", 0);
        f1.put("class_name", "caries");
        f1.put("score", 0.5621);
        f1.put("bbox_pixels", f1Bbox);
        f1.put("review_status", "PENDIENTE");

        Map<String, Object> f2Bbox = new LinkedHashMap<>();
        f2Bbox.put("xMin", 1806.10);
        f2Bbox.put("yMin", 599.13);
        f2Bbox.put("xMax", 2046.97);
        f2Bbox.put("yMax", 947.41);

        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("finding_id", "hallazgo-2");
        f2.put("class_id", 1);
        f2.put("class_name", "caries_profunda");
        f2.put("score", 0.4125);
        f2.put("bbox_pixels", f2Bbox);
        f2.put("review_status", "PENDIENTE");

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", "coronyx-dentex-yolov8n");
        model.put("version", "1.0.0-mvp");
        model.put("operating_threshold", 0.25);

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("file_name", "panoramica.png");
        image.put("width", 2896);
        image.put("height", 1316);
        image.put("format", "PNG");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", "coronyx.ml.panoramic_caries.v1");
        payload.put("request_id", "req-demo-001");
        payload.put("analyzed_at_utc", "2026-10-31T22:00:00Z");
        payload.put("status", "analysis_completed_with_findings");
        payload.put("model", model);
        payload.put("image", image);
        payload.put("findings_count", 2);
        payload.put("findings", List.of(f1, f2));
        return payload;
    }

    @Test
    void nonDentistRolesCannotExecuteOrReviewMlAnalysis() {
        when(attachmentRepository.findById(500L)).thenReturn(Optional.of(radiographAttachment));

        for (String unauthorizedRole : List.of("PACIENTE", "RECEPCIONISTA", "ADMIN_CLINICA")) {
            UserPrincipal p = principalWithRole(unauthorizedRole, clinic1);
            assertThrows(AccessDeniedException.class,
                    () -> attachmentService.analyzeRadiographWithMl(500L, 0.25, p));
            assertThrows(AccessDeniedException.class,
                    () -> attachmentService.reviewRadiographMl(500L, new MlReviewRequestDto(), p));
        }
        verifyNoInteractions(mlInferenceClient);
    }

    @Test
    void crossClinicDentistAndMissingAttachmentThrowNotFound() {
        UserPrincipal dentistClinic1 = principalWithRole("ODONTOLOGO", clinic1);
        when(attachmentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> attachmentService.analyzeRadiographWithMl(999L, null, dentistClinic1));

        // El archivo pertenece a clinic1 pero la sesión activa es de clinic2
        UserPrincipal dentistClinic2 = principalWithRole("ODONTOLOGO", clinic2);
        when(attachmentRepository.findById(500L)).thenReturn(Optional.of(radiographAttachment));
        assertThrows(NoSuchElementException.class,
                () -> attachmentService.analyzeRadiographWithMl(500L, null, dentistClinic2));
        assertThrows(NoSuchElementException.class,
                () -> attachmentService.reviewRadiographMl(500L, new MlReviewRequestDto(), dentistClinic2));
    }

    @Test
    void incompatibleFormatOrAdministrativeAttachmentIsRejectedWithBadRequest() {
        UserPrincipal dentist = principalWithRole("ODONTOLOGO", clinic1);

        Attachment pdfAttachment = new Attachment(
                clinic1, patient1, doctorUser, "INFORME",
                "clinica-10/paciente-300/informe.pdf", "informe.pdf",
                "application/pdf", 50_000L, null, false
        );
        pdfAttachment.setId(501L);
        pdfAttachment.setAmbito("CLINICO");
        when(attachmentRepository.findById(501L)).thenReturn(Optional.of(pdfAttachment));

        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.analyzeRadiographWithMl(501L, 0.25, dentist));

        Attachment adminAttachment = new Attachment(
                clinic1, patient1, doctorUser, "DOCUMENTO",
                "clinica-10/paciente-300/dni.png", "dni.png",
                "image/png", 50_000L, null, false
        );
        adminAttachment.setId(502L);
        adminAttachment.setAmbito("ADMINISTRATIVO");
        when(attachmentRepository.findById(502L)).thenReturn(Optional.of(adminAttachment));

        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.analyzeRadiographWithMl(502L, 0.25, dentist));
        verifyNoInteractions(mlInferenceClient);
    }

    @Test
    void mlServiceUnavailablePropagatesCleanlyWithoutWritingInvalidDbReviewState() {
        UserPrincipal dentist = principalWithRole("ODONTOLOGO", clinic1);
        when(attachmentRepository.findById(500L)).thenReturn(Optional.of(radiographAttachment));
        when(storageService.loadAsResource(radiographAttachment.getClaveAlmacenamiento()))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3, 4}));
        when(mlInferenceClient.analyzeRadiograph(any(), eq("panoramica.png"), eq(0.25)))
                .thenThrow(new MlServiceUnavailableException("Servicio ML apagado o en timeout"));

        assertThrows(MlServiceUnavailableException.class,
                () -> attachmentService.analyzeRadiographWithMl(500L, 0.25, dentist));

        // Nunca debe escribir estados fuera del CHECK ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'CORREGIDO')
        assertTrue(VALID_DB_REVIEW_STATES.contains(radiographAttachment.getIaEstadoRevision()));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyzeAndReviewPreservesOriginalModelPredictionAndUpdatesReviewMetadata() {
        UserPrincipal dentist = principalWithRole("ODONTOLOGO", clinic1);
        when(attachmentRepository.findById(500L)).thenReturn(Optional.of(radiographAttachment));
        when(storageService.loadAsResource(radiographAttachment.getClaveAlmacenamiento()))
                .thenReturn(new ByteArrayResource(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        when(mlInferenceClient.analyzeRadiograph(any(), eq("panoramica.png"), eq(0.25)))
                .thenReturn(sampleMlInferencePayload());

        // 1. Ejecutar análisis ML
        AttachmentResponseDto analyzed = attachmentService.analyzeRadiographWithMl(500L, 0.25, dentist);
        assertEquals("500", analyzed.getId());
        assertEquals("coronyx-dentex-yolov8n", analyzed.getIaNombreModelo());
        assertEquals("1.0.0-mvp", analyzed.getIaVersionModelo());
        assertEquals("PENDIENTE", analyzed.getIaEstadoRevision());
        assertNull(analyzed.getIaRevisadoPorId());
        assertNotNull(analyzed.getIaHallazgos());

        // 2. El odontólogo corrige el hallazgo-1 (de caries a caries_profunda y ajusta la caja) y acepta el hallazgo-2
        MlReviewRequestDto reviewRequest = new MlReviewRequestDto();
        MlFindingReviewItemDto item1 = new MlFindingReviewItemDto(
                "hallazgo-1",
                "CORREGIDO",
                "caries_profunda",
                new MlBboxDto(980.0, 650.0, 1180.0, 970.0),
                "Compromiso pulpar confirmado en inspección clínica"
        );
        MlFindingReviewItemDto item2 = new MlFindingReviewItemDto(
                "hallazgo-2",
                "ACEPTADO",
                null,
                null,
                "Coincide con lesión radiolúcida"
        );
        reviewRequest.setReviews(List.of(item1, item2));
        reviewRequest.setOverallClinicalNote("Revisión completada por odontólogo tratante.");

        AttachmentResponseDto reviewed = attachmentService.reviewRadiographMl(500L, reviewRequest, dentist);
        assertEquals("CORREGIDO", reviewed.getIaEstadoRevision());
        assertTrue(VALID_DB_REVIEW_STATES.contains(reviewed.getIaEstadoRevision()));
        assertEquals("9007199254740993", reviewed.getIaRevisadoPorId());
        assertEquals("Sebastián Odontólogo", reviewed.getIaRevisadoPorNombre());

        Map<String, Object> hallazgos = reviewed.getIaHallazgos();
        List<Map<String, Object>> originalOutput = (List<Map<String, Object>>) hallazgos.get("originalModelOutput");
        List<Map<String, Object>> currentFindings = (List<Map<String, Object>>) hallazgos.get("findings");

        assertEquals(2, originalOutput.size());
        assertEquals(2, currentFindings.size());

        // Verificar que la predicción original de hallazgo-1 permanece 100% intacta
        Map<String, Object> origF1 = originalOutput.get(0);
        assertEquals("caries", origF1.get("class_name"));
        assertEquals(0, ((Number) origF1.get("class_id")).intValue());
        Map<String, Object> origF1Bbox = (Map<String, Object>) origF1.get("bbox_pixels");
        assertEquals(974.66, ((Number) origF1Bbox.get("xMin")).doubleValue(), 0.01);

        // Verificar que el hallazgo-1 revisado tiene la clase y caja corregidas pero conserva original_*
        Map<String, Object> revF1 = currentFindings.get(0);
        assertEquals("CORREGIDO", revF1.get("reviewStatus"));
        assertEquals("caries_profunda", revF1.get("className"));
        assertEquals(1, ((Number) revF1.get("classId")).intValue());
        assertEquals("caries", revF1.get("originalClassName"));
        assertEquals(0, ((Number) revF1.get("originalClassId")).intValue());
        assertEquals("9007199254740993", revF1.get("reviewedByUsuarioClinicaId"));

        Map<String, Object> revF1Bbox = (Map<String, Object>) revF1.get("bboxPixels");
        Map<String, Object> revF1OrigBbox = (Map<String, Object>) revF1.get("originalBboxPixels");
        assertEquals(980.0, ((Number) revF1Bbox.get("xMin")).doubleValue(), 0.01);
        assertEquals(974.66, ((Number) revF1OrigBbox.get("xMin")).doubleValue(), 0.01);
    }

    @Test
    void mlInferenceClientHandlesHttp200And400And503Correctly() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/health", exchange -> {
            byte[] resp = "{\"service_status\":\"alive\",\"model_ready\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/predict", exchange -> {
            String fileName = exchange.getRequestHeaders().getFirst("X-File-Name");
            int status = 200;
            String body;
            if ("corrupt.png".equals(fileName)) {
                status = 400;
                body = "{\"status\":\"invalid_image_error\",\"error\":{\"message\":\"Imagen corrupta\"}}";
            } else if ("missing_weights.png".equals(fileName)) {
                status = 503;
                body = "{\"status\":\"model_unavailable_error\",\"error\":{\"message\":\"Pesos best.pt no disponibles\"}}";
            } else {
                body = "{\"status\":\"analysis_completed_no_findings\",\"findings_count\":0,\"findings\":[]}";
            }
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.start();
        try {
            MlInferenceClient client = new MlInferenceClient("http://127.0.0.1:" + port, 0.25, 3000L);
            Map<String, Object> health = client.checkHealth();
            assertEquals(Boolean.TRUE, health.get("model_ready"));

            Map<String, Object> okResult = client.analyzeRadiograph(new byte[]{1, 2, 3}, "normal.png", 0.25);
            assertEquals("analysis_completed_no_findings", okResult.get("status"));

            assertThrows(IllegalArgumentException.class,
                    () -> client.analyzeRadiograph(new byte[]{1, 2, 3}, "corrupt.png", 0.25));

            assertThrows(MlServiceUnavailableException.class,
                    () -> client.analyzeRadiograph(new byte[]{1, 2, 3}, "missing_weights.png", 0.25));
        } finally {
            server.stop(0);
        }
    }
}

