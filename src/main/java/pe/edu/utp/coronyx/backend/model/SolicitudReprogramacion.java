package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Entity
@Table(name = "solicitud_reprogramacion")
public class SolicitudReprogramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false)
    private Appointment cita;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_usuario_id", nullable = false)
    private User solicitanteUsuario;

    @NotNull
    @Column(name = "nueva_fecha_hora_inicio", nullable = false)
    private OffsetDateTime nuevaFechaHoraInicio;

    @NotNull
    @Column(name = "nueva_fecha_hora_fin", nullable = false)
    private OffsetDateTime nuevaFechaHoraFin;

    @NotBlank
    @Size(max = 500)
    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @NotBlank
    @Size(max = 20)
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolucion_usuario_id")
    private User resolucionUsuario;

    @Size(max = 500)
    @Column(name = "resolucion_motivo", length = 500)
    private String resolucionMotivo;

    @NotNull
    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private OffsetDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private OffsetDateTime fechaResolucion;

    public SolicitudReprogramacion() {
    }

    public SolicitudReprogramacion(Appointment cita, User solicitanteUsuario,
                                  OffsetDateTime nuevaFechaHoraInicio, OffsetDateTime nuevaFechaHoraFin,
                                  String motivo) {
        this.cita = cita;
        this.solicitanteUsuario = solicitanteUsuario;
        this.nuevaFechaHoraInicio = nuevaFechaHoraInicio;
        this.nuevaFechaHoraFin = nuevaFechaHoraFin;
        this.motivo = motivo;
        this.estado = "PENDIENTE";
    }

    @PrePersist
    protected void onCreate() {
        if (fechaSolicitud == null) {
            fechaSolicitud = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getCita() {
        return cita;
    }

    public void setCita(Appointment cita) {
        this.cita = cita;
    }

    public User getSolicitanteUsuario() {
        return solicitanteUsuario;
    }

    public void setSolicitanteUsuario(User solicitanteUsuario) {
        this.solicitanteUsuario = solicitanteUsuario;
    }

    public OffsetDateTime getNuevaFechaHoraInicio() {
        return nuevaFechaHoraInicio;
    }

    public void setNuevaFechaHoraInicio(OffsetDateTime nuevaFechaHoraInicio) {
        this.nuevaFechaHoraInicio = nuevaFechaHoraInicio;
    }

    public OffsetDateTime getNuevaFechaHoraFin() {
        return nuevaFechaHoraFin;
    }

    public void setNuevaFechaHoraFin(OffsetDateTime nuevaFechaHoraFin) {
        this.nuevaFechaHoraFin = nuevaFechaHoraFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public User getResolucionUsuario() {
        return resolucionUsuario;
    }

    public void setResolucionUsuario(User resolucionUsuario) {
        this.resolucionUsuario = resolucionUsuario;
    }

    public String getResolucionMotivo() {
        return resolucionMotivo;
    }

    public void setResolucionMotivo(String resolucionMotivo) {
        this.resolucionMotivo = resolucionMotivo;
    }

    public OffsetDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(OffsetDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public OffsetDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(OffsetDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }
}
