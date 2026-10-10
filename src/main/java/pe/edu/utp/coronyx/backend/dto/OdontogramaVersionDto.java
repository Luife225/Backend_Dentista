package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class OdontogramaVersionDto {

    private String id;
    private String pacienteId;
    private Integer numeroVersion;
    private String estado; // BORRADOR, CONFIRMADO
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaConfirmacion;
    private String firmadoPorId;
    private String firmadoPorNombre;
    private List<HallazgoDto> hallazgos = new ArrayList<>();

    public OdontogramaVersionDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public Integer getNumeroVersion() {
        return numeroVersion;
    }

    public void setNumeroVersion(Integer numeroVersion) {
        this.numeroVersion = numeroVersion;
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

    public String getFirmadoPorId() {
        return firmadoPorId;
    }

    public void setFirmadoPorId(String firmadoPorId) {
        this.firmadoPorId = firmadoPorId;
    }

    public String getFirmadoPorNombre() {
        return firmadoPorNombre;
    }

    public void setFirmadoPorNombre(String firmadoPorNombre) {
        this.firmadoPorNombre = firmadoPorNombre;
    }

    public List<HallazgoDto> getHallazgos() {
        return hallazgos;
    }

    public void setHallazgos(List<HallazgoDto> hallazgos) {
        this.hallazgos = hallazgos;
    }
}
