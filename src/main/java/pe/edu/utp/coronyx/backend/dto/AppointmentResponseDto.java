package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AppointmentResponseDto {

    private UUID id;
    private UUID clinicaId;
    private String clinicaNombre;

    private UUID pacienteId;
    private String pacienteNombreCompleto;
    private String pacienteNumeroDocumento;
    private String pacienteTelefono;

    private UUID odontologoId;
    private String odontologoNombreCompleto;
    private String odontologoCorreo;

    private UUID creadoPorId;
    private String creadoPorNombreCompleto;

    private OffsetDateTime inicioEn;
    private OffsetDateTime finEn;
    private String modalidad;
    private String estado;
    private String estadoAsistencia;
    private String motivo;
    private String consultorio;
    private String enlaceTeleconsulta;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;

    public AppointmentResponseDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(UUID clinicaId) {
        this.clinicaId = clinicaId;
    }

    public String getClinicaNombre() {
        return clinicaNombre;
    }

    public void setClinicaNombre(String clinicaNombre) {
        this.clinicaNombre = clinicaNombre;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getPacienteNombreCompleto() {
        return pacienteNombreCompleto;
    }

    public void setPacienteNombreCompleto(String pacienteNombreCompleto) {
        this.pacienteNombreCompleto = pacienteNombreCompleto;
    }

    public String getPacienteNumeroDocumento() {
        return pacienteNumeroDocumento;
    }

    public void setPacienteNumeroDocumento(String pacienteNumeroDocumento) {
        this.pacienteNumeroDocumento = pacienteNumeroDocumento;
    }

    public String getPacienteTelefono() {
        return pacienteTelefono;
    }

    public void setPacienteTelefono(String pacienteTelefono) {
        this.pacienteTelefono = pacienteTelefono;
    }

    public UUID getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(UUID odontologoId) {
        this.odontologoId = odontologoId;
    }

    public String getOdontologoNombreCompleto() {
        return odontologoNombreCompleto;
    }

    public void setOdontologoNombreCompleto(String odontologoNombreCompleto) {
        this.odontologoNombreCompleto = odontologoNombreCompleto;
    }

    public String getOdontologoCorreo() {
        return odontologoCorreo;
    }

    public void setOdontologoCorreo(String odontologoCorreo) {
        this.odontologoCorreo = odontologoCorreo;
    }

    public UUID getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(UUID creadoPorId) {
        this.creadoPorId = creadoPorId;
    }

    public String getCreadoPorNombreCompleto() {
        return creadoPorNombreCompleto;
    }

    public void setCreadoPorNombreCompleto(String creadoPorNombreCompleto) {
        this.creadoPorNombreCompleto = creadoPorNombreCompleto;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getEstadoAsistencia() {
        return estadoAsistencia;
    }

    public void setEstadoAsistencia(String estadoAsistencia) {
        this.estadoAsistencia = estadoAsistencia;
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
}
