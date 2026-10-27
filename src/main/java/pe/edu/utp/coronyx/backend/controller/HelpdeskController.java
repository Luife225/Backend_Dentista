package pe.edu.utp.coronyx.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.HelpdeskBitacoraDto;
import pe.edu.utp.coronyx.backend.dto.HelpdeskCatalogDto;
import pe.edu.utp.coronyx.backend.dto.HelpdeskTicketDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping({"/api/v1/helpdesk", "/helpdesk"})
public class HelpdeskController {

    private final JdbcTemplate jdbcTemplate;

    private final List<HelpdeskCatalogDto> catalog = new CopyOnWriteArrayList<>();
    private final List<HelpdeskTicketDto> tickets = new CopyOnWriteArrayList<>();
    private final List<HelpdeskBitacoraDto> bitacora = new CopyOnWriteArrayList<>();
    private final AtomicLong bitacoraIdGen = new AtomicLong(100);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public HelpdeskController(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initFallbackCatalog();
        initFallbackTickets();
    }

    private void initFallbackCatalog() {
        catalog.add(new HelpdeskCatalogDto("INC-PAC-001", "PACIENTE", "Reprogramación urgente de cita odontológica (falta < 2 horas)", "El paciente solicita cambio de horario de emergencia y no puede esperar.", "Asistencia", 3, 2, "ALTO", "RECEPCIONISTA", 20, "/app/agenda", "📅 Ir a Cita en Agenda"));
        catalog.add(new HelpdeskCatalogDto("INC-PAC-002", "PACIENTE", "Corrección de DNI o Nombre por error de digitación en admisión", "El paciente detecta que sus datos de identidad fueron mal ingresados.", "Asistencia", 2, 2, "MEDIO", "ADMIN_CLINICA", 60, "/app/pacientes", "👤 Ir a Ficha de Paciente"));
        catalog.add(new HelpdeskCatalogDto("INC-PAC-003", "PACIENTE", "Radiografía o receta médica no visible en portal del paciente", "El paciente tuvo consulta pero los archivos adjuntos permanecen ocultos.", "Incidencia", 2, 2, "MEDIO", "ODONTOLOGO", 30, "/app/pacientes", "📁 Ir a Archivos de Paciente"));
        catalog.add(new HelpdeskCatalogDto("INC-PAC-004", "PACIENTE", "Falla de conexión, video o audio en sala de Teleodontología", "No se establece enlace WebRTC o token Jitsi en sesión en vivo.", "Incidencia", 3, 3, "CRITICO", "SOPORTE_TECNICO", 10, "/app/teleodontologia", "📹 Ir a Sala Virtual"));
        catalog.add(new HelpdeskCatalogDto("INC-PAC-005", "PACIENTE", "Consulta sobre horarios de atención de fin de semana o precios", "Consulta informativa previa sobre turnos y aranceles de sede.", "Información", 2, 1, "BAJO", "RECEPCIONISTA", 120, "/app/agenda", "ℹ️ Ver Horarios de Sede"));

        catalog.add(new HelpdeskCatalogDto("INC-ODO-001", "ODONTOLOGO", "Reasignación urgente de Sillón dental por retraso en cirugía previa", "Procedimiento quirúrgico se extiende y siguiente paciente está en sala.", "Incidencia", 2, 3, "ALTO", "RECEPCIONISTA", 15, "/app/agenda", "📅 Reubicar Sillón en Agenda"));
        catalog.add(new HelpdeskCatalogDto("INC-ODO-002", "ODONTOLOGO", "Error 413 al cargar archivo binario pesado de radiografía (> 25MB)", "La imagen panorámica supera el umbral estándar del servidor.", "Incidencia", 2, 2, "MEDIO", "SOPORTE_TECNICO", 30, "/app/pacientes", "📁 Subida Manual"));
        catalog.add(new HelpdeskCatalogDto("INC-ODO-003", "ODONTOLOGO", "Solicitud de adenda médica en historia clínica cerrada por auditoría", "Necesidad de incorporar observación diagnóstica complementaria.", "Asistencia", 1, 2, "BAJO", "ADMIN_CLINICA", 120, "/app/pacientes", "🦷 Ver Historia Clínica"));

        catalog.add(new HelpdeskCatalogDto("INC-REC-001", "RECEPCIONISTA", "Liberación de turno y sillón por inasistencia física (Paciente No-Show)", "Paciente no se presentó en tolerancia de 15 minutos; liberar sillón.", "Incidencia", 3, 2, "ALTO", "RECEPCIONISTA", 15, "/app/agenda", "📅 Gestionar No-Show"));
        catalog.add(new HelpdeskCatalogDto("INC-REC-002", "RECEPCIONISTA", "Solicitud de turno de paciente fuera del horario laboral de la sede", "Paciente insiste en turno nocturno o feriado; requiere autorización.", "Información", 2, 1, "BAJO", "ADMIN_CLINICA", 120, "/app/agenda", "⚙️ Ver Franja Laboral"));

        catalog.add(new HelpdeskCatalogDto("INC-ADM-001", "ADMIN_CLINICA", "Bloqueo urgente de credenciales por desvinculación laboral de personal", "Cese de contrato con acceso activo a historias clínicas.", "Incidencia", 1, 3, "ALTO", "ADMIN_CLINICA", 15, "/app/consultorio", "👥 Desactivar Credenciales"));
        catalog.add(new HelpdeskCatalogDto("INC-ADM-002", "ADMIN_CLINICA", "Caída de base de datos o error 500 en servicios de Railway Cloud", "Indisponibilidad del backend o lentitud en consultas de facturación.", "Incidencia", 1, 3, "CRITICO", "SOPORTE_TECNICO", 10, "/app/super-admin", "🖥️ Monitoreo de Servidor"));

        catalog.add(new HelpdeskCatalogDto("INC-GEN-099", "TODOS", "Otros requerimientos o fallas no contempladas en el catálogo (Con Captura)", "Caja de texto libre para detallar el problema y adjuntar captura de evidencia.", "Asistencia", 2, 2, "MEDIO", "ADMIN_CLINICA", 60, null, "📋 Triaje Administrativo"));
    }

