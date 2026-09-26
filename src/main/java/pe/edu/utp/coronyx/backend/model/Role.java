package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rol")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @NotBlank
    @Size(max = 80)
    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permisos_json", columnDefinition = "jsonb", nullable = false)
    private String permisosJson = "[]";

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Role() {
    }

    public Role(String codigo, String nombre, String descripcion, String permisosJson) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.permisosJson = permisosJson != null ? permisosJson : "[]";
        this.activo = true;
    }

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getPermisosJson() {
        return permisosJson;
    }

    public void setPermisosJson(String permisosJson) {
        this.permisosJson = permisosJson;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
