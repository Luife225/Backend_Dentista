package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "caja_pago")
public class CashPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id")
    public CashCharge cargo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    public ClinicUser creadoPor;
    @Column(name = "importe")
    public java.math.BigDecimal importe;
    @Column(name = "medio")
    public String medio;
    @Column(name = "referencia")
    public String referencia;
    @Column(name = "solicitud_id")
    public String solicitudId;
    @Column(name = "anulado")
    public boolean anulado;
    @Column(name = "motivo_anulacion")
    public String motivoAnulacion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por_id")
    public ClinicUser anuladoPor;
    @Column(name = "fecha_anulacion")
    public java.time.OffsetDateTime fechaAnulacion;
    @Column(name = "fecha_creacion")
    public java.time.OffsetDateTime fechaCreacion;
}
