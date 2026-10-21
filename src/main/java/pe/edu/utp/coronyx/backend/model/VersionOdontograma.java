package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "version_odontograma")
public class VersionOdontograma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Patient paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_anterior_id")
    private VersionOdontograma versionAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmado_por")
    private ClinicUser firmadoPor;

    @NotNull
    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion = 1;

    @NotNull
    @Size(max = 16)
    @Column(name = "estado", nullable = false, length = 16)
    private String estado = "BORRADOR";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;

    @OneToMany(mappedBy = "versionOdontograma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HallazgoOdontograma> hallazgos = new ArrayList<>();

    public VersionOdontograma() {
    }

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
        if (estado == null || estado.isBlank()) {
            estado = "BORRADOR";
        }
        if (numeroVersion == null || numeroVersion < 1) {
            numeroVersion = 1;
        }
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

    public VersionOdontograma getVersionAnterior() {
        return versionAnterior;
    }

    public void setVersionAnterior(VersionOdontograma versionAnterior) {
        this.versionAnterior = versionAnterior;
    }

    public ClinicUser getFirmadoPor() {
        return firmadoPor;
    }

    public void setFirmadoPor(ClinicUser firmadoPor) {
        this.firmadoPor = firmadoPor;
    }

    public Integer getNumeroVersion() {
        return numeroVersion;
    }

    public void setNumeroVersion(Integer numeroVersion) {
        this.numeroVersion = numeroVersion;
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

    public List<HallazgoOdontograma> getHallazgos() {
        return hallazgos;
    }

    public void setHallazgos(List<HallazgoOdontograma> hallazgos) {
        this.hallazgos = hallazgos;
    }
}
