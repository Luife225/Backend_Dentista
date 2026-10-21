package pe.edu.utp.coronyx.backend.dto;

public class SlotDto {

    private String inicio; // e.g. "09:00" o ISO
    private String fin;    // e.g. "09:30" o ISO
    private boolean disponible;
    private String motivoNoDisponible;

    public SlotDto() {
    }

    public SlotDto(String inicio, String fin, boolean disponible, String motivoNoDisponible) {
        this.inicio = inicio;
        this.fin = fin;
        this.disponible = disponible;
        this.motivoNoDisponible = motivoNoDisponible;
    }

    public String getInicio() {
        return inicio;
    }

    public void setInicio(String inicio) {
        this.inicio = inicio;
    }

    public String getFin() {
        return fin;
    }

    public void setFin(String fin) {
        this.fin = fin;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public String getMotivoNoDisponible() {
        return motivoNoDisponible;
    }

    public void setMotivoNoDisponible(String motivoNoDisponible) {
        this.motivoNoDisponible = motivoNoDisponible;
    }
}
