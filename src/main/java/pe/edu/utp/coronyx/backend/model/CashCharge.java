package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "caja_cargo")
public class CashCharge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    public Patient paciente;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    public Appointment cita;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    public ClinicUser creadoPor;
    @Column(name = "concepto")
    public String concepto;
    @Column(name = "importe")
    public java.math.BigDecimal importe;
    @Column(name = "estado")
    public String estado;
    @Column(name = "solicitud_id")
    public String solicitudId;
    @Column(name = "motivo_anulacion")
    public String motivoAnulacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "anulado_por_id")
    public ClinicUser anuladoPor;
    @Column(name = "fecha_anulacion")
    public java.time.OffsetDateTime fechaAnulacion;
    @Column(name = "fecha_creacion")
    public java.time.OffsetDateTime fechaCreacion;
}
