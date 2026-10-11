package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AppointmentAsistenciaDto {

    @NotBlank(message = "El estado de asistencia es obligatorio")
    @Pattern(regexp = "^(ASISTIO|NO_SHOW)$", message = "El estado de asistencia debe ser ASISTIO o NO_SHOW")
    @com.fasterxml.jackson.annotation.JsonProperty("estadoAsistencia")
    @com.fasterxml.jackson.annotation.JsonAlias({"asistencia", "asistenciaEstado"})
    private String estadoAsistencia;

    public AppointmentAsistenciaDto() {
    }

    public AppointmentAsistenciaDto(String estadoAsistencia) {
        this.estadoAsistencia = estadoAsistencia;
    }

    public String getEstadoAsistencia() {
        return estadoAsistencia;
    }

    public void setEstadoAsistencia(String estadoAsistencia) {
        this.estadoAsistencia = estadoAsistencia;
    }

    public String getAsistencia() {
        return estadoAsistencia;
    }

    public void setAsistencia(String asistencia) {
        this.estadoAsistencia = asistencia;
    }
}
