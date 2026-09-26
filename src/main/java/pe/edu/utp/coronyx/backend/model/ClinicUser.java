package pe.edu.utp.coronyx.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario_clinica", uniqueConstraints = {
        @UniqueConstraint(name = "uq_usuario_clinica_rol", columnNames = {"clinica_id", "usuario_id"})
})
public class ClinicUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinic clinica;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Role rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_asignado_por")
    private User rolAsignadoPor;

    @NotBlank
    @Size(max = 16)
    @Column(name = "estado", nullable = false, length = 16)
    private String estado = "ACTIVO";

    @NotNull
    @Column(name = "fecha_asignacion", nullable = false, updatable = false)
    private OffsetDateTime fechaAsignacion;

    @NotNull
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    public ClinicUser() {
    }

    public ClinicUser(Clinic clinica, User usuario, Role rol, User rolAsignadoPor, String estado) {
        this.clinica = clinica;
        this.usuario = usuario;
        this.rol = rol;
        this.rolAsignadoPor = rolAsignadoPor;
        this.estado = estado != null ? estado : "ACTIVO";
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (fechaAsignacion == null) {
            fechaAsignacion = now;
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

    public Clinic getClinica() {
        return clinica;
    }

    public void setClinica(Clinic clinica) {
        this.clinica = clinica;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public Role getRol() {
        return rol;
    }

    public void setRol(Role rol) {
        this.rol = rol;
    }

    public User getRolAsignadoPor() {
        return rolAsignadoPor;
    }

    public void setRolAsignadoPor(User rolAsignadoPor) {
        this.rolAsignadoPor = rolAsignadoPor;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaAsignacion() {
        return fechaAsignacion;
    }

    public void setFechaAsignacion(OffsetDateTime fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
