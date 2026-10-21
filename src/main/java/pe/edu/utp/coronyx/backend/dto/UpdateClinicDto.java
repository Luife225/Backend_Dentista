package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.Size;

public class UpdateClinicDto {

    @Size(max = 160, message = "El nombre no puede exceder 160 caracteres")
    private String nombre;

    @Size(max = 200, message = "La razón social no puede exceder 200 caracteres")
    private String razonSocial;

    @Size(max = 64, message = "La zona horaria no puede exceder 64 caracteres")
    private String zonaHoraria;

    @Size(max = 20, message = "El RUC no puede exceder 20 caracteres")
    private String ruc;

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String telefono;

    @Size(max = 254, message = "El correo no puede exceder 254 caracteres")
    private String correo;

    @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
    private String ciudad;

    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String direccion;

    public UpdateClinicDto() {
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
