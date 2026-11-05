package pe.edu.utp.coronyx.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HelpdeskTicketDto(
        String id,
        @JsonAlias({"codigoCatalogo", "catalogoId"})
        String catalogoId,
        String solicitud,
        String solicitante,
        String solicitanteRol,
        @JsonAlias({"solicitante_id", "solicitanteId"})
        Long solicitanteId,
        String responsable,
        String responsableRol,
        @JsonAlias({"responsable_id", "responsableId"})
        Long responsableId,
        Integer probabilidad,
        Integer impacto,
        String prioridad,
        String estado,
        String tiempoTranscurrido,
        String tipoItil,
        Integer slaMinutos,
        String evidencia,
        String fechaRegistro,
        String fechaResolucion,
        String descripcionDetallada,
        String archivoAdjuntoUrl,
        Long citaId,
        Long pacienteId,
        String rutaAccion,
        String accionLabel
) {
    public int probabilidadSafe() {
        return probabilidad != null ? probabilidad : 2;
    }

    public int impactoSafe() {
        return impacto != null ? impacto : 2;
    }

    public int slaMinutosSafe() {
        return slaMinutos != null ? slaMinutos : 30;
    }
}
