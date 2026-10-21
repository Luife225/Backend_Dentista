package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.service.InventoryCatalogService;
import pe.edu.utp.coronyx.backend.service.InventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventario")
public class InventoryController {
    private final InventoryService service;
    private final InventoryCatalogService catalogService;

    public InventoryController(InventoryService service,
                               InventoryCatalogService catalogService) {
        this.service = service;
        this.catalogService = catalogService;
    }

    @GetMapping("/insumos")
    public List<ItemResponse> list() {
        return service.list();
    }

    @PostMapping("/insumos")
    public ItemResponse create(@Valid @RequestBody ItemRequest dto) {
        return service.create(dto);
    }

    @PutMapping("/insumos/{id}")
    public ItemResponse update(@PathVariable Long id, @Valid @RequestBody ItemRequest dto) {
        return service.update(id, dto);
    }

    @PostMapping("/insumos/{id}/movimientos")
    public ItemResponse move(@PathVariable Long id, @Valid @RequestBody MovementRequest dto) {
        return service.move(id, dto);
    }

    @GetMapping("/insumos/{id}/movimientos")
    public List<MovementResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping({"/catalogo/previsualizar", "/catalogo/vista-previa"})
    public CatalogPreviewResponse previewCatalog(@RequestBody CatalogPreviewRequest request) {
        List<RawCatalogRow> rows = request != null ? request.effectiveRows() : List.of();
        return catalogService.preview(rows);
    }

    @PostMapping({"/catalogo/confirmar", "/catalogo/confirm"})
    public CatalogImportConfirmResponse confirmCatalog(@Valid @RequestBody CatalogImportConfirmRequest request) {
        return catalogService.confirm(request);
    }
}



