package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public class HorarioAtencionDto {

    private String id;
    private String clinicaId;
    private String odontologoId;

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(1)
    @Max(7)
    private Integer diaSemana; // 1: Lunes .. 7: Domingo

    @NotNull(message = "La hora de apertura es obligatoria")
    private LocalTime horaApertura;

    @NotNull(message = "La hora de cierre es obligatoria")
    private LocalTime horaCierre;

    private Boolean activo = true;
    private Integer duracionTurnoMinutos = 30;

    public HorarioAtencionDto() {
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

    public Integer getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(Integer diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraApertura() {
        return horaApertura;
    }

    public void setHoraApertura(LocalTime horaApertura) {
        this.horaApertura = horaApertura;
    }

    public LocalTime getHoraCierre() {
        return horaCierre;
    }

    public void setHoraCierre(LocalTime horaCierre) {
        this.horaCierre = horaCierre;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Integer getDuracionTurnoMinutos() {
        return duracionTurnoMinutos;
    }

    public void setDuracionTurnoMinutos(Integer duracionTurnoMinutos) {
        this.duracionTurnoMinutos = duracionTurnoMinutos;
    }
}
