package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;

public class ClinicalVersionResponseDto {

    private Long id;
    private Long clinicaId;
    private Long atencionClinicaId;
    private Long versionAnteriorId;
    private Long firmadoPorId;
    private String firmadoPorNombre;
    private Integer numeroVersion;
    private String motivoConsulta;
    private String examenClinico;
    private String diagnostico;
    private String procedimientos;
    private String planTratamiento;
    private String recetasPrescripciones;
    private String indicaciones;
    private String evolucion;
    private String proximaCitaSugerida;
    private String signosVitales;
    private String textoPendienteRevision;
    private Boolean estaConfirmado;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaConfirmacion;

    public ClinicalVersionResponseDto() {
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

    public Long getAtencionClinicaId() {
        return atencionClinicaId;
    }

    public void setAtencionClinicaId(Long atencionClinicaId) {
        this.atencionClinicaId = atencionClinicaId;
    }

    public Long getVersionAnteriorId() {
        return versionAnteriorId;
    }

    public void setVersionAnteriorId(Long versionAnteriorId) {
        this.versionAnteriorId = versionAnteriorId;
    }

    public Long getFirmadoPorId() {
        return firmadoPorId;
    }

    public void setFirmadoPorId(Long firmadoPorId) {
        this.firmadoPorId = firmadoPorId;
    }

    public String getFirmadoPorNombre() {
        return firmadoPorNombre;
    }

    public void setFirmadoPorNombre(String firmadoPorNombre) {
        this.firmadoPorNombre = firmadoPorNombre;
    }

    public Integer getNumeroVersion() {
        return numeroVersion;
    }

    public void setNumeroVersion(Integer numeroVersion) {
        this.numeroVersion = numeroVersion;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public void setMotivoConsulta(String motivoConsulta) {
        this.motivoConsulta = motivoConsulta;
    }

    public String getExamenClinico() {
        return examenClinico;
    }

    public void setExamenClinico(String examenClinico) {
        this.examenClinico = examenClinico;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(String diagnostico) {
        this.diagnostico = diagnostico;
    }

    public String getProcedimientos() {
        return procedimientos;
    }

    public void setProcedimientos(String procedimientos) {
        this.procedimientos = procedimientos;
    }

    public String getPlanTratamiento() {
        return planTratamiento;
    }

    public void setPlanTratamiento(String planTratamiento) {
        this.planTratamiento = planTratamiento;
    }

    public String getRecetasPrescripciones() {
        return recetasPrescripciones;
    }

    public void setRecetasPrescripciones(String recetasPrescripciones) {
        this.recetasPrescripciones = recetasPrescripciones;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public void setIndicaciones(String indicaciones) {
        this.indicaciones = indicaciones;
    }

    public String getEvolucion() {
        return evolucion;
    }

    public void setEvolucion(String evolucion) {
        this.evolucion = evolucion;
    }

    public String getProximaCitaSugerida() {
        return proximaCitaSugerida;
    }

    public void setProximaCitaSugerida(String proximaCitaSugerida) {
        this.proximaCitaSugerida = proximaCitaSugerida;
    }

    public String getSignosVitales() {
        return signosVitales;
    }

    public void setSignosVitales(String signosVitales) {
        this.signosVitales = signosVitales;
    }

    public String getTextoPendienteRevision() {
        return textoPendienteRevision;
    }

    public void setTextoPendienteRevision(String textoPendienteRevision) {
        this.textoPendienteRevision = textoPendienteRevision;
    }

    public Boolean getEstaConfirmado() {
        return estaConfirmado;
    }

    public void setEstaConfirmado(Boolean estaConfirmado) {
        this.estaConfirmado = estaConfirmado;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(OffsetDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public OffsetDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public void setFechaConfirmacion(OffsetDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }
}
