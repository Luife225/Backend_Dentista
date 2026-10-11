package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class AusenciaDto {

    private String id;
    private String clinicaId;

    @NotNull(message = "El ID del odontólogo es obligatorio")
    private String odontologoId;
    private String odontologoNombre;

    @NotNull(message = "La fecha/hora de inicio es obligatoria")
    private OffsetDateTime inicioEn;

    @NotNull(message = "La fecha/hora de fin es obligatoria")
    private OffsetDateTime finEn;

    @Size(max = 255)
    private String motivo;

    private String tipo = "VACACIONES"; // VACACIONES, PERMISO, CAPACITACION, LICENCIA_MEDICA, OTRO

    public AusenciaDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(String clinicaId) {
        this.clinicaId = clinicaId;
    }

    public String getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(String odontologoId) {
        this.odontologoId = odontologoId;
    }

    public String getOdontologoNombre() {
        return odontologoNombre;
    }

    public void setOdontologoNombre(String odontologoNombre) {
        this.odontologoNombre = odontologoNombre;
    }

    public OffsetDateTime getInicioEn() {
        return inicioEn;
    }

    public void setInicioEn(OffsetDateTime inicioEn) {
        this.inicioEn = inicioEn;
    }

    public OffsetDateTime getFinEn() {
        return finEn;
    }

    public void setFinEn(OffsetDateTime finEn) {
        this.finEn = finEn;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
