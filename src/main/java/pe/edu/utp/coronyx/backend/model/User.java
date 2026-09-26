package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(name = "correo", nullable = false, unique = true, length = 254)
    private String correo;

    @NotBlank
    @Size(max = 255)
    @Column(name = "clave_hash", nullable = false, length = 255)
    private String claveHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @NotBlank
    @Size(max = 100)
    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @NotBlank
    @Size(max = 20)
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "correo_verificado_en")
    private OffsetDateTime correoVerificadoEn;

    @Size(max = 255)
    @Column(name = "token_recuperacion_hash", length = 255)
    private String tokenRecuperacionHash;

    @Column(name = "token_recuperacion_expira_en")
    private OffsetDateTime tokenRecuperacionExpiraEn;

    @Column(name = "token_recuperacion_usado_en")
    private OffsetDateTime tokenRecuperacionUsadoEn;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @NotNull
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    public User() {
    }

    public User(String correo, String claveHash, String nombres, String apellidos) {
        this.correo = correo;
        this.claveHash = claveHash;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.estado = "ACTIVO";
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = now;
        }
        if (fechaActualizacion == null) {
            fechaActualizacion = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getClaveHash() {
        return claveHash;
    }

    public void setClaveHash(String claveHash) {
        this.claveHash = claveHash;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getCorreoVerificadoEn() {
        return correoVerificadoEn;
    }

    public void setCorreoVerificadoEn(OffsetDateTime correoVerificadoEn) {
        this.correoVerificadoEn = correoVerificadoEn;
    }

    public String getTokenRecuperacionHash() {
        return tokenRecuperacionHash;
    }

    public void setTokenRecuperacionHash(String tokenRecuperacionHash) {
        this.tokenRecuperacionHash = tokenRecuperacionHash;
    }

    public OffsetDateTime getTokenRecuperacionExpiraEn() {
        return tokenRecuperacionExpiraEn;
    }

    public void setTokenRecuperacionExpiraEn(OffsetDateTime tokenRecuperacionExpiraEn) {
        this.tokenRecuperacionExpiraEn = tokenRecuperacionExpiraEn;
    }

    public OffsetDateTime getTokenRecuperacionUsadoEn() {
        return tokenRecuperacionUsadoEn;
    }

    public void setTokenRecuperacionUsadoEn(OffsetDateTime tokenRecuperacionUsadoEn) {
        this.tokenRecuperacionUsadoEn = tokenRecuperacionUsadoEn;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
