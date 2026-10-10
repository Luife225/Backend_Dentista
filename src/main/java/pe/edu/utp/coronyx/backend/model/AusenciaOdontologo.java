package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ausencia_odontologo")
public class AusenciaOdontologo {

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
    @JoinColumn(name = "odontologo_id", nullable = false)
    private ClinicUser odontologo;

    @NotNull
    @Column(name = "inicio_en", nullable = false)
    private OffsetDateTime inicioEn;

    @NotNull
    @Column(name = "fin_en", nullable = false)
    private OffsetDateTime finEn;

    @Size(max = 255)
    @Column(name = "motivo", length = 255)
    private String motivo;

    @NotNull
    @Size(max = 40)
    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo = "VACACIONES";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public AusenciaOdontologo() {
    }

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
        if (tipo == null || tipo.isBlank()) {
            tipo = "VACACIONES";
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

    public ClinicUser getOdontologo() {
        return odontologo;
    }

    public void setOdontologo(ClinicUser odontologo) {
        this.odontologo = odontologo;
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

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
