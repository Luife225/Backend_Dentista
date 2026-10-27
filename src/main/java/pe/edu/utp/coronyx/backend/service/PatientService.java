package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.PatientAdminDto;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final PatientAccountService patientAccounts;

    public PatientService(PatientRepository patientRepository,
                          ClinicRepository clinicRepository,
                          UserRepository userRepository, PatientAccountService patientAccounts) {
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
        this.patientAccounts = patientAccounts;
    }

    @Transactional(readOnly = true)
    public List<PatientDto> getAllPatients(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin() && principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo a expedientes clínicos");
        }
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no tiene autorización para listar expedientes de la clínica");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        Long clinicaId = principal.getClinicaId();
        boolean isClinical = "ODONTOLOGO".equalsIgnoreCase(principal.getRol());

        return patientRepository.findByClinicaIdOrderByFechaCreacionDesc(clinicaId)
                .stream()
                .map(p -> toDto(p, isClinical))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientDto getPatientById(Long id, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin() && principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo a expedientes clínicos");
        }

        // Regla para rol PACIENTE: solo puede consultar su propio expediente
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPacienteId() == null || !principal.getPacienteId().equals(id)) {
                throw new AccessDeniedException("Acceso denegado: no tiene autorización para consultar expedientes de otros pacientes");
            }
            Patient patient = patientRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));
            return toDto(patient, true);
        }

        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        // Aislamiento multi-sede: el paciente debe pertenecer a la clínica activa del usuario
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));

        if (!patient.getClinica().getId().equals(principal.getClinicaId())) {
            // Política de no exposición/ocultación de existencia entre sedes distintas
            throw new NoSuchElementException("Paciente no encontrado con ID: " + id);
        }

        boolean isClinical = "ODONTOLOGO".equalsIgnoreCase(principal.getRol());
        return toDto(patient, isClinical);
    }

    @Transactional(readOnly = true)
    public PatientDto getMyPatientProfile(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (!"PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el rol PACIENTE posee un perfil directo de paciente");
        }
        Long pacienteId = principal.getPacienteId();
        if (pacienteId != null) {
            Patient patient = patientRepository.findById(pacienteId)
                    .orElseThrow(() -> new NoSuchElementException("Ficha de paciente no encontrada para ID: " + pacienteId));
            return toDto(patient, true);
        }
        if (principal.getUserId() != null) {
            Patient patient = patientRepository.findFirstByUsuarioId(principal.getUserId())
                    .orElseThrow(() -> new NoSuchElementException("No se encontró ficha de paciente vinculada a su cuenta de usuario"));
            return toDto(patient, true);
        }
        throw new NoSuchElementException("No se encontró ficha de paciente asociada a la sesión");
    }

    /**
     * V14: Actualización de datos de perfil propios por el paciente autenticado.
     * Permite modificar únicamente: telefono, ciudad, direccion.
     * Rechaza cualquier intento de alterar campos clínicos confidenciales o datos de identidad.
     */
    @Transactional
    public PatientDto updateMyPatientProfile(pe.edu.utp.coronyx.backend.dto.UpdateMyProfileDto dto, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (!"PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo un paciente puede actualizar su perfil propio");
        }

        Patient patient = null;
        if (principal.getPacienteId() != null) {
            patient = patientRepository.findById(principal.getPacienteId()).orElse(null);
        }
        if (patient == null && principal.getUserId() != null) {
            patient = patientRepository.findFirstByUsuarioId(principal.getUserId()).orElse(null);
        }
        if (patient == null) {
            throw new NoSuchElementException("No se encontró expediente de paciente asociado a su cuenta");
        }

        // Negativos: rechazar intentos de mutación de campos clínicos protegidos
        if (dto.getAlergias() != null && !dto.getAlergias().equals(patient.getAlergias())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar sus alergias clínicas");
        }
        if (dto.getAntecedentesMedicos() != null && !dto.getAntecedentesMedicos().equals(patient.getAntecedentesMedicos())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar sus antecedentes médicos");
        }
        if (dto.getMedicamentos() != null && !dto.getMedicamentos().equals(patient.getMedicamentos())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar sus medicamentos clínicos");
        }
        if (dto.getEstado() != null && !dto.getEstado().equals(patient.getEstado())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar el estado de su expediente");
        }
        if (dto.getTipoDocumento() != null && !dto.getTipoDocumento().equals(patient.getTipoDocumento())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar su documento de identidad");
        }
        if (dto.getNumeroDocumento() != null && !dto.getNumeroDocumento().equals(patient.getNumeroDocumento())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no puede modificar su número de documento");
        }

        if (dto.getTelefono() != null) {
            patient.setTelefono(dto.getTelefono().trim());
        }
        if (dto.getCiudad() != null) {
            patient.setCiudad(dto.getCiudad().trim());
        }
        if (dto.getDireccion() != null) {
            patient.setDireccion(dto.getDireccion().trim());
        }

        Patient saved = patientRepository.save(patient);
        return toDto(saved, true);
    }

    @Transactional
    public PatientDto createPatient(PatientDto dto, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin() && principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo para dar de alta pacientes");
        }
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no tiene autorización para dar de alta otros pacientes");
        }
        if (principal.getClinic() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica operativa asignada");
        }

        // Validación de reglas de formato y edad
        validatePatientData(dto, false);

        // La clínica proviene estrictamente del principal validado, sin fallbacks inventados
        Clinic clinic = principal.getClinic();

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

        boolean isClinical = "ODONTOLOGO".equalsIgnoreCase(principal.getRol());
        if (isClinical) {
            patient.setAlergias(dto.getAlergias());
            patient.setAntecedentesMedicos(dto.getAntecedentesMedicos());
            patient.setMedicamentos(dto.getMedicamentos());
        }

        patient.setCiudad(dto.getCiudad() != null ? dto.getCiudad().trim() : null);
        patient.setTipoSangre(dto.getTipoSangre() != null ? dto.getTipoSangre().trim() : "POR DETERMINAR");
        patient.setSeguro(dto.getSeguro() != null ? dto.getSeguro().trim() : "PARTICULAR");

        // Vinculación explícita de cuenta de paciente sin equiparar paciente.id a usuario.id
        if (dto.getUsuarioId() != null && !dto.getUsuarioId().isBlank()) {
            patientAccounts.link(patient, patientAccounts.parseUserId(dto.getUsuarioId()), principal);
        }

        Patient saved = patientRepository.save(patient);
        return toDto(saved, isClinical);
    }

    @Transactional
    public PatientDto updatePatient(Long id, PatientDto dto, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin() && principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo para modificar pacientes");
        }
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: un paciente no tiene autorización para modificar expedientes");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        Patient patient = patientRepository.findLockedById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));

        if (!patient.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + id);
        }

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

        boolean isClinical = "ODONTOLOGO".equalsIgnoreCase(principal.getRol());
        if (isClinical) {
            if (dto.getAlergias() != null) {
                patient.setAlergias(dto.getAlergias());
            }
            if (dto.getAntecedentesMedicos() != null) {
                patient.setAntecedentesMedicos(dto.getAntecedentesMedicos());
            }
            if (dto.getMedicamentos() != null) {
                patient.setMedicamentos(dto.getMedicamentos());
            }
            if (principal.getMembership() != null) {
                patient.setMedicoActualizo(principal.getMembership());
            }
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
        if (dto.getEstado() != null) {
            patient.setEstado(dto.getEstado());
        }

        if (dto.getUsuarioId() != null) {
            Long targetId = patientAccounts.parseUserId(dto.getUsuarioId());
            Long currentId = patient.getUsuario() == null ? null : patient.getUsuario().getId();
            if (!java.util.Objects.equals(targetId, currentId)) {
                patientAccounts.link(patient, targetId, principal);
            }
        }

        Patient updated = patientRepository.save(patient);
        return toDto(updated, isClinical);
    }

    @Transactional
    public PatientDto archivePatient(Long id, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol()) && !"SUPER_ADMIN".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo ADMIN_CLINICA o SUPER_ADMIN tiene autorización para archivar expedientes");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + id));

        if (!patient.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + id);
        }

        if ("ARCHIVADO".equals(patient.getEstado())) {
            throw new IllegalStateException("El paciente ya se encuentra archivado");
        }

        patient.setEstado("ARCHIVADO");
        Patient archived = patientRepository.save(patient);
        return toDto(archived, false);
    }

    private void validatePatientData(PatientDto dto, boolean isUpdate) {
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

        if (dto.getFechaNacimiento() != null) {
            LocalDate fechaNac = dto.getFechaNacimiento();
            LocalDate hoy = LocalDate.now();

            if (fechaNac.isAfter(hoy)) {
                throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
            }
            if (fechaNac.isBefore(LocalDate.of(1900, 1, 1))) {
                throw new IllegalArgumentException("La fecha de nacimiento ingresada no es válida.");
            }
        } else if (!isUpdate) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }

        if (dto.getTelefono() != null && !dto.getTelefono().isBlank()) {
            String tel = dto.getTelefono().trim();
            if (!tel.matches("^[+]?[0-9\\s\\-]{8,25}$")) {
                throw new IllegalArgumentException("El formato del número de teléfono o celular no es válido (ej: +51 987654321).");
            }
        }
    }

    public PatientDto toDto(Patient patient) {
        return toDto(patient, true);
    }

    public PatientDto toDto(Patient patient, boolean isClinical) {
        PatientDto dto = new PatientDto();
        dto.setId(patient.getId() != null ? patient.getId().toString() : null);
        dto.setClinicaId(patient.getClinica() != null && patient.getClinica().getId() != null ? patient.getClinica().getId().toString() : null);
        dto.setUsuarioId(patient.getUsuario() != null && patient.getUsuario().getId() != null ? patient.getUsuario().getId().toString() : null);
        dto.setNombres(patient.getNombres());
        dto.setApellidos(patient.getApellidos());
        dto.setTipoDocumento(patient.getTipoDocumento());
        dto.setNumeroDocumento(patient.getNumeroDocumento());
        dto.setFechaNacimiento(patient.getFechaNacimiento());
        dto.setTelefono(patient.getTelefono());
        dto.setCorreo(patient.getCorreo());
        dto.setEstado(patient.getEstado());

        // Aislamiento de datos clínicos para DTO administrativo
        if (isClinical) {
            dto.setAlergias(patient.getAlergias());
            dto.setAntecedentesMedicos(patient.getAntecedentesMedicos());
            dto.setMedicamentos(patient.getMedicamentos());
        } else {
            dto.setAlergias(null);
            dto.setAntecedentesMedicos(null);
            dto.setMedicamentos(null);
        }

        dto.setCiudad(patient.getCiudad());
        dto.setDireccion(patient.getDireccion());
        dto.setTipoSangre(patient.getTipoSangre());
        dto.setSeguro(patient.getSeguro());
        dto.setFechaCreacion(patient.getFechaCreacion());
        dto.setFechaActualizacion(patient.getFechaActualizacion());
        return dto;
    }

    public PatientAdminDto toAdminDto(Patient patient) {
        PatientAdminDto dto = new PatientAdminDto();
        dto.setId(patient.getId() != null ? patient.getId().toString() : null);
        dto.setClinicaId(patient.getClinica() != null && patient.getClinica().getId() != null ? patient.getClinica().getId().toString() : null);
        dto.setUsuarioId(patient.getUsuario() != null && patient.getUsuario().getId() != null ? patient.getUsuario().getId().toString() : null);
        dto.setNombres(patient.getNombres());
        dto.setApellidos(patient.getApellidos());
        dto.setTipoDocumento(patient.getTipoDocumento());
        dto.setNumeroDocumento(patient.getNumeroDocumento());
        dto.setFechaNacimiento(patient.getFechaNacimiento());
        dto.setTelefono(patient.getTelefono());
        dto.setCorreo(patient.getCorreo());
        dto.setEstado(patient.getEstado());
        dto.setCiudad(patient.getCiudad());
        dto.setSeguro(patient.getSeguro());
        dto.setFechaCreacion(patient.getFechaCreacion());
        dto.setFechaActualizacion(patient.getFechaActualizacion());
        return dto;
    }

    @Transactional
    public PatientDto linkUserAccount(Long patientId, Long usuarioId, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol()) && !"RECEPCIONISTA".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo personal administrativo puede vincular cuentas de usuario a pacientes");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        Patient patient = patientRepository.findLockedById(patientId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + patientId));

        if (!patient.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + patientId);
        }

        patientAccounts.link(patient, usuarioId, principal);

        Patient updated = patientRepository.save(patient);
        boolean isClinical = "ODONTOLOGO".equalsIgnoreCase(principal.getRol());
        return toDto(updated, isClinical);
    }

    @Transactional(readOnly = true)
    public List<PatientAdminDto> getAllAdministrativePatients(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if ((principal.isSuperAdmin() && principal.getClinicaId() == null) || "PACIENTE".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: no autorizado para consultar la lista administrativa de pacientes");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el usuario no tiene una clínica activa asignada");
        }

        return patientRepository.findByClinicaIdOrderByFechaCreacionDesc(principal.getClinicaId())
                .stream()
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }
}
