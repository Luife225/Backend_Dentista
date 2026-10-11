package pe.edu.utp.coronyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HallazgoDto {

    private String id;

    @NotBlank(message = "El código de pieza dental es obligatorio")
    @Size(max = 4)
    private String codigoPiezaDental;

    @NotBlank(message = "El código de superficie o cara es obligatorio")
    @Size(max = 12)
    private String codigoSuperficieCara; // MESIAL, DISTAL, OCLUSAL, VESTIBULAR, LINGUAL, GENERAL

    @NotBlank(message = "El código de condición es obligatorio")
    @Size(max = 40)
    private String codigoCondicion; // CARIES, RESTAURACION, CORONA, ENDODONCIA, AUSENTE, FRACTURA, IMPLANTE, SANO

    private String notaObservacion;

    public HallazgoDto() {
    }

    public HallazgoDto(String id, String codigoPiezaDental, String codigoSuperficieCara, String codigoCondicion, String notaObservacion) {
        this.id = id;
        this.codigoPiezaDental = codigoPiezaDental;
        this.codigoSuperficieCara = codigoSuperficieCara;
        this.codigoCondicion = codigoCondicion;
        this.notaObservacion = notaObservacion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCodigoPiezaDental() {
        return codigoPiezaDental;
    }

    public void setCodigoPiezaDental(String codigoPiezaDental) {
        this.codigoPiezaDental = codigoPiezaDental;
    }

    public String getCodigoSuperficieCara() {
        return codigoSuperficieCara;
    }

    public void setCodigoSuperficieCara(String codigoSuperficieCara) {
        this.codigoSuperficieCara = codigoSuperficieCara;
    }

    public String getCodigoCondicion() {
        return codigoCondicion;
    }

    public void setCodigoCondicion(String codigoCondicion) {
        this.codigoCondicion = codigoCondicion;
    }

    public String getNotaObservacion() {
        return notaObservacion;
    }

    public void setNotaObservacion(String notaObservacion) {
        this.notaObservacion = notaObservacion;
    }
}
