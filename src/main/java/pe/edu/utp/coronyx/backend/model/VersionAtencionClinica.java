package pe.edu.utp.coronyx.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "version_atencion_clinica", uniqueConstraints = {
        @UniqueConstraint(name = "uq_version_atencion_numero", columnNames = {"atencion_clinica_id", "numero_version"})
})
public class VersionAtencionClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Version @Column(name = "revision", nullable = false)
    private long revision;
    public long getRevision() { return revision; }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atencion_clinica_id", nullable = false)
    @JsonIgnore
    private AtencionClinica atencionClinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_anterior_id")
    private VersionAtencionClinica versionAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmado_por")
    private ClinicUser firmadoPor;

    @NotNull
    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion = 1;

    @Column(name = "motivo_consulta", columnDefinition = "TEXT")
    private String motivoConsulta;

    @Column(name = "examen_clinico", columnDefinition = "TEXT")
    private String examenClinico;

    @Column(name = "diagnostico", columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "procedimientos", columnDefinition = "TEXT")
    private String procedimientos;

    @Column(name = "plan_tratamiento", columnDefinition = "TEXT")
    private String planTratamiento;

    @Column(name = "recetas_prescripciones", columnDefinition = "TEXT")
    private String recetasPrescripciones;

    @Column(name = "indicaciones", columnDefinition = "TEXT")
    private String indicaciones;

    @Column(name = "evolucion", columnDefinition = "TEXT")
    private String evolucion;

    @Column(name = "proxima_cita_sugerida", length = 50)
    private String proximaCitaSugerida;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signos_vitales", columnDefinition = "jsonb")
    private String signosVitales;

    @Column(name = "texto_pendiente_revision", columnDefinition = "TEXT")
    private String textoPendienteRevision;

    @NotNull
    @Column(name = "esta_confirmado", nullable = false)
    private Boolean estaConfirmado = false;

    @NotNull
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;

    public VersionAtencionClinica() {
    }

    public VersionAtencionClinica(Clinic clinica, AtencionClinica atencionClinica, Integer numeroVersion) {
        this.clinica = clinica;
        this.atencionClinica = atencionClinica;
        this.numeroVersion = numeroVersion;
        this.estaConfirmado = false;
        this.fechaRegistro = OffsetDateTime.now();
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

    public AtencionClinica getAtencionClinica() {
        return atencionClinica;
    }

    public void setAtencionClinica(AtencionClinica atencionClinica) {
        this.atencionClinica = atencionClinica;
    }

    public VersionAtencionClinica getVersionAnterior() {
        return versionAnterior;
    }

    public void setVersionAnterior(VersionAtencionClinica versionAnterior) {
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

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public void setMotivoConsulta(String motivoConsulta) {
        this.motivoConsulta = motivoConsulta;
    }

    public String getExamenClinico() {
        return examenClinico;
    }

    public void setExamenClinico(String examenClinico) {
        this.examenClinico = examenClinico;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(String diagnostico) {
        this.diagnostico = diagnostico;
    }

    public String getProcedimientos() {
        return procedimientos;
    }

    public void setProcedimientos(String procedimientos) {
        this.procedimientos = procedimientos;
    }

    public String getPlanTratamiento() {
        return planTratamiento;
    }

    public void setPlanTratamiento(String planTratamiento) {
        this.planTratamiento = planTratamiento;
    }

    public String getRecetasPrescripciones() {
        return recetasPrescripciones;
    }

    public void setRecetasPrescripciones(String recetasPrescripciones) {
        this.recetasPrescripciones = recetasPrescripciones;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public void setIndicaciones(String indicaciones) {
        this.indicaciones = indicaciones;
    }

    public String getEvolucion() {
        return evolucion;
    }

    public void setEvolucion(String evolucion) {
        this.evolucion = evolucion;
    }

    public String getProximaCitaSugerida() {
        return proximaCitaSugerida;
    }

    public void setProximaCitaSugerida(String proximaCitaSugerida) {
        this.proximaCitaSugerida = proximaCitaSugerida;
    }

    public String getSignosVitales() {
        return signosVitales;
    }

    public void setSignosVitales(String signosVitales) {
        this.signosVitales = signosVitales;
    }

    public String getTextoPendienteRevision() {
        return textoPendienteRevision;
    }

    public void setTextoPendienteRevision(String textoPendienteRevision) {
        this.textoPendienteRevision = textoPendienteRevision;
    }

    public Boolean getEstaConfirmado() {
        return estaConfirmado;
    }

    public void setEstaConfirmado(Boolean estaConfirmado) {
        this.estaConfirmado = estaConfirmado;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(OffsetDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public OffsetDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public void setFechaConfirmacion(OffsetDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }
}
