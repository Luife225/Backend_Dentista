package pe.edu.utp.coronyx.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class DashboardSummaryDto {

    private int totalCitasHoy;
    private int citasConfirmadasHoy;
    private int citasPendientesHoy;
    private int citasAtendidasHoy;
    private int citasCanceladasHoy;
    private long totalPacientes;
    private String fechaHoyLima;
    private AppointmentResponseDto proximaCita;
    private List<AppointmentResponseDto> citasHoy = new ArrayList<>();

    public DashboardSummaryDto() {
    }

    public int getTotalCitasHoy() {
        return totalCitasHoy;
    }

    public void setTotalCitasHoy(int totalCitasHoy) {
        this.totalCitasHoy = totalCitasHoy;
    }

    public int getCitasConfirmadasHoy() {
        return citasConfirmadasHoy;
    }

    public void setCitasConfirmadasHoy(int citasConfirmadasHoy) {
        this.citasConfirmadasHoy = citasConfirmadasHoy;
    }

    public int getCitasPendientesHoy() {
        return citasPendientesHoy;
    }

    public void setCitasPendientesHoy(int citasPendientesHoy) {
        this.citasPendientesHoy = citasPendientesHoy;
    }

    public int getCitasAtendidasHoy() {
        return citasAtendidasHoy;
    }

    public void setCitasAtendidasHoy(int citasAtendidasHoy) {
        this.citasAtendidasHoy = citasAtendidasHoy;
    }

    public int getCitasCanceladasHoy() {
        return citasCanceladasHoy;
    }

    public void setCitasCanceladasHoy(int citasCanceladasHoy) {
        this.citasCanceladasHoy = citasCanceladasHoy;
    }

    public long getTotalPacientes() {
        return totalPacientes;
    }

    public void setTotalPacientes(long totalPacientes) {
        this.totalPacientes = totalPacientes;
    }

    public String getFechaHoyLima() {
        return fechaHoyLima;
    }

    public void setFechaHoyLima(String fechaHoyLima) {
        this.fechaHoyLima = fechaHoyLima;
    }

    public AppointmentResponseDto getProximaCita() {
        return proximaCita;
    }

    public void setProximaCita(AppointmentResponseDto proximaCita) {
        this.proximaCita = proximaCita;
    }

    public List<AppointmentResponseDto> getCitasHoy() {
        return citasHoy;
    }

    public void setCitasHoy(List<AppointmentResponseDto> citasHoy) {
        this.citasHoy = citasHoy;
    }
}
