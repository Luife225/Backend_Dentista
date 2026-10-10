package pe.edu.utp.coronyx.backend.dto;

public class ConfirmOdontogramaDto {

    private String notas;

    public ConfirmOdontogramaDto() {
    }

    public ConfirmOdontogramaDto(String notas) {
        this.notas = notas;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }
}
