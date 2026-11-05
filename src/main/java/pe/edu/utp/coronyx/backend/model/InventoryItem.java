package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "inventario_insumo")
public class InventoryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;
    @Column(name = "nombre")
    public String nombre;
    @Column(name = "categoria")
    public String categoria;
    @Column(name = "unidad")
    public String unidad;
    @Column(name = "codigo")
    public String codigo;
    @Column(name = "presentacion")
    public String presentacion = "UNIDAD";
    @Column(name = "clasificacion")
    public String clasificacion = "CONSUMIBLE";
    @Column(name = "contenido_descriptivo")
    public String contenidoDescriptivo;
    @Column(name = "stock")
    public int stock;
    @Column(name = "stock_cerrado")
    public int stockCerrado;
    @Column(name = "stock_en_uso")
    public int stockEnUso;
    @Column(name = "stock_minimo")
    public int stockMinimo;
    @Column(name = "costo_unitario")
    public java.math.BigDecimal costoUnitario;
    @Column(name = "proveedor")
    public String proveedor;
    @Column(name = "activo")
    public boolean activo;
    @Column(name = "fecha_creacion")
    public java.time.OffsetDateTime fechaCreacion;
}
