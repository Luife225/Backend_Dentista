package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.*;

@Service
@Transactional
public class InventoryCatalogService {
    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final CatalogImportBatchRepository batchRepository;

    private static final Set<String> CLINICAL_SERVICE_KEYWORDS = Set.of(
            "CURACION", "TRATAMIENTO", "ENDODONCIA", "EXTRACCION", "CONSULTA",
            "BLANQUEAMIENTO", "PROFILAXIS", "IMPLANTE", "RADIOGRAFIA", "CIRUGIA",
            "SESION", "RESTAURACION", "ORTODONCIA", "CORONA", "DIAGNOSTICO"
    );

    private static final Set<String> REUSABLE_KEYWORDS = Set.of(
            "PINZA", "ESPEJO", "EXPLORADOR", "TURBINA", "MICROMOTOR", "FORCEPS",
            "INSTRUMENTAL", "ALICATE", "PORTA", "TIJERA", "BANDEJA", "CURETA", "ESPONJERO"
    );

    public InventoryCatalogService(InventoryItemRepository itemRepository,
                                  InventoryMovementRepository movementRepository,
                                  CatalogImportBatchRepository batchRepository) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.batchRepository = batchRepository;
    }

    private UserPrincipal adminPrincipal() {
        UserPrincipal p = SecurityUtils.getCurrentPrincipal();
        if (p.getClinicaId() == null || p.getMembership() == null || !"ADMIN_CLINICA".equals(p.getRol())) {
            throw new AccessDeniedException("La importación de catálogo está reservada al administrador de la clínica");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public CatalogPreviewResponse preview(List<RawCatalogRow> rows) {
        UserPrincipal p = adminPrincipal();
        if (rows == null || rows.isEmpty()) {
            return new CatalogPreviewResponse("lote-" + UUID.randomUUID().toString().substring(0, 8),
                    0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }

        List<InventoryItem> existingItems = itemRepository.findByClinicaIdOrderByNombreAsc(p.getClinicaId());
        Set<String> existingCodes = new HashSet<>();
        Set<String> existingNames = new HashSet<>();
        for (InventoryItem it : existingItems) {
            if (it.codigo != null && !it.codigo.isBlank()) existingCodes.add(it.codigo.trim().toUpperCase());
            if (it.nombre != null && !it.nombre.isBlank()) existingNames.add(it.nombre.trim().toUpperCase());
        }

        // Detectar códigos repetidos en el archivo
        Map<String, Integer> codeCounts = new HashMap<>();
        for (RawCatalogRow r : rows) {
            if (r.codigo() != null && !r.codigo().isBlank()) {
                String c = r.codigo().trim();
                codeCounts.put(c, codeCounts.getOrDefault(c, 0) + 1);
            }
        }

        List<CatalogPreviewItem> items = new ArrayList<>();
        List<CatalogRowAnalysisDto> filas = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        int candidatas = 0;
        int pendientes = 0;
        int importables = 0;
        int serviciosOmitidos = 0;
        int existentes = 0;

        int rowNum = 1;
        for (RawCatalogRow r : rows) {
            String rawCode = r.codigo() != null ? r.codigo().trim() : "";
            String rawUm = r.effectiveUm() != null ? r.effectiveUm().trim().toUpperCase() : "";
            String rawDesc = r.descripcion() != null ? r.descripcion().trim() : "";
            BigDecimal price = r.effectivePrice();

            if (rawDesc.isBlank() && rawCode.isBlank()) {
                rowNum++;
                continue; // Fila vacía
            }

            boolean isPending = false;
            List<String> reasons = new ArrayList<>();

            // 1. Revisión de código repetido
            boolean isDuplicateCode = false;
            if (!rawCode.isBlank() && codeCounts.getOrDefault(rawCode, 0) > 1) {
                isPending = true;
                isDuplicateCode = true;
                reasons.add("Código repetido (" + codeCounts.get(rawCode) + " apariciones); verificar variante");
            }

            // 2. Detección de servicios o procedimientos clínicos
            String descUpper = rawDesc.toUpperCase();
            String famUpper = r.familia() != null ? r.familia().toUpperCase() : "";
            boolean isService = CLINICAL_SERVICE_KEYWORDS.stream().anyMatch(descUpper::contains)
                    || rawCode.matches("^[dD]\\d{4}$")
                    || famUpper.contains("PROCED")
                    || famUpper.contains("CIRUGIA")
                    || famUpper.contains("SERVICIO");

            if (isService) {
                isPending = true;
                serviciosOmitidos++;
                reasons.add("Parece tratamiento o servicio clínico; separar de existencias de insumos");
            }

            // 3. Mapeo de Presentación
            String presentacion;
            String unidadSugerida;
            if (rawUm.contains("CAJA") || rawUm.equals("CJ") || rawUm.equals("CJA")) {
                presentacion = "CAJA";
                unidadSugerida = "cajas";
            } else if (rawUm.contains("FCO") || rawUm.contains("FRASCO") || rawUm.contains("BOTELLA") || rawUm.contains("ENVASE")) {
                presentacion = "ENVASE";
                unidadSugerida = "envases";
            } else if (rawUm.contains("UND") || rawUm.contains("UNIDAD") || rawUm.contains("PZA") || rawUm.contains("PIEZA") || rawUm.equals("U")) {
                presentacion = "UNIDAD";
                unidadSugerida = "unidades";
            } else {
                presentacion = "UNIDAD";
                unidadSugerida = rawUm.isBlank() ? "unidades" : rawUm.toLowerCase();
                isPending = true;
                reasons.add("Presentación no estándar ('" + (rawUm.isBlank() ? "Vacía" : rawUm) + "'); requiere asignación UNIDAD/CAJA/ENVASE");
            }

            // 4. Clasificación consumible vs reutilizable
            boolean isReusable = REUSABLE_KEYWORDS.stream().anyMatch(descUpper::contains);
            String clasificacion = isReusable ? "REUTILIZABLE" : "CONSUMIBLE";

            // 5. Extracción opcional de contenido descriptivo (ej. "100 guantes", "500 ml")
            String contenidoDescriptivo = null;
            Matcher m = Pattern.compile("(?i)(?:x|de)?\\s*(\\d+\\s*(?:ml|mg|gr|piezas|guantes|unidades|und|hojas|tabletas|tubos|ampollas|carpules))").matcher(rawDesc);
            if (m.find()) {
                contenidoDescriptivo = m.group(1).trim();
            }

            // 6. Precio
            BigDecimal costoUnitario = BigDecimal.ZERO;
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                costoUnitario = price;
            } else {
                reasons.add("Precio no informado; quedará en 0.00 hasta registrar compra real");
            }

            // 7. Existencia en inventario
            boolean isExisting = (!rawCode.isBlank() && existingCodes.contains(rawCode.toUpperCase()))
                    || (!rawDesc.isBlank() && existingNames.contains(rawDesc.toUpperCase()));
            if (isExisting) {
                existentes++;
            }

            boolean isImportable = !isService && !isExisting;
            if (isImportable) {
                importables++;
            }

            String estadoRevision = isPending ? "PENDIENTE" : "APROBADO";
            if (isPending) {
                pendientes++;
            } else {
                candidatas++;
            }

            String motivo = reasons.isEmpty() ? null : String.join("; ", reasons);
            String categoria = isService ? "Servicios odontológicos" : (r.familia() != null && !r.familia().isBlank() ? r.familia() : "Material dental");

            items.add(new CatalogPreviewItem(
                    rawCode.isBlank() ? null : rawCode,
                    rawDesc,
                    categoria,
                    presentacion,
                    clasificacion,
                    contenidoDescriptivo,
                    costoUnitario,
                    estadoRevision,
                    motivo
            ));

            filas.add(new CatalogRowAnalysisDto(
                    rowNum,
                    rawCode,
                    rawDesc,
                    rawUm.isBlank() ? "unidad" : rawUm.toLowerCase(),
                    isService ? "SERVICIO_PROCEDIMIENTO" : "INSUMO_FISICO",
                    presentacion,
                    unidadSugerida,
                    clasificacion,
                    contenidoDescriptivo,
                    categoria,
                    costoUnitario,
                    isImportable,
                    isExisting,
                    isExisting ? "Ya registrado en inventario" : (isService ? "Servicio clínico (se omite del inventario físico)" : "Listo para importar"),
                    isService ? "Procedimiento/servicio clínico" : (isExisting ? "Insumo ya existente" : null),
                    isDuplicateCode
            ));

            rowNum++;
        }

        if (serviciosOmitidos > 0) {
            advertencias.add("Se identificaron " + serviciosOmitidos + " procedimientos o servicios clínicos que fueron filtrados para no afectar el stock de materiales.");
        }
        if (existentes > 0) {
            advertencias.add("Se identificaron " + existentes + " insumos que ya se encuentran registrados en su inventario.");
        }

        String loteId = "lote-" + UUID.randomUUID().toString().substring(0, 8);
        return new CatalogPreviewResponse(
                loteId,
                rows.size(),
                candidatas,
                pendientes,
                importables,
                serviciosOmitidos,
                existentes,
                items,
                filas,
                advertencias
        );
    }


    public CatalogImportConfirmResponse confirm(CatalogImportConfirmRequest request) {
        UserPrincipal p = adminPrincipal();

        // Evitar reimportación del mismo lote
        if (batchRepository.existsByClinicaIdAndLoteId(p.getClinicaId(), request.loteId())) {
            throw new IllegalStateException("El lote de importación '" + request.loteId() + "' ya fue procesado anteriormente");
        }

        int importedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();

        for (CatalogImportItemRequest itemDto : request.items()) {
            InventoryItem item = new InventoryItem();
            item.clinica = p.getClinic();
            item.codigo = itemDto.codigo() != null && !itemDto.codigo().isBlank() ? itemDto.codigo().trim() : null;
            item.nombre = itemDto.nombre().trim();
            item.categoria = itemDto.categoria().trim();
            item.unidad = itemDto.presentacion().toLowerCase();
            item.presentacion = itemDto.presentacion().toUpperCase();
            item.clasificacion = itemDto.clasificacion().toUpperCase();
            item.contenidoDescriptivo = itemDto.contenidoDescriptivo();
            item.costoUnitario = itemDto.costoUnitario() != null && itemDto.costoUnitario().compareTo(BigDecimal.ZERO) >= 0 ? itemDto.costoUnitario() : BigDecimal.ZERO;
            item.stockMinimo = 0;
            item.activo = true;
            item.fechaCreacion = now;

            int cerrado = itemDto.stockCerradoInicial() != null && itemDto.stockCerradoInicial() > 0 ? itemDto.stockCerradoInicial() : 0;
            int enUso = itemDto.stockEnUsoInicial() != null && itemDto.stockEnUsoInicial() > 0 ? itemDto.stockEnUsoInicial() : 0;
            int total = cerrado + enUso;

            item.stockCerrado = cerrado;
            item.stockEnUso = enUso;
            item.stock = total;

            item = itemRepository.saveAndFlush(item);

            // Si hay existencias iniciales por conteo, registrar movimiento auditable de AJUSTE / Conteo Inicial
            if (total > 0) {
                InventoryMovement m = new InventoryMovement();
                m.clinica = p.getClinic();
                m.insumo = item;
                m.creadoPor = p.getMembership();
                m.tipo = "AJUSTE";
                m.cantidad = total;
                m.estadoOrigen = null;
                m.estadoDestino = enUso > 0 ? "EN_USO" : "CERRADO";
                m.stockCerradoAnterior = 0;
                m.stockCerradoResultante = cerrado;
                m.stockEnUsoAnterior = 0;
                m.stockEnUsoResultante = enUso;
                m.stockAnterior = 0;
                m.stockResultante = total;
                m.motivo = itemDto.motivoConteo() != null && !itemDto.motivoConteo().isBlank()
                        ? itemDto.motivoConteo().trim()
                        : "Conteo físico inicial verificado (" + cerrado + " cerrados, " + enUso + " en uso)";
                m.solicitudId = request.loteId() + "-item-" + item.getId();
                m.fechaCreacion = now;
                movementRepository.saveAndFlush(m);
            }

            importedCount++;
        }

        CatalogImportBatch batch = new CatalogImportBatch();
        batch.clinica = p.getClinic();
        batch.loteId = request.loteId();
        batch.nombreArchivo = request.nombreArchivo();
        batch.hoja = request.hoja();
        batch.totalFilas = request.items().size();
        batch.filasImportadas = importedCount;
        batch.creadoPor = p.getMembership();
        batch.fechaCreacion = now;
        batchRepository.save(batch);

        return new CatalogImportConfirmResponse(
                request.loteId(),
                request.items().size(),
                importedCount,
                "Se importaron exitosamente " + importedCount + " insumos del catálogo"
        );
    }
}
