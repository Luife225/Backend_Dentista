package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ClinicalEncounterResponseDto {

    private String id;
    private String clinicaId;
    private String pacienteId;
    private String pacienteNombreCompleto;
    private String pacienteNumeroDocumento;
    private String citaId;
    private String odontologoId;
    private String odontologoNombreCompleto;
    private String estado;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaConfirmacion;
    private ClinicalVersionResponseDto versionActual;
    private Integer totalVersiones = 0;
    private List<ClinicalVersionResponseDto> versiones = new ArrayList<>();

    public ClinicalEncounterResponseDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id == null ? null : id.toString();
    }

    public String getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(Long clinicaId) {
        this.clinicaId = clinicaId == null ? null : clinicaId.toString();
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId == null ? null : pacienteId.toString();
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

    public String getCitaId() {
        return citaId;
    }

    public void setCitaId(Long citaId) {
        this.citaId = citaId == null ? null : citaId.toString();
    }

    public String getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(Long odontologoId) {
        this.odontologoId = odontologoId == null ? null : odontologoId.toString();
    }

    public String getOdontologoNombreCompleto() {
        return odontologoNombreCompleto;
    }

    public void setOdontologoNombreCompleto(String odontologoNombreCompleto) {
        this.odontologoNombreCompleto = odontologoNombreCompleto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public void setFechaConfirmacion(OffsetDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }

    public ClinicalVersionResponseDto getVersionActual() {
        return versionActual;
    }

    public void setVersionActual(ClinicalVersionResponseDto versionActual) {
        this.versionActual = versionActual;
    }

    public Integer getTotalVersiones() {
        return totalVersiones;
    }

    public void setTotalVersiones(Integer totalVersiones) {
        this.totalVersiones = totalVersiones;
    }

    public List<ClinicalVersionResponseDto> getVersiones() {
        return versiones;
    }

    public void setVersiones(List<ClinicalVersionResponseDto> versiones) {
        this.versiones = versiones;
    }
}
