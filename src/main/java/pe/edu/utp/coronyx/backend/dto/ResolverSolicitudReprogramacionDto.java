package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResolverSolicitudReprogramacionDto {

    @NotBlank(message = "El estado de resolución es obligatorio")
    @Pattern(regexp = "^(APROBADA|RECHAZADA)$", message = "El estado debe ser APROBADA o RECHAZADA")
    @com.fasterxml.jackson.annotation.JsonProperty("estado")
    @com.fasterxml.jackson.annotation.JsonAlias({"accion", "resolucion", "estadoResolucion"})
    private String estado;

    @Size(max = 500, message = "El motivo de la resolución no puede exceder 500 caracteres")
    @com.fasterxml.jackson.annotation.JsonProperty("motivoResolucion")
    @com.fasterxml.jackson.annotation.JsonAlias({"observaciones", "motivo", "comentario"})
    private String motivoResolucion;

    public ResolverSolicitudReprogramacionDto() {
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoResolucion() {
        return motivoResolucion;
    }

    public void setMotivoResolucion(String motivoResolucion) {
        this.motivoResolucion = motivoResolucion;
    }
}
