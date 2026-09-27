package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;

    public PatientService(PatientRepository patientRepository, ClinicRepository clinicRepository) {
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional
    public Clinic getOrCreateDefaultClinic() {
        return clinicRepository.findFirstByOrderByFechaCreacionAsc()
                .orElseGet(() -> {
                    Clinic defaultClinic = new Clinic(
                            "Clínica Dental Coronyx - Sede Central",
                            "Coronyx Dental S.A.C.",
                            "America/Lima",
                            "ABIERTO"
                    );
                    return clinicRepository.save(defaultClinic);
                });
    }

    @Transactional(readOnly = true)
    public List<PatientDto> getAllPatients() {
        return patientRepository.findAllByOrderByFechaCreacionDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PatientDto createPatient(PatientDto dto) {
        Clinic clinic;
        if (dto.getClinicaId() != null) {
            clinic = clinicRepository.findById(dto.getClinicaId())
                    .orElseGet(this::getOrCreateDefaultClinic);
        } else {
            clinic = getOrCreateDefaultClinic();
        }

        Patient patient = new Patient();
        patient.setClinica(clinic);
        patient.setNombres(dto.getNombres());
        patient.setApellidos(dto.getApellidos());
        patient.setTipoDocumento(dto.getTipoDocumento() != null ? dto.getTipoDocumento() : "DNI");
        patient.setNumeroDocumento(dto.getNumeroDocumento());
        patient.setFechaNacimiento(dto.getFechaNacimiento());
        patient.setTelefono(dto.getTelefono());
        patient.setCorreo(dto.getCorreo());
        patient.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        patient.setAlergias(dto.getAlergias());
        patient.setAntecedentesMedicos(dto.getAntecedentesMedicos());
        patient.setMedicamentos(dto.getMedicamentos());

        Patient saved = patientRepository.save(patient);
        return toDto(saved);
    }

    public PatientDto toDto(Patient patient) {
        PatientDto dto = new PatientDto();
        dto.setId(patient.getId());
        dto.setClinicaId(patient.getClinica() != null ? patient.getClinica().getId() : null);
        dto.setNombres(patient.getNombres());
        dto.setApellidos(patient.getApellidos());
        dto.setTipoDocumento(patient.getTipoDocumento());
        dto.setNumeroDocumento(patient.getNumeroDocumento());
        dto.setFechaNacimiento(patient.getFechaNacimiento());
        dto.setTelefono(patient.getTelefono());
        dto.setCorreo(patient.getCorreo());
        dto.setEstado(patient.getEstado());
        dto.setAlergias(patient.getAlergias());
        dto.setAntecedentesMedicos(patient.getAntecedentesMedicos());
        dto.setMedicamentos(patient.getMedicamentos());
        dto.setFechaCreacion(patient.getFechaCreacion());
        dto.setFechaActualizacion(patient.getFechaActualizacion());
        return dto;
    }
}
