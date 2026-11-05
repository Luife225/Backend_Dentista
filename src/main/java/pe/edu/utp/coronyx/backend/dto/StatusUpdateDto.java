package pe.edu.utp.coronyx.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class StatusUpdateDto {

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "El estado debe ser ACTIVO o INACTIVO")
    private String estado = "ACTIVO";

    public StatusUpdateDto() {
    }

    public StatusUpdateDto(String estado) {
        this.estado = estado;
    }

    @JsonProperty("activo")
    public void setActivo(Boolean activo) {
        if (activo != null) {
            this.estado = activo ? "ACTIVO" : "INACTIVO";
        }
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
