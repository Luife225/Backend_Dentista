package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.Size;

public class UpdateClinicUserDto {

    @Size(min = 2, max = 100, message = "Los nombres deben tener entre 2 y 100 caracteres")
    private String nombres;

    @Size(min = 2, max = 100, message = "Los apellidos deben tener entre 2 y 100 caracteres")
    private String apellidos;

    private String rol;

    @Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres si se desea actualizar")
    private String clave;

    private String estado;

    public UpdateClinicUserDto() {
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

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
