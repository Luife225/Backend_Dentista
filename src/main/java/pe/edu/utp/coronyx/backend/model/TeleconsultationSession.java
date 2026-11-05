package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "teleconsulta_sesion")
public class TeleconsultationSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long getId() { return id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    public Clinic clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    public Appointment cita;

    @Column(name = "sala_id", unique = true)
    public String salaId;

    @Column(name = "estado")
    public String estado = "PROGRAMADA";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iniciada_por_id")
    public ClinicUser iniciadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalizada_por_id")
    public ClinicUser finalizadaPor;

    @Column(name = "fecha_inicio")
    public OffsetDateTime fechaInicio;

    @Column(name = "fecha_fin")
    public OffsetDateTime fechaFin;

    @Column(name = "fecha_creacion")
    public OffsetDateTime fechaCreacion;
}
