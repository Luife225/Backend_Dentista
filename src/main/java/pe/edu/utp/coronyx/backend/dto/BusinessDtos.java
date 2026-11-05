package pe.edu.utp.coronyx.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class BusinessDtos {
    private BusinessDtos() {}
    public record ChargeRequest(@NotNull Long pacienteId, Long citaId,
            @NotBlank @Size(max=200) String concepto,
            @NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal importe,
            @NotBlank @Size(max=80) String solicitudId) {}
    public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal importe,
            @NotBlank @Pattern(regexp="EFECTIVO|TARJETA|TRANSFERENCIA|YAPE|PLIN") String medio,
            @Size(max=120) String referencia, @NotBlank @Size(max=80) String solicitudId) {}
    public record VoidRequest(@NotBlank @Size(max=500) String motivo) {}
    public record PaymentResponse(String id, String cargoId, BigDecimal importe, String medio, String referencia,
            boolean anulado, String motivoAnulacion, String creadoPorId, OffsetDateTime fechaCreacion) {}
    public record ChargeResponse(String id, String pacienteId, String pacienteNombre, String citaId, String concepto,
            BigDecimal importe, BigDecimal pagado, BigDecimal saldo, String estado, String moneda,
            String motivoAnulacion, OffsetDateTime fechaCreacion, List<PaymentResponse> pagos) {}
    public record ItemRequest(String codigo, @NotBlank @Size(max=160) String nombre, @NotBlank @Size(max=80) String categoria,
            @NotBlank @Size(max=40) String unidad, String presentacion, String clasificacion,
            @Size(max=200) String contenidoDescriptivo, @Min(0) int stockMinimo,
            @NotNull @DecimalMin("0.00") @Digits(integer=10, fraction=2) BigDecimal costoUnitario,
            @Size(max=160) String proveedor, boolean activo) {
        public ItemRequest(String nombre, String categoria, String unidad, int stockMinimo,
                BigDecimal costoUnitario, String proveedor, boolean activo) {
            this(null, nombre, categoria, unidad, "UNIDAD", "CONSUMIBLE", null, stockMinimo, costoUnitario, proveedor, activo);
        }
    }
    public record MovementRequest(@NotBlank @Pattern(regexp="ENTRADA|APERTURA|AGOTADO|SALIDA|DEVOLUCION|AJUSTE") String tipo,
            @Min(0) @Max(1000000000) int cantidad, String estadoOrigen,
            Integer stockCerradoAjuste, Integer stockEnUsoAjuste,
            @NotBlank @Size(max=500) String motivo, @NotBlank @Size(max=80) String solicitudId) {
        public MovementRequest(String tipo, int cantidad, String motivo, String solicitudId) {
            this(tipo, cantidad, null, null, null, motivo, solicitudId);
        }
    }
    public record ItemResponse(String id, String codigo, String nombre, String categoria, String unidad,
            String presentacion, String clasificacion, String contenidoDescriptivo,
            int stock, int stockCerrado, int stockEnUso, int stockMinimo,
            BigDecimal costoUnitario, String proveedor, boolean activo, boolean stockBajo) {}
    public record MovementResponse(String id, String insumoId, String tipo, int cantidad,
            String estadoOrigen, String estadoDestino,
            int stockCerradoAnterior, int stockCerradoResultante,
            int stockEnUsoAnterior, int stockEnUsoResultante,
            int stockAnterior, int stockResultante,
            String motivo, String creadoPorId, OffsetDateTime fechaCreacion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCatalogRow(
            String codigo,
            String um,
            String unidadMedida,
            String descripcion,
            BigDecimal precioUnitario,
            BigDecimal precioReferencial,
            String familia
    ) {
        public RawCatalogRow(String codigo, String um, String descripcion, BigDecimal precioUnitario) {
            this(codigo, um, um, descripcion, precioUnitario, precioUnitario, null);
        }
        public String effectiveUm() {
            if (um != null && !um.isBlank()) return um;
            if (unidadMedida != null && !unidadMedida.isBlank()) return unidadMedida;
            return "";
        }
        public BigDecimal effectivePrice() {
            if (precioUnitario != null) return precioUnitario;
            if (precioReferencial != null) return precioReferencial;
            return BigDecimal.ZERO;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogPreviewRequest(
            List<RawCatalogRow> filas,
            List<RawCatalogRow> items,
            String origen
    ) {
        public List<RawCatalogRow> effectiveRows() {
            if (filas != null && !filas.isEmpty()) return filas;
            if (items != null && !items.isEmpty()) return items;
            return List.of();
        }
    }

    public record CatalogPreviewItem(String codigo, String nombre, String categoria, String presentacion,
            String clasificacion, String contenidoDescriptivo, BigDecimal costoUnitario,
            String estadoRevision, String motivoPendiente) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogRowAnalysisDto(
            int fila,
            String codigo,
            String descripcion,
            String unidadMedida,
            String tipoDetectado,
            String presentacionSugerida,
            String unidadSugerida,
            String clasificacionSugerida,
            String contenidoDescriptivoSugerido,
            String categoriaSugerida,
            BigDecimal precioReferencial,
            boolean importable,
            boolean existenteEnInventario,
            String observacion,
            String motivoExclusion,
            boolean duplicadoCodigo
    ) {}

    public record CatalogPreviewResponse(
            String loteId,
            int totalFilas,
            int filasCandidatas,
            int filasPendientes,
            int filasImportables,
            int filasServiciosOmitidas,
            int filasExistentes,
            List<CatalogPreviewItem> items,
            List<CatalogRowAnalysisDto> filas,
            List<String> advertencias
    ) {
        public CatalogPreviewResponse(int totalFilas, int filasCandidatas, int filasPendientes, List<CatalogPreviewItem> items) {
            this("lote-preview-" + System.currentTimeMillis(), totalFilas, filasCandidatas, filasPendientes,
                 filasCandidatas, filasPendientes, 0, items, List.of(), List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogImportItemRequest(
            String codigo,
            @NotBlank String nombre,
            @NotBlank String categoria,
            @NotBlank String presentacion,
            @NotBlank String clasificacion,
            String contenidoDescriptivo,
            BigDecimal costoUnitario,
            Integer stockCerradoInicial,
            Integer stockEnUsoInicial,
            String motivoConteo,
            String unidad,
            Integer stockMinimo,
            String proveedor
    ) {
        public CatalogImportItemRequest(String codigo, String nombre, String categoria,
                String presentacion, String clasificacion, String contenidoDescriptivo,
                BigDecimal costoUnitario, Integer stockCerradoInicial, Integer stockEnUsoInicial, String motivoConteo) {
            this(codigo, nombre, categoria, presentacion, clasificacion, contenidoDescriptivo, costoUnitario, stockCerradoInicial, stockEnUsoInicial, motivoConteo, null, 0, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogImportConfirmRequest(
            @NotBlank String loteId,
            String nombreArchivo,
            String hoja,
            @NotEmpty List<CatalogImportItemRequest> items,
            String solicitudId
    ) {
        public CatalogImportConfirmRequest(String loteId, String nombreArchivo, String hoja, List<CatalogImportItemRequest> items) {
            this(loteId, nombreArchivo, hoja, items, null);
        }
    }

    public record CatalogImportConfirmResponse(
            String loteId,
            int totalProcesados,
            int itemsImportados,
            int itemsRegistrados,
            int itemsOmitidos,
            String mensaje
    ) {
        public CatalogImportConfirmResponse(String loteId, int totalProcesados, int itemsImportados, String mensaje) {
            this(loteId, totalProcesados, itemsImportados, itemsImportados, totalProcesados - itemsImportados, mensaje);
        }
    }
}

