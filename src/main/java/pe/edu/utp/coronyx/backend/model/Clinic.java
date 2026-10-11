package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Entity
@Table(name = "clinica")
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotBlank
    @Size(max = 160)
    @Column(name = "nombre", nullable = false, length = 160)
    private String nombre;

    @Size(max = 200)
    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @NotBlank
    @Size(max = 64)
    @Column(name = "zona_horaria", nullable = false, length = 64)
    private String zonaHoraria = "America/Lima";

    @NotBlank
    @Size(max = 16)
    @Column(name = "estado", nullable = false, length = 16)
    private String estado = "ABIERTO";

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @NotNull
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @Size(max = 20)
    @Column(name = "ruc", length = 20)
    private String ruc;

    @Size(max = 30)
    @Column(name = "telefono", length = 30)
    private String telefono;

    @Size(max = 254)
    @Column(name = "correo", length = 254)
    private String correo;

    @Size(max = 100)
    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Size(max = 255)
    @Column(name = "direccion", length = 255)
    private String direccion;

    public Clinic() {
    }

    public Clinic(String nombre, String razonSocial, String zonaHoraria, String estado) {
        this.nombre = nombre;
        this.razonSocial = razonSocial;
        this.zonaHoraria = zonaHoraria != null ? zonaHoraria : "America/Lima";
        this.estado = estado != null ? estado : "ABIERTO";
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public void setZonaHoraria(String zonaHoraria) {
        this.zonaHoraria = zonaHoraria;
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

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}
