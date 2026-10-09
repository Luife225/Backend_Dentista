package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.NoSuchElementException;
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
        // Validación de reglas de negocio odontológicas
        validatePatientData(dto, false);

        Clinic clinic;
        if (dto.getClinicaId() != null) {
            clinic = clinicRepository.findById(dto.getClinicaId())
                    .orElseGet(this::getOrCreateDefaultClinic);
        } else {
            clinic = getOrCreateDefaultClinic();
        }

        Patient patient = new Patient();
        patient.setClinica(clinic);
        patient.setNombres(dto.getNombres().trim());
        patient.setApellidos(dto.getApellidos().trim());
        patient.setTipoDocumento(dto.getTipoDocumento() != null ? dto.getTipoDocumento().toUpperCase().trim() : "DNI");
        patient.setNumeroDocumento(dto.getNumeroDocumento().trim());
        patient.setFechaNacimiento(dto.getFechaNacimiento());
        patient.setTelefono(dto.getTelefono() != null ? dto.getTelefono().trim() : null);
        patient.setCorreo(dto.getCorreo() != null ? dto.getCorreo().trim().toLowerCase() : null);
        patient.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        patient.setAlergias(dto.getAlergias());
        patient.setAntecedentesMedicos(dto.getAntecedentesMedicos());
        patient.setMedicamentos(dto.getMedicamentos());
        patient.setCiudad(dto.getCiudad() != null ? dto.getCiudad().trim() : null);
        patient.setTipoSangre(dto.getTipoSangre() != null ? dto.getTipoSangre().trim() : "POR DETERMINAR");
        patient.setSeguro(dto.getSeguro() != null ? dto.getSeguro().trim() : "PARTICULAR");

        Patient saved = patientRepository.save(patient);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public PatientDto getPatientById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));
        return toDto(patient);
    }

    @Transactional
    public PatientDto updatePatient(UUID id, PatientDto dto) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));

        // Validación de reglas si los campos vienen para actualizar
        validatePatientData(dto, true);

        if (dto.getNombres() != null && !dto.getNombres().isBlank()) {
            patient.setNombres(dto.getNombres().trim());
        }
        if (dto.getApellidos() != null && !dto.getApellidos().isBlank()) {
            patient.setApellidos(dto.getApellidos().trim());
        }
        if (dto.getTipoDocumento() != null) {
            patient.setTipoDocumento(dto.getTipoDocumento().toUpperCase().trim());
        }
        if (dto.getNumeroDocumento() != null) {
            patient.setNumeroDocumento(dto.getNumeroDocumento().trim());
        }
        if (dto.getFechaNacimiento() != null) {
            patient.setFechaNacimiento(dto.getFechaNacimiento());
        }
        if (dto.getTelefono() != null) {
            patient.setTelefono(dto.getTelefono().trim());
        }
        if (dto.getCorreo() != null) {
            patient.setCorreo(dto.getCorreo().trim().toLowerCase());
        }
        if (dto.getAlergias() != null) {
            patient.setAlergias(dto.getAlergias());
        }
        if (dto.getAntecedentesMedicos() != null) {
            patient.setAntecedentesMedicos(dto.getAntecedentesMedicos());
        }
        if (dto.getMedicamentos() != null) {
            patient.setMedicamentos(dto.getMedicamentos());
        }
        if (dto.getCiudad() != null) {
            patient.setCiudad(dto.getCiudad().trim());
        }
        if (dto.getTipoSangre() != null) {
            patient.setTipoSangre(dto.getTipoSangre().trim());
        }
        if (dto.getSeguro() != null) {
            patient.setSeguro(dto.getSeguro().trim());
        }

        Patient updated = patientRepository.save(patient);
        return toDto(updated);
    }

    @Transactional
    public PatientDto archivePatient(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));

        if ("ARCHIVADO".equals(patient.getEstado())) {
            throw new IllegalStateException("El paciente ya se encuentra archivado");
        }

        patient.setEstado("ARCHIVADO");
        Patient archived = patientRepository.save(patient);
        return toDto(archived);
    }

    /**
     * Valida reglas de formato de documento, límites de edad y teléfono.
     */
    private void validatePatientData(PatientDto dto, boolean isUpdate) {
        // 1. Validación de Tipo y Número de Documento
        String tipoDoc = dto.getTipoDocumento();
        String numDoc = dto.getNumeroDocumento();

        if (numDoc != null) {
            numDoc = numDoc.trim();
            String tipo = (tipoDoc != null) ? tipoDoc.toUpperCase().trim() : "DNI";

            switch (tipo) {
                case "DNI":
                    if (!numDoc.matches("^\\d{8}$")) {
                        throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos numéricos.");
                    }
                    break;
                case "CE":
                    if (!numDoc.matches("^[a-zA-Z0-9]{9,12}$")) {
                        throw new IllegalArgumentException("El Carné de Extranjería (CE) debe contener entre 9 y 12 caracteres alfanuméricos.");
                    }
                    break;
                case "PASAPORTE":
                    if (!numDoc.matches("^[a-zA-Z0-9]{6,12}$")) {
                        throw new IllegalArgumentException("El Pasaporte debe contener entre 6 y 12 caracteres alfanuméricos.");
                    }
                    break;
                case "CC":
                    if (!numDoc.matches("^\\d{6,10}$")) {
                        throw new IllegalArgumentException("La Cédula de Ciudadanía (CC) debe contener entre 6 y 10 dígitos numéricos.");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Tipo de documento no válido: " + tipo + ". Debe ser DNI, CE, PASAPORTE o CC.");
            }
        } else if (!isUpdate) {
            throw new IllegalArgumentException("El número de documento es obligatorio.");
        }

        // 2. Validación de Fecha de Nacimiento y Rango de Edad (5 a 100 años)
        if (dto.getFechaNacimiento() != null) {
            LocalDate fechaNac = dto.getFechaNacimiento();
            LocalDate hoy = LocalDate.now();

            if (fechaNac.isAfter(hoy)) {
                throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
            }

            int edad = Period.between(fechaNac, hoy).getYears();
            if (edad < 5) {
                throw new IllegalArgumentException("El paciente debe tener al menos 5 años de edad cumplidos para ser registrado en la clínica.");
            }
            if (edad > 100) {
                throw new IllegalArgumentException("La fecha de nacimiento ingresada no es válida (el paciente no puede exceder los 100 años de edad).");
            }
        } else if (!isUpdate) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }

        // 3. Validación de Teléfono (opcional, pero si viene debe tener formato válido)
        if (dto.getTelefono() != null && !dto.getTelefono().isBlank()) {
            String tel = dto.getTelefono().trim();
            if (!tel.matches("^[+]?[0-9\\s\\-]{8,25}$")) {
                throw new IllegalArgumentException("El formato del número de teléfono o celular no es válido (ej: +51 987654321).");
            }
        }
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
        dto.setCiudad(patient.getCiudad());
        dto.setTipoSangre(patient.getTipoSangre());
        dto.setSeguro(patient.getSeguro());
        dto.setFechaCreacion(patient.getFechaCreacion());
        dto.setFechaActualizacion(patient.getFechaActualizacion());
        return dto;
    }
}
