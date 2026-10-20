package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "inventario_movimiento")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id")
    public InventoryItem insumo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    public ClinicUser creadoPor;
    @Column(name = "tipo")
    public String tipo;
    @Column(name = "cantidad")
    public int cantidad;
    @Column(name = "estado_origen")
    public String estadoOrigen;
    @Column(name = "estado_destino")
    public String estadoDestino;
    @Column(name = "stock_cerrado_anterior")
    public int stockCerradoAnterior;
    @Column(name = "stock_cerrado_resultante")
    public int stockCerradoResultante;
    @Column(name = "stock_en_uso_anterior")
    public int stockEnUsoAnterior;
    @Column(name = "stock_en_uso_resultante")
    public int stockEnUsoResultante;
    @Column(name = "stock_anterior")
    public int stockAnterior;
    @Column(name = "stock_resultante")
    public int stockResultante;
    @Column(name = "motivo")
    public String motivo;
    @Column(name = "solicitud_id")
    public String solicitudId;
    @Column(name = "fecha_creacion")
    public java.time.OffsetDateTime fechaCreacion;
}
