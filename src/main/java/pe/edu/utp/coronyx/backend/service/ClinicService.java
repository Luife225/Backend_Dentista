package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.ClinicDto;
import pe.edu.utp.coronyx.backend.dto.UpdateClinicDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.util.NoSuchElementException;

@Service
public class ClinicService {

    private final ClinicRepository clinicRepository;

    public ClinicService(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("No se encontró una sesión activa con clínica asignada");
        }
    }

    @Transactional(readOnly = true)
    public ClinicDto getClinicProfile(UserPrincipal principal) {
        validatePrincipal(principal);
        Clinic clinic = clinicRepository.findById(principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Clínica no encontrada con ID: " + principal.getClinicaId()));
        return toDto(clinic);
    }

    @Transactional
    public ClinicDto updateClinicProfile(UpdateClinicDto dto, UserPrincipal principal) {
        validatePrincipal(principal);
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el administrador puede modificar los datos del consultorio");
        }

        Clinic clinic = clinicRepository.findById(principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Clínica no encontrada con ID: " + principal.getClinicaId()));

        if (dto.getNombre() != null && !dto.getNombre().isBlank()) {
            clinic.setNombre(dto.getNombre().trim());
        }
        if (dto.getRazonSocial() != null) {
            clinic.setRazonSocial(dto.getRazonSocial().trim());
        }
        if (dto.getZonaHoraria() != null && !dto.getZonaHoraria().isBlank()) {
            clinic.setZonaHoraria(dto.getZonaHoraria().trim());
        }
        if (dto.getRuc() != null) {
            clinic.setRuc(dto.getRuc().trim());
        }
        if (dto.getTelefono() != null) {
            clinic.setTelefono(dto.getTelefono().trim());
        }
        if (dto.getCorreo() != null) {
            clinic.setCorreo(dto.getCorreo().trim());
        }
        if (dto.getCiudad() != null) {
            clinic.setCiudad(dto.getCiudad().trim());
        }
        if (dto.getDireccion() != null) {
            clinic.setDireccion(dto.getDireccion().trim());
        }

        Clinic saved = clinicRepository.save(clinic);
        return toDto(saved);
    }

    private ClinicDto toDto(Clinic c) {
        ClinicDto dto = new ClinicDto();
        dto.setId(c.getId() != null ? c.getId().toString() : null);
        dto.setNombre(c.getNombre());
        dto.setRazonSocial(c.getRazonSocial());
        dto.setZonaHoraria(c.getZonaHoraria());
        dto.setEstado(c.getEstado());
        dto.setRuc(c.getRuc());
        dto.setTelefono(c.getTelefono());
        dto.setCorreo(c.getCorreo());
        dto.setCiudad(c.getCiudad());
        dto.setDireccion(c.getDireccion());
        dto.setFechaCreacion(c.getFechaCreacion());
        dto.setFechaActualizacion(c.getFechaActualizacion());
        return dto;
    }
}
