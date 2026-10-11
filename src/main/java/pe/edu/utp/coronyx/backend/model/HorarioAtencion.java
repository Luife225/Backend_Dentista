package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "horario_atencion")
public class HorarioAtencion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odontologo_id")
    private ClinicUser odontologo;

    @NotNull
    @Min(1)
    @Max(7)
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana; // 1: Lunes .. 7: Domingo

    @NotNull
    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @NotNull
    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @NotNull
    @Column(name = "duracion_turno_minutos", nullable = false)
    private Integer duracionTurnoMinutos = 30;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @NotNull
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    public HorarioAtencion() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (fechaCreacion == null) fechaCreacion = now;
        if (fechaActualizacion == null) fechaActualizacion = now;
        if (activo == null) activo = true;
        if (duracionTurnoMinutos == null) duracionTurnoMinutos = 30;
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Clinic getClinica() {
        return clinica;
    }

    public void setClinica(Clinic clinica) {
        this.clinica = clinica;
    }

    public ClinicUser getOdontologo() {
        return odontologo;
    }

    public void setOdontologo(ClinicUser odontologo) {
        this.odontologo = odontologo;
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

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
