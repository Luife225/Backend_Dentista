package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateVisibilityRequestDto {

    @NotNull(message = "El campo esVisiblePaciente es obligatorio.")
    private Boolean esVisiblePaciente;

    public UpdateVisibilityRequestDto() {
    }

    public UpdateVisibilityRequestDto(Boolean esVisiblePaciente) {
        this.esVisiblePaciente = esVisiblePaciente;
    }

    public Boolean getEsVisiblePaciente() {
        return esVisiblePaciente;
    }

    public void setEsVisiblePaciente(Boolean esVisiblePaciente) {
        this.esVisiblePaciente = esVisiblePaciente;
    }
}
