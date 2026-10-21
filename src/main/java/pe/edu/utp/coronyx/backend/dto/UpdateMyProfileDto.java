package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.Size;

public class UpdateMyProfileDto {

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    private String telefono;

    @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
    private String ciudad;

    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String direccion;

    // Campos sensibles bloqueados: si se envían valores no nulos para alterarlos, se validarán estrictamente
    private String alergias;
    private String antecedentesMedicos;
    private String medicamentos;
    private String estado;
    private String tipoDocumento;
    private String numeroDocumento;

    public UpdateMyProfileDto() {
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
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

    public String getAlergias() {
        return alergias;
    }

    public void setAlergias(String alergias) {
        this.alergias = alergias;
    }

    public String getAntecedentesMedicos() {
        return antecedentesMedicos;
    }

    public void setAntecedentesMedicos(String antecedentesMedicos) {
        this.antecedentesMedicos = antecedentesMedicos;
    }

    public String getMedicamentos() {
        return medicamentos;
    }

    public void setMedicamentos(String medicamentos) {
        this.medicamentos = medicamentos;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }
}