    private void initFallbackTickets() {
        // Vacio: Permite iniciar el sistema con 0 tickets y datos 100% reales
    }

    public static String calculatePriority(int impacto, int probabilidad) {
        int score = impacto * probabilidad;
        if (score >= 9) return "CRITICO";
        if (score >= 6) return "ALTO";
        if (score >= 3) return "MEDIO";
        return "BAJO";
    }

    /**
     * Catálogo de incidencias: lee directamente de PostgreSQL (catalogo_incidencia)
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<HelpdeskCatalogDto>> getCatalog(@RequestParam(required = false) String rol) {
        if (jdbcTemplate != null) {
            try {
                String sql = """
                    SELECT id, solicitante_rol, titulo, descripcion, tipo_itil,
                           probabilidad, impacto, prioridad, responsable_rol,
                           sla_minutos, ruta_accion, accion_label
                    FROM catalogo_incidencia
                    WHERE activo = true
                    ORDER BY id ASC
                """;
                List<HelpdeskCatalogDto> dbCatalog = jdbcTemplate.query(sql, (rs, rowNum) -> new HelpdeskCatalogDto(
                        rs.getString("id"),
                        rs.getString("solicitante_rol"),
                        rs.getString("titulo"),
                        rs.getString("descripcion"),
                        rs.getString("tipo_itil"),
                        rs.getInt("impacto"),
                        rs.getInt("probabilidad"),
                        rs.getString("prioridad"),
                        rs.getString("responsable_rol"),
                        rs.getInt("sla_minutos"),
                        rs.getString("ruta_accion"),
                        rs.getString("accion_label")
                ));

                if (!dbCatalog.isEmpty()) {
                    if (rol == null || rol.isBlank()) {
                        return ResponseEntity.ok(dbCatalog);
                    }
                    String clean = rol.trim().toUpperCase();
                    List<HelpdeskCatalogDto> filtered = dbCatalog.stream()
                            .filter(c -> c.solicitanteRol().equalsIgnoreCase(clean) || c.solicitanteRol().equalsIgnoreCase("TODOS"))
                            .toList();
                    return ResponseEntity.ok(filtered);
                }
            } catch (Exception e) {
                System.err.println("[HelpdeskController] Advertencia leyendo catalogo_incidencia en PostgreSQL: " + e.getMessage());
            }
        }

        if (rol == null || rol.isBlank()) {
            return ResponseEntity.ok(new ArrayList<>(catalog));
        }
        String cleanRole = rol.trim().toUpperCase();
        List<HelpdeskCatalogDto> filtered = catalog.stream()
                .filter(c -> c.solicitanteRol().equalsIgnoreCase(cleanRole) || c.solicitanteRol().equalsIgnoreCase("TODOS"))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    /**
     * Obtiene todos los tickets directamente de PostgreSQL (mesa_ayuda_ticket)
     * ordenados por Prioridad de Riesgo (CRITICO > ALTO > MEDIO > BAJO) y luego por Fecha
     */
    @GetMapping("/tickets")
    public ResponseEntity<List<HelpdeskTicketDto>> getAllTickets() {
        if (jdbcTemplate != null) {
            try {
                String sql = """
                    SELECT id, catalogo_id, solicitud,
                           COALESCE(solicitante_nombre, solicitante_rol) as solicitante,
                           solicitante_rol,
                           COALESCE(responsable_nombre, responsable_rol) as responsable,
                           responsable_rol,
                           probabilidad, impacto, prioridad, estado, tiempo_transcurrido,
                           tipo_itil, sla_minutos, evidencia,
                           to_char(fecha_registro, 'YYYY-MM-DD HH24:MI') as fecha_reg,
                           to_char(fecha_resolucion, 'YYYY-MM-DD HH24:MI') as fecha_res,
                           descripcion_detallada, archivo_adjunto_url,
                           cita_id, paciente_id, ruta_accion, accion_label
                    FROM mesa_ayuda_ticket
                    ORDER BY 
                      CASE UPPER(prioridad)
                        WHEN 'CRITICO' THEN 4
                        WHEN 'ALTO' THEN 3
                        WHEN 'MEDIO' THEN 2
                        ELSE 1
                      END DESC,
                      fecha_registro DESC
                """;
                List<HelpdeskTicketDto> dbTickets = jdbcTemplate.query(sql, (rs, rowNum) -> new HelpdeskTicketDto(
                        rs.getString("id"),
                        rs.getString("catalogo_id"),
                        rs.getString("solicitud"),
                        rs.getString("solicitante"),
                        rs.getString("solicitante_rol"),
                        rs.getString("responsable"),
                        rs.getString("responsable_rol"),
                        rs.getInt("probabilidad"),
                        rs.getInt("impacto"),
                        rs.getString("prioridad"),
                        rs.getString("estado"),
                        rs.getString("tiempo_transcurrido"),
                        rs.getString("tipo_itil"),
                        rs.getInt("sla_minutos"),
                        rs.getString("evidencia"),
                        rs.getString("fecha_reg"),
                        rs.getString("fecha_res"),
                        rs.getString("descripcion_detallada"),
                        rs.getString("archivo_adjunto_url"),
                        rs.getObject("cita_id") != null ? rs.getLong("cita_id") : null,
                        rs.getObject("paciente_id") != null ? rs.getLong("paciente_id") : null,
                        rs.getString("ruta_accion"),
                        rs.getString("accion_label")
                ));
                return ResponseEntity.ok(dbTickets);
            } catch (Exception e) {
                System.err.println("[HelpdeskController] Advertencia leyendo mesa_ayuda_ticket en PostgreSQL: " + e.getMessage());
            }
        }

        List<HelpdeskTicketDto> sorted = new ArrayList<>(tickets);
        sorted.sort((a, b) -> {
            int pA = priorityWeight(a.prioridad());
            int pB = priorityWeight(b.prioridad());
            if (pA != pB) return Integer.compare(pB, pA);
            return b.fechaRegistro().compareTo(a.fechaRegistro());
        });
        return ResponseEntity.ok(sorted);
    }

