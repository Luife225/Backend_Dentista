package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * DTO para agendamiento de citas desde el portal de paciente (/me/citas).
 * No expone pacienteId al cliente; la identidad del paciente se resuelve estrictamente
 * desde el UserPrincipal de la sesión autenticada.
 */
public class PatientAppointmentRequestDto {

    @NotBlank(message = "El ID del odontólogo es obligatorio")
    @Pattern(regexp = "^[1-9]\\d*$", message = "El ID del odontólogo debe ser un número entero positivo")
    private String odontologoId;

    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    private OffsetDateTime inicioEn;

    @NotNull(message = "La fecha y hora de fin es obligatoria")
    private OffsetDateTime finEn;

    @Pattern(regexp = "^(PRESENCIAL|VIRTUAL)$", message = "La modalidad debe ser PRESENCIAL o VIRTUAL")
    private String modalidad = "PRESENCIAL";

    @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
    private String motivo;

    @Size(max = 100, message = "El consultorio no puede exceder 100 caracteres")
    private String consultorio = "Sillón 1";

    @Size(max = 500, message = "El enlace de teleconsulta no puede exceder 500 caracteres")
    private String enlaceTeleconsulta;

    public PatientAppointmentRequestDto() {
    }

    public String getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(String odontologoId) {
        this.odontologoId = odontologoId;
    }

    public OffsetDateTime getInicioEn() {
        return inicioEn;
    }

    public void setInicioEn(OffsetDateTime inicioEn) {
        this.inicioEn = inicioEn;
    }

    public OffsetDateTime getFinEn() {
        return finEn;
    }

    public void setFinEn(OffsetDateTime finEn) {
        this.finEn = finEn;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(String consultorio) {
        this.consultorio = consultorio;
    }

    public String getEnlaceTeleconsulta() {
        return enlaceTeleconsulta;
    }

    public void setEnlaceTeleconsulta(String enlaceTeleconsulta) {
        this.enlaceTeleconsulta = enlaceTeleconsulta;
    }
}
