package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cita")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Patient paciente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "odontologo_id", nullable = false)
    private ClinicUser odontologo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private ClinicUser creadoPor;

    @NotNull
    @Column(name = "inicio_en", nullable = false)
    private OffsetDateTime inicioEn;

    @NotNull
    @Column(name = "fin_en", nullable = false)
    private OffsetDateTime finEn;

    @NotBlank
    @Size(max = 12)
    @Column(name = "modalidad", nullable = false, length = 12)
    private String modalidad = "PRESENCIAL";

    @NotBlank
    @Size(max = 20)
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "PROGRAMADA";

    @Size(max = 16)
    @Column(name = "estado_asistencia", length = 16)
    private String estadoAsistencia;

    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    @Size(max = 500)
    @Column(name = "enlace_teleconsulta", length = 500)
    private String enlaceTeleconsulta;

    @Size(max = 100)
    @Column(name = "consultorio", length = 100)
    private String consultorio;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @NotNull
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    public Appointment() {
    }

    public Appointment(Clinic clinica, Patient paciente, ClinicUser odontologo, ClinicUser creadoPor,
                       OffsetDateTime inicioEn, OffsetDateTime finEn, String modalidad) {
        this.clinica = clinica;
        this.paciente = paciente;
        this.odontologo = odontologo;
        this.creadoPor = creadoPor;
        this.inicioEn = inicioEn;
        this.finEn = finEn;
        this.modalidad = modalidad != null ? modalidad : "PRESENCIAL";
        this.estado = "PROGRAMADA";
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = now;
        }
        if (fechaActualizacion == null) {
            fechaActualizacion = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Clinic getClinica() {
        return clinica;
    }

    public void setClinica(Clinic clinica) {
        this.clinica = clinica;
    }

    public Patient getPaciente() {
        return paciente;
    }

    public void setPaciente(Patient paciente) {
        this.paciente = paciente;
    }

    public ClinicUser getOdontologo() {
        return odontologo;
    }

    public void setOdontologo(ClinicUser odontologo) {
        this.odontologo = odontologo;
    }

    public ClinicUser getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(ClinicUser creadoPor) {
        this.creadoPor = creadoPor;
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

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getEstadoAsistencia() {
        return estadoAsistencia;
    }

    public void setEstadoAsistencia(String estadoAsistencia) {
        this.estadoAsistencia = estadoAsistencia;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getEnlaceTeleconsulta() {
        return enlaceTeleconsulta;
    }

    public void setEnlaceTeleconsulta(String enlaceTeleconsulta) {
        this.enlaceTeleconsulta = enlaceTeleconsulta;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(String consultorio) {
        this.consultorio = consultorio;
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
