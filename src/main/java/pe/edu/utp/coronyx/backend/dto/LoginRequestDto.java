package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequestDto {

    @NotBlank
    @Email
    private String correo;

    @NotBlank
    private String clave;

    public LoginRequestDto() {
    }

    public LoginRequestDto(String correo, String clave) {
        this.correo = correo;
        this.clave = clave;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }
}