    private int priorityWeight(String prio) {
        if (prio == null) return 1;
        return switch (prio.toUpperCase()) {
            case "CRITICO", "CRÍTICO" -> 4;
            case "ALTO" -> 3;
            case "MEDIO" -> 2;
            default -> 1;
        };
    }

    @PostMapping("/tickets")
    public ResponseEntity<HelpdeskTicketDto> createTicket(@RequestBody HelpdeskTicketDto dto) {
        String newId = dto.id();
        if (newId == null || newId.isBlank() || isIdTaken(newId)) {
            newId = computeNextTicketId();
        }

        int imp = dto.impacto() != null && dto.impacto() > 0 ? dto.impacto() : 2;
        int prob = dto.probabilidad() != null && dto.probabilidad() > 0 ? dto.probabilidad() : 2;
        String prioridad = dto.prioridad();
        String rutaAccion = dto.rutaAccion();
        String accionLabel = dto.accionLabel();
        int sla = dto.slaMinutos() != null && dto.slaMinutos() > 0 ? dto.slaMinutos() : 60;
        String respRol = dto.responsableRol();
        String respNombre = dto.responsable();

        if (dto.catalogoId() != null && !dto.catalogoId().isBlank()) {
            for (HelpdeskCatalogDto c : catalog) {
                if (c.id().equalsIgnoreCase(dto.catalogoId())) {
                    imp = c.impacto();
                    prob = c.probabilidad();
                    prioridad = c.prioridad();
                    sla = c.slaMinutos();
                    respRol = c.responsableRol();
                    respNombre = switch (respRol) {
                        case "RECEPCIONISTA" -> "Recepcionista";
                        case "ADMIN_CLINICA" -> "Admin Clínica";
                        case "ODONTOLOGO" -> "Odontólogo";
                        default -> "Soporte Técnico TI";
                    };
                    if (rutaAccion == null) rutaAccion = c.rutaAccion();
                    if (accionLabel == null) accionLabel = c.accionLabel();
                    break;
                }
            }
        }

        if (prioridad == null || prioridad.isBlank()) {
            prioridad = calculatePriority(imp, prob);
        }

        String nowStr = LocalDateTime.now().format(FORMATTER);

        HelpdeskTicketDto created = new HelpdeskTicketDto(
                newId,
                dto.catalogoId(),
                dto.solicitud(),
                dto.solicitante() != null ? dto.solicitante() : "Paciente",
                dto.solicitanteRol() != null ? dto.solicitanteRol() : "PACIENTE",
                respNombre != null ? respNombre : "Admin Clínica",
                respRol != null ? respRol : "ADMIN_CLINICA",
                prob,
                imp,
                prioridad,
                "PENDIENTE",
                "1 min",
                dto.tipoItil() != null ? dto.tipoItil() : "Incidencia",
                sla,
                null,
                nowStr,
                null,
                dto.descripcionDetallada(),
                dto.archivoAdjuntoUrl(),
                dto.citaId(),
                dto.pacienteId(),
                rutaAccion,
                accionLabel
        );

        // 1. Guardar formalmente en PostgreSQL
        if (jdbcTemplate != null) {
            try {
                // Validar llaves foráneas para evitar violación de restricciones referenciales
                Long safeClinicaId = null;
                try {
                    safeClinicaId = jdbcTemplate.queryForObject("SELECT id FROM clinica ORDER BY id ASC LIMIT 1", Long.class);
                } catch (Exception ignored) {}

                String safeCatalogoId = null;
                if (dto.catalogoId() != null && !dto.catalogoId().isBlank()) {
                    try {
                        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM catalogo_incidencia WHERE id = ?", Integer.class, dto.catalogoId());
                        if (count != null && count > 0) {
                            safeCatalogoId = dto.catalogoId();
                        }
                    } catch (Exception ignored) {}
                }

                Long safeCitaId = null;
                if (created.citaId() != null && created.citaId() > 0) {
                    try {
                        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM cita WHERE id = ?", Integer.class, created.citaId());
                        if (count != null && count > 0) {
                            safeCitaId = created.citaId();
                        }
                    } catch (Exception ignored) {}
                }

                Long safePacienteId = null;
                if (created.pacienteId() != null && created.pacienteId() > 0) {
                    try {
                        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM paciente WHERE id = ?", Integer.class, created.pacienteId());
                        if (count != null && count > 0) {
                            safePacienteId = created.pacienteId();
                        }
                    } catch (Exception ignored) {}
                }

                String sql = """
                    INSERT INTO mesa_ayuda_ticket (
                        id, clinica_id, catalogo_id, solicitud, solicitante_nombre, solicitante_rol,
                        responsable_nombre, responsable_rol, probabilidad, impacto, prioridad,
                        estado, tiempo_transcurrido, tipo_itil, sla_minutos, evidencia,
                        fecha_registro, descripcion_detallada, archivo_adjunto_url,
                        cita_id, paciente_id, ruta_accion, accion_label
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        'PENDIENTE', '1 min', ?, ?, NULL,
                        CURRENT_TIMESTAMP, ?, ?,
                        ?, ?, ?, ?
                    )
                """;
                jdbcTemplate.update(sql,
                        newId,
                        safeClinicaId,
                        safeCatalogoId,
                        created.solicitud(),
                        created.solicitante(),
                        created.solicitanteRol(),
                        created.responsable(),
                        created.responsableRol(),
                        prob,
                        imp,
                        prioridad,
                        created.tipoItil(),
                        sla,
                        created.descripcionDetallada(),
                        created.archivoAdjuntoUrl(),
                        safeCitaId,
                        safePacienteId,
                        rutaAccion,
                        accionLabel
                );

                // Insertar en mesa_ayuda_bitacora
                jdbcTemplate.update("""
                    INSERT INTO mesa_ayuda_bitacora (
                        ticket_id, fecha_hora, estado_anterior, estado_nuevo,
                        usuario_nombre, usuario_rol, accion, comentario_evidencia, tiempo_transcurrido_minutos
                    ) VALUES (?, CURRENT_TIMESTAMP, NULL, 'PENDIENTE', ?, ?, 'CREACION', ?, 0)
                """, newId, created.solicitante(), created.solicitanteRol(), created.solicitud());

                System.out.println("[HelpdeskController] ✅ Ticket " + newId + " insertado formalmente en PostgreSQL mesa_ayuda_ticket.");
            } catch (Exception e) {
                System.err.println("[HelpdeskController] Error al guardar en PostgreSQL mesa_ayuda_ticket: " + e.getMessage());
            }
        }

        // 2. Mantener en memoria
        tickets.removeIf(t -> t.id().equalsIgnoreCase(created.id()));
        tickets.add(0, created);

        bitacora.add(new HelpdeskBitacoraDto(
                bitacoraIdGen.incrementAndGet(),
                newId,
                nowStr,
                null,
                "PENDIENTE",
                created.solicitante(),
                created.solicitanteRol(),
                "CREACION",
                created.solicitud()
        ));

        return ResponseEntity.ok(created);
    }

