package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class CreateSolicitudReprogramacionDto {

    @NotNull(message = "La nueva fecha y hora de inicio es obligatoria")
    @com.fasterxml.jackson.annotation.JsonProperty("nuevaFechaHoraInicio")
    @com.fasterxml.jackson.annotation.JsonAlias({"nuevaFechaInicioPropuesta", "inicioPropuesto", "nuevaFechaInicio"})
    private OffsetDateTime nuevaFechaHoraInicio;

    @NotNull(message = "La nueva fecha y hora de fin es obligatoria")
    @com.fasterxml.jackson.annotation.JsonProperty("nuevaFechaHoraFin")
    @com.fasterxml.jackson.annotation.JsonAlias({"nuevaFechaFinPropuesta", "finPropuesto", "nuevaFechaFin"})
    private OffsetDateTime nuevaFechaHoraFin;

    @NotBlank(message = "El motivo de la reprogramación es obligatorio")
    @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
    private String motivo;

    public CreateSolicitudReprogramacionDto() {
    }

    public OffsetDateTime getNuevaFechaHoraInicio() {
        return nuevaFechaHoraInicio;
    }

    public void setNuevaFechaHoraInicio(OffsetDateTime nuevaFechaHoraInicio) {
        this.nuevaFechaHoraInicio = nuevaFechaHoraInicio;
    }

    public OffsetDateTime getNuevaFechaHoraFin() {
        return nuevaFechaHoraFin;
    }

    public void setNuevaFechaHoraFin(OffsetDateTime nuevaFechaHoraFin) {
        this.nuevaFechaHoraFin = nuevaFechaHoraFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
