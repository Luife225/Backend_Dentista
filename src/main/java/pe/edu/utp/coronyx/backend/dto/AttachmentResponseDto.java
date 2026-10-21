package pe.edu.utp.coronyx.backend.dto;

import pe.edu.utp.coronyx.backend.model.Attachment;

import java.time.format.DateTimeFormatter;

/**
 * DTO de respuesta para archivos adjuntos clínicos y administrativos (F06-A).
 * Cumple con la representación de identificadores como Strings decimales y
 * omite cualquier detalle de historia clínica técnica privada.
 */
public class AttachmentResponseDto {

    private String id;
    private String clinicaId;
    private String pacienteId;
    private String subidoPorUsuarioId;
    private String subidoPorNombre;
    private String citaId;
    private String tipoArchivo;
    private String ambito;
    public String getAmbito() { return ambito; }
    public void setAmbito(String ambito) { this.ambito = ambito; }
    private String claveAlmacenamiento;
    private String nombreOriginal;
    private String tipoMime;
    private Long pesoBytes;
    private String regionAnatomica;
    private Boolean esVisiblePaciente;
    private String fechaCreacion;
    private String downloadUrl;
    private String iaEstadoRevision;

    public AttachmentResponseDto() {
    }

    public static AttachmentResponseDto fromEntity(Attachment entity) {
        if (entity == null) {
            return null;
        }
        AttachmentResponseDto dto = new AttachmentResponseDto();
        dto.setId(String.valueOf(entity.getId()));
        dto.setClinicaId(entity.getClinica() != null ? String.valueOf(entity.getClinica().getId()) : null);
        dto.setPacienteId(entity.getPaciente() != null ? String.valueOf(entity.getPaciente().getId()) : null);
        dto.setSubidoPorUsuarioId(entity.getSubidoPorUsuario() != null ? String.valueOf(entity.getSubidoPorUsuario().getId()) : null);
        dto.setSubidoPorNombre(entity.getSubidoPorUsuario() != null ? entity.getSubidoPorUsuario().getNombreCompleto() : null);
        dto.setCitaId(entity.getCita() != null ? String.valueOf(entity.getCita().getId()) : null);
        dto.setTipoArchivo(entity.getTipoArchivo());
        dto.setAmbito(entity.getAmbito());
        dto.setClaveAlmacenamiento(entity.getClaveAlmacenamiento());
        dto.setNombreOriginal(entity.getNombreOriginal());
        dto.setTipoMime(entity.getTipoMime());
        dto.setPesoBytes(entity.getPesoBytes());
        dto.setRegionAnatomica(entity.getRegionAnatomica());
        dto.setEsVisiblePaciente(entity.isEsVisiblePaciente());
        dto.setIaEstadoRevision(entity.getIaEstadoRevision());
        if (entity.getFechaCreacion() != null) {
            dto.setFechaCreacion(entity.getFechaCreacion().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        dto.setDownloadUrl("/api/v1/attachments/" + entity.getId() + "/download");
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(String clinicaId) {
        this.clinicaId = clinicaId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getSubidoPorUsuarioId() {
        return subidoPorUsuarioId;
    }

    public void setSubidoPorUsuarioId(String subidoPorUsuarioId) {
        this.subidoPorUsuarioId = subidoPorUsuarioId;
    }

    public String getSubidoPorNombre() {
        return subidoPorNombre;
    }

    public void setSubidoPorNombre(String subidoPorNombre) {
        this.subidoPorNombre = subidoPorNombre;
    }

    public String getCitaId() {
        return citaId;
    }

    public void setCitaId(String citaId) {
        this.citaId = citaId;
    }

    public String getTipoArchivo() {
        return tipoArchivo;
    }

    public void setTipoArchivo(String tipoArchivo) {
        this.tipoArchivo = tipoArchivo;
    }

    public String getClaveAlmacenamiento() {
        return claveAlmacenamiento;
    }

    public void setClaveAlmacenamiento(String claveAlmacenamiento) {
        this.claveAlmacenamiento = claveAlmacenamiento;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public void setNombreOriginal(String nombreOriginal) {
        this.nombreOriginal = nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public void setTipoMime(String tipoMime) {
        this.tipoMime = tipoMime;
    }

    public Long getPesoBytes() {
        return pesoBytes;
    }

    public void setPesoBytes(Long pesoBytes) {
        this.pesoBytes = pesoBytes;
    }

    public String getRegionAnatomica() {
        return regionAnatomica;
    }

    public void setRegionAnatomica(String regionAnatomica) {
        this.regionAnatomica = regionAnatomica;
    }

    public Boolean getEsVisiblePaciente() {
        return esVisiblePaciente;
    }

    public void setEsVisiblePaciente(Boolean esVisiblePaciente) {
        this.esVisiblePaciente = esVisiblePaciente;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getIaEstadoRevision() {
        return iaEstadoRevision;
    }

    public void setIaEstadoRevision(String iaEstadoRevision) {
        this.iaEstadoRevision = iaEstadoRevision;
    }
}
