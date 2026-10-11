package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "atencion_clinica")
public class AtencionClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Patient paciente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", unique = true)
    private Appointment cita;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "odontologo_id", nullable = false)
    private ClinicUser odontologo;

    @NotBlank
    @Size(max = 16)
    @Column(name = "estado", nullable = false, length = 16)
    private String estado = "BORRADOR";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;

    @OneToMany(mappedBy = "atencionClinica", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("numeroVersion ASC")
    private List<VersionAtencionClinica> versiones = new ArrayList<>();

    public AtencionClinica() {
    }

    public AtencionClinica(Clinic clinica, Patient paciente, Appointment cita, ClinicUser odontologo) {
        this.clinica = clinica;
        this.paciente = paciente;
        this.cita = cita;
        this.odontologo = odontologo;
        this.estado = "BORRADOR";
        this.fechaCreacion = OffsetDateTime.now();
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

    public Patient getPaciente() {
        return paciente;
    }

    public void setPaciente(Patient paciente) {
        this.paciente = paciente;
    }

    public Appointment getCita() {
        return cita;
    }

    public void setCita(Appointment cita) {
        this.cita = cita;
    }

    public ClinicUser getOdontologo() {
        return odontologo;
    }

    public void setOdontologo(ClinicUser odontologo) {
        this.odontologo = odontologo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public void setFechaConfirmacion(OffsetDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }

    public List<VersionAtencionClinica> getVersiones() {
        return versiones;
    }

    public void setVersiones(List<VersionAtencionClinica> versiones) {
        this.versiones = versiones;
    }

    public void addVersion(VersionAtencionClinica version) {
        versiones.add(version);
        version.setAtencionClinica(this);
    }
}
