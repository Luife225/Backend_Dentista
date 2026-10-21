package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ClinicalEncounterResponseDto {

    private Long id;
    private Long clinicaId;
    private Long pacienteId;
    private String pacienteNombreCompleto;
    private String pacienteNumeroDocumento;
    private Long citaId;
    private Long odontologoId;
    private String odontologoNombreCompleto;
    private String estado;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaConfirmacion;
    private ClinicalVersionResponseDto versionActual;
    private Integer totalVersiones = 0;
    private List<ClinicalVersionResponseDto> versiones = new ArrayList<>();

    public ClinicalEncounterResponseDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClinicaId() {
        return clinicaId;
    }

    public void setClinicaId(Long clinicaId) {
        this.clinicaId = clinicaId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
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

    public Long getCitaId() {
        return citaId;
    }

    public void setCitaId(Long citaId) {
        this.citaId = citaId;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public void setOdontologoId(Long odontologoId) {
        this.odontologoId = odontologoId;
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
