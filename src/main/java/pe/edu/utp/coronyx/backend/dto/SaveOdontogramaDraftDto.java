package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SaveOdontogramaDraftDto {

    @NotNull(message = "La lista de hallazgos es obligatoria")
    @Valid
    private List<HallazgoDto> hallazgos = new ArrayList<>();

    public SaveOdontogramaDraftDto() {
    }

    public SaveOdontogramaDraftDto(List<HallazgoDto> hallazgos) {
        this.hallazgos = hallazgos;
    }

    public List<HallazgoDto> getHallazgos() {
        return hallazgos;
    }

    public void setHallazgos(List<HallazgoDto> hallazgos) {
        this.hallazgos = hallazgos;
    }
}
