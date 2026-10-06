package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AppointmentAsistenciaDto {

    @NotBlank(message = "El estado de asistencia es obligatorio")
    @Pattern(regexp = "^(ASISTIO|NO_SHOW)$", message = "El estado de asistencia debe ser ASISTIO o NO_SHOW")
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
}
