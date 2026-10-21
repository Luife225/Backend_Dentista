package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;

public class NotificationDto {

    private String id;
    private String tipoEvento;
    private String titulo;
    private String cuerpoMensaje;
    private String estado;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaLectura;

    public NotificationDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
