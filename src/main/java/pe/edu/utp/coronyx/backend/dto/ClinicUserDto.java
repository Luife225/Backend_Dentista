package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;

/**
 * DTO para la representación de una membresía/cuenta de usuario en la clínica.
 * Todos los identificadores se exponen como cadenas decimales positivas (contrato de IDs).
 */
public class ClinicUserDto {

    private String id; // usuario_clinica.id
    private String usuarioId; // usuario.id
    private String correo;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String rol;
    private String rolNombre;
    private String estado;
    private String clinicaId;
    private String clinicaNombre;
    private String pacienteId;
    private OffsetDateTime fechaAsignacion;
    private OffsetDateTime fechaActualizacion;

    public ClinicUserDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
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

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getRolNombre() {
        return rolNombre;
    }

    public void setRolNombre(String rolNombre) {
        this.rolNombre = rolNombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(String clinicaId) {
        this.clinicaId = clinicaId;
    }

    public String getClinicaNombre() {
        return clinicaNombre;
    }

    public void setClinicaNombre(String clinicaNombre) {
        this.clinicaNombre = clinicaNombre;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
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
