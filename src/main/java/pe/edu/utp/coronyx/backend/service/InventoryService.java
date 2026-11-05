package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class InventoryService {
    private final InventoryItemRepository items;
    private final InventoryMovementRepository movements;

    public InventoryService(InventoryItemRepository items, InventoryMovementRepository movements) {
        this.items = items;
        this.movements = movements;
    }

    private UserPrincipal principal() {
        UserPrincipal p = SecurityUtils.getCurrentPrincipal();
        if (p.getClinicaId() == null || p.getMembership() == null || !Set.of("ADMIN_CLINICA", "RECEPCIONISTA").contains(p.getRol())) {
            throw new AccessDeniedException("Inventario está reservado al administrador y recepción de la clínica");
        }
        return p;
    }

    private InventoryItem item(Long id, UserPrincipal p) {
        return items.lockById(id, p.getClinicaId()).orElseThrow(() -> new NoSuchElementException("Insumo no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> list() {
        UserPrincipal p = principal();
        return items.findByClinicaIdOrderByNombreAsc(p.getClinicaId()).stream().map(this::response).toList();
    }

    public ItemResponse create(ItemRequest dto) {
        UserPrincipal p = principal();
        InventoryItem i = new InventoryItem();
        i.clinica = p.getClinic();
        i.fechaCreacion = OffsetDateTime.now();
        i.stock = 0;
        i.stockCerrado = 0;
        i.stockEnUso = 0;
        copy(dto, i);
        return response(items.saveAndFlush(i));
    }

    public ItemResponse update(Long id, ItemRequest dto) {
        UserPrincipal p = principal();
        InventoryItem i = item(id, p);
        if (movements.existsByInsumoId(id)) {
            if (!i.unidad.equals(dto.unidad().trim())) {
                throw new IllegalStateException("El insumo ya tiene movimientos. Cree otro insumo para trabajar con una unidad diferente.");
            }
            String targetPres = dto.presentacion() != null ? dto.presentacion().trim().toUpperCase() : "UNIDAD";
            if (!i.presentacion.equals(targetPres)) {
                throw new IllegalStateException("El insumo ya tiene movimientos. Cree otro insumo para trabajar con una presentación diferente.");
            }
        }
        copy(dto, i);
        return response(items.save(i));
    }

    private void copy(ItemRequest dto, InventoryItem i) {
        if (dto.stockMinimo() < 0 || dto.costoUnitario() == null || dto.costoUnitario().signum() < 0) {
            throw new IllegalArgumentException("Stock mínimo y costo no pueden ser negativos");
        }
        i.nombre = dto.nombre().trim();
        i.categoria = dto.categoria().trim();
        i.unidad = dto.unidad().trim();
        i.codigo = dto.codigo() != null && !dto.codigo().isBlank() ? dto.codigo().trim() : null;

        String pres = dto.presentacion() != null && !dto.presentacion().isBlank() ? dto.presentacion().trim().toUpperCase() : "UNIDAD";
        if (!Set.of("UNIDAD", "CAJA", "ENVASE").contains(pres)) {
            throw new IllegalArgumentException("Presentación inválida: debe ser UNIDAD, CAJA o ENVASE");
        }
        i.presentacion = pres;

        String clas = dto.clasificacion() != null && !dto.clasificacion().isBlank() ? dto.clasificacion().trim().toUpperCase() : "CONSUMIBLE";
        if (!Set.of("CONSUMIBLE", "REUTILIZABLE").contains(clas)) {
            throw new IllegalArgumentException("Clasificación inválida: debe ser CONSUMIBLE o REUTILIZABLE");
        }
        i.clasificacion = clas;

        i.contenidoDescriptivo = dto.contenidoDescriptivo() != null && !dto.contenidoDescriptivo().isBlank() ? dto.contenidoDescriptivo().trim() : null;
        i.stockMinimo = dto.stockMinimo();
        i.costoUnitario = dto.costoUnitario();
        i.proveedor = dto.proveedor() != null ? dto.proveedor().trim() : null;
        i.activo = dto.activo();
    }

    public ItemResponse move(Long id, MovementRequest dto) {
        UserPrincipal p = principal();
        InventoryItem i = item(id, p);

        // Protección contra duplicados / reintentos con la misma solicitud_id
        InventoryMovement previous = movements.findByClinicaIdAndSolicitudId(p.getClinicaId(), dto.solicitudId()).orElse(null);
        if (previous != null) {
            if (!previous.insumo.getId().equals(id) || !previous.tipo.equals(dto.tipo()) || previous.cantidad != dto.cantidad()
                    || !previous.motivo.equals(dto.motivo().trim())) {
                throw new IllegalStateException("La solicitud ya se utilizó para otro movimiento");
            }
            return response(i);
        }

        if (!i.activo) {
            throw new IllegalStateException("Active el insumo antes de registrar movimientos");
        }
        if (dto.cantidad() < 0 || (!"AJUSTE".equals(dto.tipo()) && dto.cantidad() == 0)) {
            throw new IllegalArgumentException("Ingrese una cantidad positiva");
        }

        int stockCerradoAnterior = i.stockCerrado;
        int stockEnUsoAnterior = i.stockEnUso;
        if (stockCerradoAnterior == 0 && stockEnUsoAnterior == 0 && i.stock > 0) {
            stockCerradoAnterior = i.stock;
        }
        int stockAnterior = i.stock > 0 ? i.stock : (stockCerradoAnterior + stockEnUsoAnterior);

        int stockCerradoResultante;
        int stockEnUsoResultante;
        String estadoOrigen = dto.estadoOrigen();
        String estadoDestino = null;

        switch (dto.tipo()) {
            case "ENTRADA" -> {
                estadoDestino = "CERRADO";
                stockCerradoResultante = Math.addExact(stockCerradoAnterior, dto.cantidad());
                stockEnUsoResultante = stockEnUsoAnterior;
            }
            case "APERTURA" -> {
                // Abrir o poner en uso: traslada de cerrado a en uso sin modificar el total
                estadoOrigen = "CERRADO";
                estadoDestino = "EN_USO";
                if (stockCerradoAnterior < dto.cantidad()) {
                    throw new IllegalStateException("Existencias cerradas/disponibles insuficientes para abrir");
                }
                stockCerradoResultante = stockCerradoAnterior - dto.cantidad();
                stockEnUsoResultante = Math.addExact(stockEnUsoAnterior, dto.cantidad());
            }
            case "AGOTADO" -> {
                // Marcar agotado: retira de en uso una cantidad consumida. Solo CONSUMIBLE.
                if ("REUTILIZABLE".equalsIgnoreCase(i.clasificacion)) {
                    throw new IllegalStateException("Un insumo reutilizable no se agota por haberlo usado; registre devolución o baja con motivo");
                }
                estadoOrigen = "EN_USO";
                if (stockEnUsoAnterior < dto.cantidad()) {
                    throw new IllegalStateException("No hay suficientes existencias en uso para marcar como agotadas");
                }
                stockCerradoResultante = stockCerradoAnterior;
                stockEnUsoResultante = stockEnUsoAnterior - dto.cantidad();
            }
            case "SALIDA" -> {
                // Salida o baja manual con motivo. Aplica sobre estadoOrigen (CERRADO por defecto o EN_USO)
                String origen = estadoOrigen != null && estadoOrigen.equalsIgnoreCase("EN_USO") ? "EN_USO" : "CERRADO";
                estadoOrigen = origen;
                if ("EN_USO".equals(origen)) {
                    if (stockEnUsoAnterior < dto.cantidad()) {
                        throw new IllegalStateException("Existencias insuficientes en uso para registrar la salida");
                    }
                    stockEnUsoResultante = stockEnUsoAnterior - dto.cantidad();
                    stockCerradoResultante = stockCerradoAnterior;
                } else {
                    if (stockCerradoAnterior < dto.cantidad()) {
                        throw new IllegalStateException("Existencias cerradas/disponibles insuficientes para registrar la salida");
                    }
                    stockCerradoResultante = stockCerradoAnterior - dto.cantidad();
                    stockEnUsoResultante = stockEnUsoAnterior;
                }
            }
            case "DEVOLUCION" -> {
                // Devolver un reutilizable a disponible: traslada de en uso a cerrado sin modificar total
                if (!"REUTILIZABLE".equalsIgnoreCase(i.clasificacion)) {
                    throw new IllegalStateException("Solo los insumos reutilizables admiten devolución a disponible");
                }
                estadoOrigen = "EN_USO";
                estadoDestino = "CERRADO";
                if (stockEnUsoAnterior < dto.cantidad()) {
                    throw new IllegalStateException("No hay suficientes existencias en uso para devolver a disponible");
                }
                stockCerradoResultante = Math.addExact(stockCerradoAnterior, dto.cantidad());
                stockEnUsoResultante = stockEnUsoAnterior - dto.cantidad();
            }
            case "AJUSTE" -> {
                if (!"ADMIN_CLINICA".equals(p.getRol())) {
                    throw new AccessDeniedException("Solo el administrador puede ajustar existencias");
                }
                if (dto.stockCerradoAjuste() != null && dto.stockCerradoAjuste() < 0) {
                    throw new IllegalArgumentException("Las existencias cerradas no pueden ser negativas");
                }
                if (dto.stockEnUsoAjuste() != null && dto.stockEnUsoAjuste() < 0) {
                    throw new IllegalArgumentException("Las existencias en uso no pueden ser negativas");
                }

                if (dto.stockCerradoAjuste() != null) {
                    stockCerradoResultante = dto.stockCerradoAjuste();
                    stockEnUsoResultante = dto.stockEnUsoAjuste() != null ? dto.stockEnUsoAjuste() : stockEnUsoAnterior;
                } else if (dto.stockEnUsoAjuste() != null) {
                    stockCerradoResultante = stockCerradoAnterior;
                    stockEnUsoResultante = dto.stockEnUsoAjuste();
                } else {
                    // Si se envía solo cantidad genérica en ajuste, se asigna como existencias cerradas y 0 en uso
                    stockCerradoResultante = dto.cantidad();
                    stockEnUsoResultante = 0;
                }
            }
            default -> throw new IllegalArgumentException("Tipo de movimiento inválido: " + dto.tipo());
        }

        long stockResultanteLong = (long) stockCerradoResultante + stockEnUsoResultante;
        if (stockResultanteLong > Integer.MAX_VALUE || stockCerradoResultante < 0 || stockEnUsoResultante < 0) {
            throw new IllegalArgumentException("Las existencias calculadas exceden los límites permitidos");
        }
        int stockResultante = (int) stockResultanteLong;

        InventoryMovement m = new InventoryMovement();
        m.clinica = p.getClinic();
        m.insumo = i;
        m.creadoPor = p.getMembership();
        m.tipo = dto.tipo();
        m.cantidad = dto.cantidad();
        m.estadoOrigen = estadoOrigen;
        m.estadoDestino = estadoDestino;
        m.stockCerradoAnterior = stockCerradoAnterior;
        m.stockCerradoResultante = stockCerradoResultante;
        m.stockEnUsoAnterior = stockEnUsoAnterior;
        m.stockEnUsoResultante = stockEnUsoResultante;
        m.stockAnterior = stockAnterior;
        m.stockResultante = stockResultante;
        m.motivo = dto.motivo().trim();
        m.solicitudId = dto.solicitudId();
        m.fechaCreacion = OffsetDateTime.now();

        i.stockCerrado = stockCerradoResultante;
        i.stockEnUso = stockEnUsoResultante;
        i.stock = stockResultante;

        items.save(i);
        movements.saveAndFlush(m);

        return response(i);
    }

    public List<MovementResponse> history(Long id) {
        UserPrincipal p = principal();
        item(id, p);
        return movements.findByInsumoIdOrderByFechaCreacionDesc(id).stream().map(m -> new MovementResponse(
                m.id.toString(),
                id.toString(),
                m.tipo,
                m.cantidad,
                m.estadoOrigen,
                m.estadoDestino,
                m.stockCerradoAnterior,
                m.stockCerradoResultante,
                m.stockEnUsoAnterior,
                m.stockEnUsoResultante,
                m.stockAnterior,
                m.stockResultante,
                m.motivo,
                m.creadoPor.getId().toString(),
                m.fechaCreacion
        )).toList();
    }

    private ItemResponse response(InventoryItem i) {
        boolean stockBajo = i.stockCerrado <= i.stockMinimo;
        return new ItemResponse(
                i.id.toString(),
                i.codigo,
                i.nombre,
                i.categoria,
                i.unidad,
                i.presentacion,
                i.clasificacion,
                i.contenidoDescriptivo,
                i.stock,
                i.stockCerrado,
                i.stockEnUso,
                i.stockMinimo,
                i.costoUnitario,
                i.proveedor,
                i.activo,
                stockBajo
        );
    }
}
