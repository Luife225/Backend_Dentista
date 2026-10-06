package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AppointmentEstadoDto {

    @NotBlank(message = "El nuevo estado es obligatorio")
    @Pattern(regexp = "^(PROGRAMADA|CONFIRMADA|CANCELADA|EN_ATENCION|FINALIZADA)$",
            message = "Estado inválido. Valores permitidos: PROGRAMADA, CONFIRMADA, CANCELADA, EN_ATENCION, FINALIZADA")
    private String estado;

    private String motivoCancelacion;

    public AppointmentEstadoDto() {
    }

    public AppointmentEstadoDto(String estado) {
        this.estado = estado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }
}
