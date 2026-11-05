package pe.edu.utp.coronyx.backend.dto;

public record HelpdeskCatalogDto(
        String id,
        String solicitanteRol,
        String titulo,
        String descripcion,
        String tipoItil,
        int probabilidad,
        int impacto,
        String prioridad,
        String responsableRol,
        int slaMinutos,
        String rutaAccion,
        String accionLabel
) {}
