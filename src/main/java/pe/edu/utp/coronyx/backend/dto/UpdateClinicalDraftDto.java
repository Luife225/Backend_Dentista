package pe.edu.utp.coronyx.backend.dto;

public class UpdateClinicalDraftDto {
    private Long revision;
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }


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

    public UpdateClinicalDraftDto() {
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
}
