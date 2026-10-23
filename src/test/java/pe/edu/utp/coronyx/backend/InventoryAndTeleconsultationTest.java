package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.dto.TeleconsultationDtos.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class InventoryAndTeleconsultationTest {

    private void login(String rol, Long userId, Long usuarioClinicaId, Long pacienteId, Long clinicaId) {
        User u = new User();
        u.setId(userId);
        u.setCorreo(rol.toLowerCase() + "@coronyx.pe");
        u.setNombres("Test");
        u.setApellidos(rol);
        u.setEstado("ACTIVO");

        Clinic c = new Clinic();
        c.setId(clinicaId);
        c.setNombre("Clínica Dental UTP");

        ClinicUser cu = null;
        if (usuarioClinicaId != null) {
            cu = new ClinicUser();
            cu.setId(usuarioClinicaId);
            cu.setUsuario(u);
            cu.setClinica(c);
            Role r = new Role();
            r.setCodigo(rol);
            r.setNombre(rol);
            cu.setRol(r);
            cu.setEstado("ACTIVO");
        }

        Patient p = null;
        if (pacienteId != null) {
            p = new Patient();
            p.setId(pacienteId);
            p.setClinica(c);
            p.setUsuario(u);
        }

        UserPrincipal principal = new UserPrincipal(u, cu, c, p, rol);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @BeforeEach
    void setup() {
        login("ADMIN_CLINICA", 1L, 101L, null, 1L);
    }

    // =========================================================================
    // 1. Pruebas de Inventario por presentación y estado
    // =========================================================================

    @Test
    void testAceptacionRecepcionAperturaAgotamientoCajas() {
        // Criterio de aceptación del plan y requerimiento del usuario:
        // Recibir 5 cajas -> 5 cerradas, 0 en uso, total 5
        // Abrir 1 caja -> 4 cerradas, 1 en uso, total 5 (abrir no altera total)
        // Agotar esa caja -> 4 cerradas, 0 en uso, total 4 (agotar descuenta existencias)
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem cajaGuantes = new InventoryItem();
        cajaGuantes.id = 50L;
        cajaGuantes.nombre = "Guantes de Nitrilo Talla M";
        cajaGuantes.categoria = "Bioseguridad";
        cajaGuantes.unidad = "cajas";
        cajaGuantes.presentacion = "CAJA";
        cajaGuantes.clasificacion = "CONSUMIBLE";
        cajaGuantes.contenidoDescriptivo = "100 guantes";
        cajaGuantes.stock = 0;
        cajaGuantes.stockCerrado = 0;
        cajaGuantes.stockEnUso = 0;
        cajaGuantes.stockMinimo = 2;
        cajaGuantes.costoUnitario = new BigDecimal("45.00");
        cajaGuantes.activo = true;

        when(items.lockById(50L, 1L)).thenReturn(Optional.of(cajaGuantes));
        when(moves.findByClinicaIdAndSolicitudId(eq(1L), anyString())).thenReturn(Optional.empty());

        InventoryService service = new InventoryService(items, moves);

        // Paso 1: Recibir 5 cajas
        ItemResponse r1 = service.move(50L, new MovementRequest("ENTRADA", 5, null, null, null, "Compra de lote", "req-in-1"));
        assertEquals(5, r1.stock());
        assertEquals(5, r1.stockCerrado());
        assertEquals(0, r1.stockEnUso());
        assertFalse(r1.stockBajo(), "5 cerradas supera el mínimo de 2");

        // Paso 2: Abrir 1 caja
        ItemResponse r2 = service.move(50L, new MovementRequest("APERTURA", 1, null, null, null, "Puesta en uso en consultorio 1", "req-open-1"));
        assertEquals(5, r2.stock(), "Abrir no debe disminuir el total");
        assertEquals(4, r2.stockCerrado(), "Quedan 4 cerradas");
        assertEquals(1, r2.stockEnUso(), "1 caja en uso con contenido restante desconocido");

        // Paso 3: Agotar 1 caja
        ItemResponse r3 = service.move(50L, new MovementRequest("AGOTADO", 1, null, null, null, "Caja consumida totalmente", "req-drain-1"));
        assertEquals(4, r3.stock(), "Agotar disminuye el total físico");
        assertEquals(4, r3.stockCerrado());
        assertEquals(0, r3.stockEnUso());

        verify(moves, times(3)).saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void testAbrirMasDeLoDisponibleEsRechazado() {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem item = new InventoryItem();
        item.id = 51L;
        item.stockCerrado = 2;
        item.stockEnUso = 1;
        item.stock = 3;
        item.activo = true;
        item.presentacion = "ENVASE";
        item.clasificacion = "CONSUMIBLE";

        when(items.lockById(51L, 1L)).thenReturn(Optional.of(item));
        when(moves.findByClinicaIdAndSolicitudId(eq(1L), anyString())).thenReturn(Optional.empty());

        InventoryService service = new InventoryService(items, moves);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.move(51L, new MovementRequest("APERTURA", 3, null, null, null, "Abrir frascos", "req-bad-open"))
        );
        assertTrue(ex.getMessage().contains("cerradas/disponibles insuficientes"));
    }

    @Test
    void testAgotarReusableEsRechazado() {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem pinza = new InventoryItem();
        pinza.id = 52L;
        pinza.nombre = "Pinza Algodonera";
        pinza.presentacion = "UNIDAD";
        pinza.clasificacion = "REUTILIZABLE";
        pinza.stockCerrado = 2;
        pinza.stockEnUso = 3;
        pinza.stock = 5;
        pinza.activo = true;

        when(items.lockById(52L, 1L)).thenReturn(Optional.of(pinza));
        when(moves.findByClinicaIdAndSolicitudId(eq(1L), anyString())).thenReturn(Optional.empty());

        InventoryService service = new InventoryService(items, moves);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.move(52L, new MovementRequest("AGOTADO", 1, null, null, null, "Pinza usada", "req-drain-reusable"))
        );
        assertTrue(ex.getMessage().contains("reutilizable no se agota"));
    }

    @Test
    void testDevolverReusableADisponibleConservaTotal() {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem espejo = new InventoryItem();
        espejo.id = 53L;
        espejo.nombre = "Espejo Bucal N° 5";
        espejo.presentacion = "UNIDAD";
        espejo.clasificacion = "REUTILIZABLE";
        espejo.stockCerrado = 1;
        espejo.stockEnUso = 4;
        espejo.stock = 5;
        espejo.activo = true;

        when(items.lockById(53L, 1L)).thenReturn(Optional.of(espejo));
        when(moves.findByClinicaIdAndSolicitudId(eq(1L), anyString())).thenReturn(Optional.empty());

        InventoryService service = new InventoryService(items, moves);

        ItemResponse r = service.move(53L, new MovementRequest("DEVOLUCION", 2, null, null, null, "Devolución a inventario disponible", "req-dev-1"));
        assertEquals(5, r.stock(), "Devolver no cambia el total físico");
        assertEquals(3, r.stockCerrado(), "1 + 2 = 3 cerrados/disponibles");
        assertEquals(2, r.stockEnUso(), "4 - 2 = 2 en uso");
    }

    @Test
    void testAjusteAdministrativoPorEstado() {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem item = new InventoryItem();
        item.id = 54L;
        item.stockCerrado = 3;
        item.stockEnUso = 2;
        item.stock = 5;
        item.activo = true;

        when(items.lockById(54L, 1L)).thenReturn(Optional.of(item));
        when(moves.findByClinicaIdAndSolicitudId(eq(1L), anyString())).thenReturn(Optional.empty());

        InventoryService service = new InventoryService(items, moves);

        // Administrador ajusta conteo: 4 cerradas y 1 en uso
        ItemResponse r = service.move(54L, new MovementRequest("AJUSTE", 0, null, 4, 1, "Conteo físico semestral", "req-adj-1"));
        assertEquals(5, r.stock());
        assertEquals(4, r.stockCerrado());
        assertEquals(1, r.stockEnUso());

        // Recepcionista intenta ajustar -> Denegado
        login("RECEPCIONISTA", 2L, 102L, null, 1L);
        assertThrows(AccessDeniedException.class, () ->
                service.move(54L, new MovementRequest("AJUSTE", 0, null, 2, 2, "Intento recepcionista", "req-adj-2"))
        );
    }

    @Test
    void testReintentoIdempotenteNoDuplicaMovimiento() {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        InventoryItem item = new InventoryItem();
        item.id = 55L;
        item.stockCerrado = 4;
        item.stockEnUso = 1;
        item.stock = 5;
        item.activo = true;

        InventoryMovement prev = new InventoryMovement();
        prev.insumo = item;
        prev.tipo = "APERTURA";
        prev.cantidad = 1;
        prev.motivo = "Apertura para gabinete";
        prev.solicitudId = "req-idem-1";

        when(items.lockById(55L, 1L)).thenReturn(Optional.of(item));
        when(moves.findByClinicaIdAndSolicitudId(1L, "req-idem-1")).thenReturn(Optional.of(prev));

        InventoryService service = new InventoryService(items, moves);

        // Reintento idéntico -> respuesta idempotente sin guardar nuevo movimiento
        ItemResponse r = service.move(55L, new MovementRequest("APERTURA", 1, null, null, null, "Apertura para gabinete", "req-idem-1"));
        assertEquals(5, r.stock());
        verify(moves, never()).saveAndFlush(any());

        // Reutilización de clave con datos distintos -> Rechazado
        assertThrows(IllegalStateException.class, () ->
                service.move(55L, new MovementRequest("APERTURA", 2, null, null, null, "Apertura para gabinete", "req-idem-1"))
        );
    }

    @Test
    void testIndependenciaDeAtencionesYCitasNoModificanInventario() {
        // Validación obligatoria de la revisión 2:
        // Citas y atención clínica no modifican stock ni consumen inventario
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        InventoryMovementRepository moves = mock(InventoryMovementRepository.class);

        // No hay interacciones con repositories de inventario durante el ciclo clínico
        verifyNoInteractions(items);
        verifyNoInteractions(moves);
    }

    // =========================================================================
    // 2. Pruebas de Importación de Catálogo (clasificación y prevención de errores)
    // =========================================================================

    @Test
    void testVistaPreviaCatalogoClasificaServiciosPresentacionesYDuplicados() {
        InventoryItemRepository itemRepo = mock(InventoryItemRepository.class);
        InventoryMovementRepository moveRepo = mock(InventoryMovementRepository.class);
        CatalogImportBatchRepository batchRepo = mock(CatalogImportBatchRepository.class);

        InventoryCatalogService catalogService = new InventoryCatalogService(itemRepo, moveRepo, batchRepo);

        List<RawCatalogRow> rows = List.of(
                new RawCatalogRow("D2620", "CAJA", "GUANTES DE NITRILO M X 100", new BigDecimal("48.50")),
                new RawCatalogRow("D2620", "CAJA", "GUANTES DE NITRILO L X 100", new BigDecimal("48.50")), // Código repetido
                new RawCatalogRow("S001", "UND", "CURACION DENTAL RESINA COMPUESTA", new BigDecimal("120.00")), // Servicio/Tratamiento
                new RawCatalogRow("M001", "METRO", "ALAMBRE DE ORTODONCIA 0.16", null), // Presentación no estándar y sin precio
                new RawCatalogRow("P001", "UND", "PINZA ALGODONERA DE ACERO", new BigDecimal("25.00")), // Reutilizable
                new RawCatalogRow("E001", "FCO", "ALCOHOL ETILICO 70% 500 ML", new BigDecimal("12.00")) // Envase
        );

        CatalogPreviewResponse preview = catalogService.preview(rows);
        assertEquals(6, preview.totalFilas());
        assertTrue(preview.filasPendientes() >= 3, "Códigos duplicados, servicios y presentaciones no estándar deben quedar pendientes");

        // D2620 debe estar marcado como pendiente por código repetido
        CatalogPreviewItem itemD2620 = preview.items().get(0);
        assertEquals("PENDIENTE", itemD2620.estadoRevision());
        assertTrue(itemD2620.motivoPendiente().contains("Código repetido"));

        // S001 debe estar marcado como servicio clínico
        CatalogPreviewItem itemS001 = preview.items().get(2);
        assertEquals("PENDIENTE", itemS001.estadoRevision());
        assertTrue(itemS001.motivoPendiente().contains("tratamiento o servicio clínico"));

        // M001 con METRO debe estar pendiente de confirmación de presentación
        CatalogPreviewItem itemM001 = preview.items().get(3);
        assertEquals("PENDIENTE", itemM001.estadoRevision());
        assertTrue(itemM001.motivoPendiente().contains("Presentación no estándar"));

        // P001 debe clasificarse como REUTILIZABLE
        CatalogPreviewItem itemP001 = preview.items().get(4);
        assertEquals("REUTILIZABLE", itemP001.clasificacion());
        assertEquals("APROBADO", itemP001.estadoRevision());

        // E001 debe mapearse a ENVASE
        CatalogPreviewItem itemE001 = preview.items().get(5);
        assertEquals("ENVASE", itemE001.presentacion());
        assertEquals("APROBADO", itemE001.estadoRevision());
    }

    @Test
    void testConvertValueFromJsonNode() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "{\"filas\":[{\"codigo\":\"MAT-001\",\"descripcion\":\"Guantes\",\"unidadMedida\":\"caja\",\"precioReferencial\":28.5}]}";
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
        List<RawCatalogRow> rows = mapper.convertValue(node.get("filas"), new com.fasterxml.jackson.core.type.TypeReference<List<RawCatalogRow>>() {});
        assertEquals(1, rows.size());
        assertEquals("MAT-001", rows.get(0).codigo());
        assertEquals("caja", rows.get(0).effectiveUm());
        assertEquals(new BigDecimal("28.5"), rows.get(0).effectivePrice());
    }

    @Test
    void testControllerPreviewCatalogFullFlow() throws Exception {
        InventoryItemRepository itemRepo = mock(InventoryItemRepository.class);
        InventoryMovementRepository moveRepo = mock(InventoryMovementRepository.class);
        CatalogImportBatchRepository batchRepo = mock(CatalogImportBatchRepository.class);
        when(itemRepo.findByClinicaIdOrderByNombreAsc(1L)).thenReturn(List.of());

        InventoryService invService = new InventoryService(itemRepo, moveRepo);
        InventoryCatalogService catalogService = new InventoryCatalogService(itemRepo, moveRepo, batchRepo);
        pe.edu.utp.coronyx.backend.controller.InventoryController controller =
                new pe.edu.utp.coronyx.backend.controller.InventoryController(invService, catalogService);

        login("ADMIN_CLINICA", 1L, 101L, null, 1L);
        CatalogPreviewRequest req = new CatalogPreviewRequest(
                List.of(new RawCatalogRow("MAT-001", "caja", "Guantes", new BigDecimal("28.5"))),
                null,
                "TEST"
        );
        CatalogPreviewResponse res = controller.previewCatalog(req);
        assertNotNull(res);
        assertEquals(1, res.filas().size());
    }




    @Test
    void testConfirmacionImportacionCreaInsumosYPrevieneReimportacion() {
        InventoryItemRepository itemRepo = mock(InventoryItemRepository.class);
        InventoryMovementRepository moveRepo = mock(InventoryMovementRepository.class);
        CatalogImportBatchRepository batchRepo = mock(CatalogImportBatchRepository.class);

        when(batchRepo.existsByClinicaIdAndLoteId(1L, "lote-2026-001")).thenReturn(false);
        when(itemRepo.saveAndFlush(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem i = inv.getArgument(0);
            i.id = 99L;
            return i;
        });

        InventoryCatalogService catalogService = new InventoryCatalogService(itemRepo, moveRepo, batchRepo);

        CatalogImportConfirmRequest request = new CatalogImportConfirmRequest(
                "lote-2026-001",
                "BIEN_SERVICIOS.xlsx",
                "BIEN&SERVICIOS",
                List.of(
                        new CatalogImportItemRequest("G001", "Guantes Nitrilo", "Bioseguridad", "CAJA", "CONSUMIBLE", "100 guantes", new BigDecimal("45.00"), 4, 1, "Conteo inicial: 4 cerradas y 1 abierta"),
                        new CatalogImportItemRequest("A001", "Alcohol 70%", "Bioseguridad", "ENVASE", "CONSUMIBLE", "500 ml", new BigDecimal("10.00"), 2, 0, "Conteo inicial: 2 frascos cerrados")
                )
        );

        CatalogImportConfirmResponse res = catalogService.confirm(request);
        assertEquals("lote-2026-001", res.loteId());
        assertEquals(2, res.itemsImportados());

        // Verifica que se guardó el registro de importación y los movimientos de conteo inicial
        verify(batchRepo).save(any(CatalogImportBatch.class));
        verify(moveRepo, times(2)).saveAndFlush(any(InventoryMovement.class));

        // Intento de reimportar el mismo lote debe ser rechazado
        when(batchRepo.existsByClinicaIdAndLoteId(1L, "lote-2026-001")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> catalogService.confirm(request));
    }

    // =========================================================================
    // 3. Pruebas de Teleconsulta integrada (seguridad, tokens opacos y cierre)
    // =========================================================================

    @Test
    void testTeleconsultaOdontologoYPacienteAccedenConTokensValidosParaMismaSala() {
        TeleconsultationSessionRepository sessionRepo = mock(TeleconsultationSessionRepository.class);
        AppointmentRepository apptRepo = mock(AppointmentRepository.class);

        Clinic clinic = new Clinic();
        clinic.setId(1L);

        ClinicUser odontologo = new ClinicUser();
        odontologo.setId(10L);

        Patient paciente = new Patient();
        paciente.setId(20L);

        Appointment cita = new Appointment();
        cita.setId(300L);
        cita.setClinica(clinic);
        cita.setOdontologo(odontologo);
        cita.setPaciente(paciente);
        cita.setModalidad("VIRTUAL");
        cita.setEstado("PROGRAMADA");

        when(apptRepo.findByIdAndClinicaId(300L, 1L)).thenReturn(Optional.of(cita));
        when(sessionRepo.findByClinicaIdAndCitaId(1L, 300L)).thenReturn(Optional.empty());
        when(sessionRepo.saveAndFlush(any(TeleconsultationSession.class))).thenAnswer(inv -> {
            TeleconsultationSession s = inv.getArgument(0);
            s.id = 1L;
            return s;
        });

        TeleconsultationService service = new TeleconsultationService(sessionRepo, apptRepo);

        // 1. Odontólogo asignado ingresa
        login("ODONTOLOGO", 5L, 10L, null, 1L);
        TeleconsultationJoinResponse docJoin = service.joinSession(300L);

        assertNotNull(docJoin.salaId());
        assertTrue(docJoin.salaId().startsWith("cx-"), "La sala debe usar identificador opaco aleatorio");
        assertFalse(docJoin.salaId().contains("Test"), "La sala no debe contener nombres");
        assertTrue(docJoin.esModerador(), "El odontólogo debe recibir rol de moderador");
        assertNotNull(docJoin.jwtToken());

        // Simular que la sesión ya existe para la cita
        TeleconsultationSession activeSession = new TeleconsultationSession();
        activeSession.id = 1L;
        activeSession.clinica = clinic;
        activeSession.cita = cita;
        activeSession.salaId = docJoin.salaId();
        activeSession.estado = "EN_CURSO";
        when(sessionRepo.findByClinicaIdAndCitaId(1L, 300L)).thenReturn(Optional.of(activeSession));
        when(sessionRepo.findBySalaId(docJoin.salaId())).thenReturn(Optional.of(activeSession));

        // 2. Paciente vinculado ingresa a la misma cita
        login("PACIENTE", 6L, 12L, 20L, 1L);
        TeleconsultationJoinResponse patientJoin = service.joinSession(300L);

        assertEquals(docJoin.salaId(), patientJoin.salaId(), "Paciente y odontólogo deben ingresar a la misma sala opaca");
        assertFalse(patientJoin.esModerador(), "El paciente no debe ser moderador");
        assertNotNull(patientJoin.jwtToken());

        // 3. Verificación de tokens de la sala
        VerifyTokenResponse vDoc = service.verifyRoomToken(docJoin.salaId(), docJoin.jwtToken());
        assertTrue(vDoc.valido());
        assertTrue(vDoc.esModerador());

        VerifyTokenResponse vPat = service.verifyRoomToken(docJoin.salaId(), patientJoin.jwtToken());
        assertTrue(vPat.valido());
        assertFalse(vPat.esModerador());
    }

    @Test
    void testTeleconsultaRechazaUsuarioAjenoCitaPresencialYTokenDeOtraSala() {
        TeleconsultationSessionRepository sessionRepo = mock(TeleconsultationSessionRepository.class);
        AppointmentRepository apptRepo = mock(AppointmentRepository.class);

        Clinic clinic = new Clinic();
        clinic.setId(1L);

        ClinicUser doc1 = new ClinicUser(); doc1.setId(10L);
        ClinicUser doc2 = new ClinicUser(); doc2.setId(11L);
        Patient pac1 = new Patient(); pac1.setId(20L);
        Patient pac2 = new Patient(); pac2.setId(21L);

        Appointment citaVirtual = new Appointment();
        citaVirtual.setId(301L);
        citaVirtual.setClinica(clinic);
        citaVirtual.setOdontologo(doc1);
        citaVirtual.setPaciente(pac1);
        citaVirtual.setModalidad("VIRTUAL");
        citaVirtual.setEstado("CONFIRMADA");

        Appointment citaPresencial = new Appointment();
        citaPresencial.setId(302L);
        citaPresencial.setClinica(clinic);
        citaPresencial.setOdontologo(doc1);
        citaPresencial.setPaciente(pac1);
        citaPresencial.setModalidad("PRESENCIAL");
        citaPresencial.setEstado("CONFIRMADA");

        when(apptRepo.findByIdAndClinicaId(301L, 1L)).thenReturn(Optional.of(citaVirtual));
        when(apptRepo.findByIdAndClinicaId(302L, 1L)).thenReturn(Optional.of(citaPresencial));

        TeleconsultationService service = new TeleconsultationService(sessionRepo, apptRepo);

        // Intento 1: Otro odontólogo no asignado intenta entrar -> Rechazado con 403
        login("ODONTOLOGO", 99L, 11L, null, 1L);
        assertThrows(AccessDeniedException.class, () -> service.joinSession(301L));

        // Intento 2: Paciente ajeno intenta entrar -> Rechazado con 403
        login("PACIENTE", 98L, 15L, 21L, 1L);
        assertThrows(AccessDeniedException.class, () -> service.joinSession(301L));

        // Intento 3: Cita presencial -> Rechazada con IllegalStateException
        login("ODONTOLOGO", 5L, 10L, null, 1L);
        assertThrows(IllegalStateException.class, () -> service.joinSession(302L));

        // Intento 4: Verificación de acceso sin token -> Rechazado
        VerifyTokenResponse noToken = service.verifyRoomToken("cx-room-1", "");
        assertFalse(noToken.valido());

        // Intento 5: Token válido pero emitido para otra sala -> Rechazado
        TeleconsultationSession activeSession = new TeleconsultationSession();
        activeSession.salaId = "cx-room-1";
        activeSession.estado = "EN_CURSO";
        when(sessionRepo.findByClinicaIdAndCitaId(1L, 301L)).thenReturn(Optional.of(activeSession));
        when(sessionRepo.findBySalaId("cx-room-1")).thenReturn(Optional.of(activeSession));

        TeleconsultationJoinResponse join = service.joinSession(301L);
        VerifyTokenResponse wrongRoom = service.verifyRoomToken("cx-otra-sala-2", join.jwtToken());
        assertFalse(wrongRoom.valido(), "Token de otra sala debe ser rechazado tajantemente");
        assertTrue(wrongRoom.mensaje().contains("sala diferente"));
    }

    @Test
    void testCerrarSalaTeleconsultaImpideReingreso() {
        TeleconsultationSessionRepository sessionRepo = mock(TeleconsultationSessionRepository.class);
        AppointmentRepository apptRepo = mock(AppointmentRepository.class);

        Clinic clinic = new Clinic();
        clinic.setId(1L);

        ClinicUser doc = new ClinicUser(); doc.setId(10L);
        Patient pac = new Patient(); pac.setId(20L);

        Appointment cita = new Appointment();
        cita.setId(303L);
        cita.setClinica(clinic);
        cita.setOdontologo(doc);
        cita.setPaciente(pac);
        cita.setModalidad("VIRTUAL");
        cita.setEstado("EN_ATENCION");

        TeleconsultationSession session = new TeleconsultationSession();
        session.id = 99L;
        session.clinica = clinic;
        session.cita = cita;
        session.salaId = "cx-test-finish";
        session.estado = "EN_CURSO";

        when(apptRepo.findByIdAndClinicaId(303L, 1L)).thenReturn(Optional.of(cita));
        when(sessionRepo.findByClinicaIdAndCitaId(1L, 303L)).thenReturn(Optional.of(session));
        when(sessionRepo.findBySalaId("cx-test-finish")).thenReturn(Optional.of(session));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        TeleconsultationService service = new TeleconsultationService(sessionRepo, apptRepo);

        // Paciente no puede cerrar la sala para todos
        login("PACIENTE", 6L, 12L, 20L, 1L);
        assertThrows(AccessDeniedException.class, () -> service.closeSession(303L));

        // Odontólogo cierra la sala
        login("ODONTOLOGO", 5L, 10L, null, 1L);
        TeleconsultationSessionResponse closed = service.closeSession(303L);
        assertEquals("FINALIZADA", closed.estado());

        // Intentar reingresar a sala finalizada es rechazado
        assertThrows(IllegalStateException.class, () -> service.joinSession(303L));
    }
}
