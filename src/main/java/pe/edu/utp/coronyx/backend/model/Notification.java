package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificacion")
public class Notification {

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
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private User destinatario;

    @NotBlank
    @Size(max = 40)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;

    @NotBlank
    @Size(max = 160)
    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @NotBlank
    @Size(max = 500)
    @Column(name = "cuerpo_mensaje", nullable = false, length = 500)
    private String cuerpoMensaje;

    @NotNull
    @Size(max = 16)
    @Column(name = "estado", nullable = false, length = 16)
    private String estado = "ENVIADO";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_lectura")
    private OffsetDateTime fechaLectura;

    public Notification() {
    }

    public Notification(Clinic clinica, User destinatario, String tipoEvento, String titulo, String cuerpoMensaje) {
        this.clinica = clinica;
        this.destinatario = destinatario;
        this.tipoEvento = tipoEvento;
        this.titulo = titulo;
        this.cuerpoMensaje = cuerpoMensaje;
        this.estado = "ENVIADO";
    }

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
        if (estado == null || estado.isBlank()) {
            estado = "ENVIADO";
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

    public User getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(User destinatario) {
        this.destinatario = destinatario;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getCuerpoMensaje() {
        return cuerpoMensaje;
    }

    public void setCuerpoMensaje(String cuerpoMensaje) {
        this.cuerpoMensaje = cuerpoMensaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaLectura() {
        return fechaLectura;
    }

    public void setFechaLectura(OffsetDateTime fechaLectura) {
        this.fechaLectura = fechaLectura;
    }
}
