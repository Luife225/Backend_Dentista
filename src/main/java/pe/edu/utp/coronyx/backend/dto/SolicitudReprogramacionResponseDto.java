package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;

public class SolicitudReprogramacionResponseDto {

    private String id;
    private String citaId;
    private String solicitanteUsuarioId;
    private String solicitanteNombreCompleto;
    private String pacienteId;
    private String pacienteNombreCompleto;
    private String odontologoId;
    private String odontologoNombreCompleto;
    private OffsetDateTime fechaActualCitaInicio;
    private OffsetDateTime fechaActualCitaFin;
    private OffsetDateTime nuevaFechaHoraInicio;
    private OffsetDateTime nuevaFechaHoraFin;
    private String motivo;
    private String estado;
    private String resolucionUsuarioId;
    private String resolucionUsuarioNombre;
    private String resolucionMotivo;
    private OffsetDateTime fechaSolicitud;
    private OffsetDateTime fechaResolucion;

    public SolicitudReprogramacionResponseDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCitaId() {
        return citaId;
    }

    public void setCitaId(String citaId) {
        this.citaId = citaId;
    }

    public String getSolicitanteUsuarioId() {
        return solicitanteUsuarioId;
    }

    public void setSolicitanteUsuarioId(String solicitanteUsuarioId) {
        this.solicitanteUsuarioId = solicitanteUsuarioId;
    }

    public String getSolicitanteNombreCompleto() {
        return solicitanteNombreCompleto;
    }

    public void setSolicitanteNombreCompleto(String solicitanteNombreCompleto) {
        this.solicitanteNombreCompleto = solicitanteNombreCompleto;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getPacienteNombreCompleto() {
        return pacienteNombreCompleto;
    }

    public void setPacienteNombreCompleto(String pacienteNombreCompleto) {
        this.pacienteNombreCompleto = pacienteNombreCompleto;
    }

    public String getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(String odontologoId) {
        this.odontologoId = odontologoId;
    }

    public String getOdontologoNombreCompleto() {
        return odontologoNombreCompleto;
    }

    public void setOdontologoNombreCompleto(String odontologoNombreCompleto) {
        this.odontologoNombreCompleto = odontologoNombreCompleto;
    }

    public OffsetDateTime getFechaActualCitaInicio() {
        return fechaActualCitaInicio;
    }

    public void setFechaActualCitaInicio(OffsetDateTime fechaActualCitaInicio) {
        this.fechaActualCitaInicio = fechaActualCitaInicio;
    }

    public OffsetDateTime getFechaActualCitaFin() {
        return fechaActualCitaFin;
    }

    public void setFechaActualCitaFin(OffsetDateTime fechaActualCitaFin) {
        this.fechaActualCitaFin = fechaActualCitaFin;
    }

    public OffsetDateTime getNuevaFechaHoraInicio() {
        return nuevaFechaHoraInicio;
    }

    public void setNuevaFechaHoraInicio(OffsetDateTime nuevaFechaHoraInicio) {
        this.nuevaFechaHoraInicio = nuevaFechaHoraInicio;
    }

    public OffsetDateTime getNuevaFechaHoraFin() {
        return nuevaFechaHoraFin;
    }

    public void setNuevaFechaHoraFin(OffsetDateTime nuevaFechaHoraFin) {
        this.nuevaFechaHoraFin = nuevaFechaHoraFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getResolucionUsuarioId() {
        return resolucionUsuarioId;
    }

    public void setResolucionUsuarioId(String resolucionUsuarioId) {
        this.resolucionUsuarioId = resolucionUsuarioId;
    }

    public String getResolucionUsuarioNombre() {
        return resolucionUsuarioNombre;
    }

    public void setResolucionUsuarioNombre(String resolucionUsuarioNombre) {
        this.resolucionUsuarioNombre = resolucionUsuarioNombre;
    }

    public String getResolucionMotivo() {
        return resolucionMotivo;
    }

    public void setResolucionMotivo(String resolucionMotivo) {
        this.resolucionMotivo = resolucionMotivo;
    }

    public OffsetDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(OffsetDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public OffsetDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(OffsetDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public String getResueltoPorId() {
        return resolucionUsuarioId;
    }

    public String getResueltoPorNombre() {
        return resolucionUsuarioNombre;
    }
}
