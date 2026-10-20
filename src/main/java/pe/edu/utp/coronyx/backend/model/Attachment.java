package pe.edu.utp.coronyx.backend.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Entidad JPA que representa la metadata persistida de un archivo clínico o administrativo adjunto (F06-A).
 * Mapea la tabla archivo_adjunto de PostgreSQL con clave primaria BIGINT.
 */
@Entity
@Table(name = "archivo_adjunto")
public class Attachment {

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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subido_por_usuario_id", nullable = false)
    private User subidoPorUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Appointment cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ia_revisado_por")
    private ClinicUser iaRevisadoPor;

    @NotBlank
    @Size(max = 24)
    @Column(name = "tipo_archivo", nullable = false, length = 24)
    private String tipoArchivo;

    @Column(name = "ambito", nullable = false, length = 20)
    private String ambito = "CLINICO";
    public String getAmbito() { return ambito; }
    public void setAmbito(String ambito) { this.ambito = ambito; }

    @NotBlank
    @Size(max = 500)
    @Column(name = "clave_almacenamiento", nullable = false, unique = true, length = 500)
    private String claveAlmacenamiento;

    @NotBlank
    @Size(max = 255)
    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @NotBlank
    @Size(max = 100)
    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @NotNull
    @Positive
    @Column(name = "peso_bytes", nullable = false)
    private Long pesoBytes;

    @Size(max = 80)
    @Column(name = "region_anatomica", length = 80)
    private String regionAnatomica;

    @Column(name = "es_visible_paciente", nullable = false)
    private boolean esVisiblePaciente = false;

    @Size(max = 100)
    @Column(name = "ia_nombre_modelo", length = 100)
    private String iaNombreModelo;

    @Size(max = 80)
    @Column(name = "ia_version_modelo", length = 80)
    private String iaVersionModelo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ia_hallazgos_json", columnDefinition = "jsonb")
    private String iaHallazgosJson;

    @Size(max = 16)
    @Column(name = "ia_estado_revision", length = 16)
    private String iaEstadoRevision = "PENDIENTE";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Attachment() {
    }

    public Attachment(Clinic clinica, Patient paciente, User subidoPorUsuario,
                      String tipoArchivo, String claveAlmacenamiento, String nombreOriginal,
                      String tipoMime, Long pesoBytes, String regionAnatomica, boolean esVisiblePaciente) {
        this.clinica = clinica;
        this.paciente = paciente;
        this.subidoPorUsuario = subidoPorUsuario;
        this.tipoArchivo = tipoArchivo;
        this.claveAlmacenamiento = claveAlmacenamiento;
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.pesoBytes = pesoBytes;
        this.regionAnatomica = regionAnatomica;
        this.esVisiblePaciente = esVisiblePaciente;
        this.fechaCreacion = OffsetDateTime.now();
        this.iaEstadoRevision = "PENDIENTE";
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

    public User getSubidoPorUsuario() {
        return subidoPorUsuario;
    }

    public void setSubidoPorUsuario(User subidoPorUsuario) {
        this.subidoPorUsuario = subidoPorUsuario;
    }

    public Appointment getCita() {
        return cita;
    }

    public void setCita(Appointment cita) {
        this.cita = cita;
    }

    public ClinicUser getIaRevisadoPor() {
        return iaRevisadoPor;
    }

    public void setIaRevisadoPor(ClinicUser iaRevisadoPor) {
        this.iaRevisadoPor = iaRevisadoPor;
    }

    public String getTipoArchivo() {
        return tipoArchivo;
    }

    public void setTipoArchivo(String tipoArchivo) {
        this.tipoArchivo = tipoArchivo;
    }

    public String getClaveAlmacenamiento() {
        return claveAlmacenamiento;
    }

    public void setClaveAlmacenamiento(String claveAlmacenamiento) {
        this.claveAlmacenamiento = claveAlmacenamiento;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public void setNombreOriginal(String nombreOriginal) {
        this.nombreOriginal = nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public void setTipoMime(String tipoMime) {
        this.tipoMime = tipoMime;
    }

    public Long getPesoBytes() {
        return pesoBytes;
    }

    public void setPesoBytes(Long pesoBytes) {
        this.pesoBytes = pesoBytes;
    }

    public String getRegionAnatomica() {
        return regionAnatomica;
    }

    public void setRegionAnatomica(String regionAnatomica) {
        this.regionAnatomica = regionAnatomica;
    }

    public boolean isEsVisiblePaciente() {
        return esVisiblePaciente;
    }

    public void setEsVisiblePaciente(boolean esVisiblePaciente) {
        this.esVisiblePaciente = esVisiblePaciente;
    }

    public String getIaNombreModelo() {
        return iaNombreModelo;
    }

    public void setIaNombreModelo(String iaNombreModelo) {
        this.iaNombreModelo = iaNombreModelo;
    }

    public String getIaVersionModelo() {
        return iaVersionModelo;
    }

    public void setIaVersionModelo(String iaVersionModelo) {
        this.iaVersionModelo = iaVersionModelo;
    }

    public String getIaHallazgosJson() {
        return iaHallazgosJson;
    }

    public void setIaHallazgosJson(String iaHallazgosJson) {
        this.iaHallazgosJson = iaHallazgosJson;
    }

    public String getIaEstadoRevision() {
        return iaEstadoRevision;
    }

    public void setIaEstadoRevision(String iaEstadoRevision) {
        this.iaEstadoRevision = iaEstadoRevision;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
