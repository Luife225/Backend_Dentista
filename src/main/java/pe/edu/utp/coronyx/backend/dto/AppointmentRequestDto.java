package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AppointmentRequestDto {

    private UUID clinicaId;

    @NotNull(message = "El ID del paciente es obligatorio")
    private UUID pacienteId;

    @NotNull(message = "El ID del odontólogo (usuario_clinica) es obligatorio")
    private UUID odontologoId;

    private UUID creadoPorId;

    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    private OffsetDateTime inicioEn;

    @NotNull(message = "La fecha y hora de fin es obligatoria")
    private OffsetDateTime finEn;

    @NotBlank(message = "La modalidad es obligatoria")
    @Pattern(regexp = "^(PRESENCIAL|VIRTUAL)$", message = "La modalidad debe ser PRESENCIAL o VIRTUAL")
    private String modalidad = "PRESENCIAL";

    @Size(max = 500, message = "El motivo no puede exceder los 500 caracteres")
    private String motivo;

    @Size(max = 100, message = "El consultorio o sillón no puede exceder 100 caracteres")
    private String consultorio = "Sillón 1";

    @Size(max = 500, message = "El enlace de teleconsulta no puede exceder los 500 caracteres")
    private String enlaceTeleconsulta;

    public AppointmentRequestDto() {
    }

    public UUID getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(UUID clinicaId) {
        this.clinicaId = clinicaId;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    public UUID getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(UUID odontologoId) {
        this.odontologoId = odontologoId;
    }

    public UUID getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(UUID creadoPorId) {
        this.creadoPorId = creadoPorId;
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