    private boolean isIdTaken(String id) {
        if (id == null || id.isBlank()) return false;
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM mesa_ayuda_ticket WHERE UPPER(id) = UPPER(?)",
                        Integer.class,
                        id
                );
                if (count != null && count > 0) return true;
            } catch (Exception ignored) {}
        }
        return tickets.stream().anyMatch(t -> t.id().equalsIgnoreCase(id));
    }

    private synchronized String computeNextTicketId() {
        int next = 1;
        if (jdbcTemplate != null) {
            try {
                Integer maxDb = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(CAST(SUBSTRING(id FROM 5) AS INTEGER)), 0) FROM mesa_ayuda_ticket WHERE id ~ '^TKT-[0-9]+$'",
                        Integer.class
                );
                if (maxDb != null && maxDb >= next) {
                    next = maxDb + 1;
                }
            } catch (Exception ignored) {}
        }

        int maxMem = tickets.stream()
                .mapToInt(t -> {
                    try {
                        return Integer.parseInt(t.id().replaceAll("\\D", ""));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        if (maxMem >= next) {
            next = maxMem + 1;
        }

        String candidate = String.format("TKT-%03d", next);
        while (isIdTaken(candidate)) {
            next++;
            candidate = String.format("TKT-%03d", next);
        }
        return candidate;
    }

    /**
     * Transición de estado auditada: actualiza mesa_ayuda_ticket y agrega traza a mesa_ayuda_bitacora en PostgreSQL
     */
    @PatchMapping("/tickets/{id}/status")
    public ResponseEntity<HelpdeskTicketDto> updateTicketStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {

        String targetStatus = payload.get("estado");
        if (targetStatus == null || targetStatus.isBlank()) {
            targetStatus = payload.get("nuevoEstado");
        }
        if (targetStatus == null || targetStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        targetStatus = targetStatus.trim().toUpperCase();

        String usuario = payload.getOrDefault("usuarioNombre", "Resolutor");
        String rol = payload.getOrDefault("usuarioRol", "ADMIN_CLINICA");
        String evidencia = payload.get("evidencia");
        String nowStr = LocalDateTime.now().format(FORMATTER);

        String oldStatus = "PENDIENTE";

        // 1. Actualizar en memoria
        HelpdeskTicketDto updated = null;
        for (int i = 0; i < tickets.size(); i++) {
            HelpdeskTicketDto cur = tickets.get(i);
            if (cur.id().equalsIgnoreCase(id)) {
                oldStatus = cur.estado();
                updated = new HelpdeskTicketDto(
                        cur.id(),
                        cur.catalogoId(),
                        cur.solicitud(),
                        cur.solicitante(),
                        cur.solicitanteRol(),
                        cur.responsable(),
                        cur.responsableRol(),
                        cur.probabilidad(),
                        cur.impacto(),
                        cur.prioridad(),
                        targetStatus,
                        cur.tiempoTranscurrido(),
                        cur.tipoItil(),
                        cur.slaMinutos(),
                        evidencia != null ? evidencia : cur.evidencia(),
                        cur.fechaRegistro(),
                        targetStatus.equals("ATENDIDO") ? nowStr : cur.fechaResolucion(),
                        cur.descripcionDetallada(),
                        cur.archivoAdjuntoUrl(),
                        cur.citaId(),
                        cur.pacienteId(),
                        cur.rutaAccion(),
                        cur.accionLabel()
                );
                tickets.set(i, updated);
                break;
            }
        }

        String accion = switch (targetStatus) {
            case "EN_PROCESO" -> "INICIO_ATENCION";
            case "ATENDIDO" -> "RESOLUCION";
            case "CANCELADO" -> "CANCELACION";
            default -> "CAMBIO_ESTADO";
        };

        // 2. Persistir formalmente en PostgreSQL (mesa_ayuda_ticket y mesa_ayuda_bitacora)
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                    UPDATE mesa_ayuda_ticket
                    SET estado = ?,
                        evidencia = COALESCE(?, evidencia),
                        fecha_resolucion = CASE WHEN ? = 'ATENDIDO' THEN CURRENT_TIMESTAMP ELSE fecha_resolucion END
                    WHERE UPPER(id) = UPPER(?)
                """, targetStatus, evidencia, targetStatus, id);

                jdbcTemplate.update("""
                    INSERT INTO mesa_ayuda_bitacora (
                        ticket_id, fecha_hora, estado_anterior, estado_nuevo,
                        usuario_nombre, usuario_rol, accion, comentario_evidencia, tiempo_transcurrido_minutos
                    ) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, 15)
                """, id, oldStatus, targetStatus, usuario, rol, accion, evidencia != null ? evidencia : "Transición formal a " + targetStatus);

                System.out.println("[HelpdeskController] ✅ Ticket " + id + " actualizado a " + targetStatus + " en PostgreSQL mesa_ayuda_ticket.");
            } catch (Exception e) {
                System.err.println("[HelpdeskController] Error actualizando PostgreSQL: " + e.getMessage());
            }
        }

        // Agregar a bitacora en memoria
        bitacora.add(new HelpdeskBitacoraDto(
                bitacoraIdGen.incrementAndGet(),
                id,
                nowStr,
                oldStatus,
                targetStatus,
                usuario,
                rol,
                accion,
                evidencia != null ? evidencia : "Transición formal a " + targetStatus
        ));

        if (updated != null) {
            return ResponseEntity.ok(updated);
        }

        // Si no estaba en memoria pero se actualizó en PostgreSQL, devolver DTO sintetizado
        return ResponseEntity.ok(new HelpdeskTicketDto(
                id, null, "Ticket " + id, usuario, rol, usuario, rol,
                2, 2, "MEDIO", targetStatus, "15 min", "Incidencia", 60,
                evidencia, nowStr, nowStr, null, null, null, null, null, null
        ));
    }

    /**
     * Endpoint de compatibilidad hacia atrás: resolver ticket directamente
     */
    @PutMapping("/tickets/{id}/resolve")
    public ResponseEntity<HelpdeskTicketDto> resolveTicket(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload) {

        Map<String, String> patchPayload = new HashMap<>();
        patchPayload.put("estado", "ATENDIDO");
        if (payload != null && payload.containsKey("evidencia")) {
            patchPayload.put("evidencia", payload.get("evidencia"));
        } else {
            patchPayload.put("evidencia", "Atendido y resuelto formalmente por mano humana.");
        }
        if (payload != null && payload.containsKey("usuarioNombre")) {
            patchPayload.put("usuarioNombre", payload.get("usuarioNombre"));
        }
        if (payload != null && payload.containsKey("usuarioRol")) {
            patchPayload.put("usuarioRol", payload.get("usuarioRol"));
        }

        return updateTicketStatus(id, patchPayload);
    }

    /**
     * Obtiene el historial de bitácora directamente de PostgreSQL (mesa_ayuda_bitacora)
     */
    @GetMapping("/tickets/{id}/bitacora")
    public ResponseEntity<List<HelpdeskBitacoraDto>> getTicketBitacora(@PathVariable String id) {
        if (jdbcTemplate != null) {
            try {
                String sql = """
                    SELECT id, ticket_id, to_char(fecha_hora, 'YYYY-MM-DD HH24:MI') as f_hora,
                           estado_anterior, estado_nuevo, usuario_nombre, usuario_rol,
                           accion, comentario_evidencia
                    FROM mesa_ayuda_bitacora
                    WHERE UPPER(ticket_id) = UPPER(?)
                    ORDER BY id ASC
                """;
                List<HelpdeskBitacoraDto> dbHistory = jdbcTemplate.query(sql, (rs, rowNum) -> new HelpdeskBitacoraDto(
                        rs.getLong("id"),
                        rs.getString("ticket_id"),
                        rs.getString("f_hora"),
                        rs.getString("estado_anterior"),
                        rs.getString("estado_nuevo"),
                        rs.getString("usuario_nombre"),
                        rs.getString("usuario_rol"),
                        rs.getString("accion"),
                        rs.getString("comentario_evidencia")
                ), id);

                if (!dbHistory.isEmpty()) {
                    return ResponseEntity.ok(dbHistory);
                }
            } catch (Exception e) {
                System.err.println("[HelpdeskController] Advertencia leyendo mesa_ayuda_bitacora en PostgreSQL: " + e.getMessage());
            }
        }

        List<HelpdeskBitacoraDto> history = bitacora.stream()
                .filter(b -> b.ticketId().equalsIgnoreCase(id))
                .sorted(Comparator.comparing(HelpdeskBitacoraDto::id))
                .toList();
        return ResponseEntity.ok(history);
    }
}
