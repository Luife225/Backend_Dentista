package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "hallazgo_odontograma")
public class HallazgoOdontograma {

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
    @JoinColumn(name = "version_odontograma_id", nullable = false)
    private VersionOdontograma versionOdontograma;

    @NotBlank
    @Size(max = 4)
    @Column(name = "codigo_pieza_dental", nullable = false, length = 4)
    private String codigoPiezaDental;

    @NotBlank
    @Size(max = 12)
    @Column(name = "codigo_superficie_cara", nullable = false, length = 12)
    private String codigoSuperficieCara;

    @NotBlank
    @Size(max = 40)
    @Column(name = "codigo_condicion", nullable = false, length = 40)
    private String codigoCondicion;

    @Column(name = "nota_observacion", columnDefinition = "TEXT")
    private String notaObservacion;

    public HallazgoOdontograma() {
    }

    public HallazgoOdontograma(Clinic clinica, VersionOdontograma versionOdontograma,
                                String codigoPiezaDental, String codigoSuperficieCara,
                                String codigoCondicion, String notaObservacion) {
        this.clinica = clinica;
        this.versionOdontograma = versionOdontograma;
        this.codigoPiezaDental = codigoPiezaDental;
        this.codigoSuperficieCara = codigoSuperficieCara;
        this.codigoCondicion = codigoCondicion;
        this.notaObservacion = notaObservacion;
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

    public VersionOdontograma getVersionOdontograma() {
        return versionOdontograma;
    }

    public void setVersionOdontograma(VersionOdontograma versionOdontograma) {
        this.versionOdontograma = versionOdontograma;
    }

    public String getCodigoPiezaDental() {
        return codigoPiezaDental;
    }

    public void setCodigoPiezaDental(String codigoPiezaDental) {
        this.codigoPiezaDental = codigoPiezaDental;
    }

    public String getCodigoSuperficieCara() {
        return codigoSuperficieCara;
    }

    public void setCodigoSuperficieCara(String codigoSuperficieCara) {
        this.codigoSuperficieCara = codigoSuperficieCara;
    }

    public String getCodigoCondicion() {
        return codigoCondicion;
    }

    public void setCodigoCondicion(String codigoCondicion) {
        this.codigoCondicion = codigoCondicion;
    }

    public String getNotaObservacion() {
        return notaObservacion;
    }

    public void setNotaObservacion(String notaObservacion) {
        this.notaObservacion = notaObservacion;
    }
}
