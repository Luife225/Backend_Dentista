package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventario_catalogo_importacion")
public class CatalogImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;

    @Column(name = "lote_id")
    public String loteId;

    @Column(name = "nombre_archivo")
    public String nombreArchivo;

    @Column(name = "hoja")
    public String hoja;

    @Column(name = "total_filas")
    public int totalFilas;

    @Column(name = "filas_importadas")
    public int filasImportadas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    public ClinicUser creadoPor;

    @Column(name = "fecha_creacion")
    public OffsetDateTime fechaCreacion;
}
