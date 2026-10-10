package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.ConfirmOdontogramaDto;
import pe.edu.utp.coronyx.backend.dto.HallazgoDto;
import pe.edu.utp.coronyx.backend.dto.OdontogramaVersionDto;
import pe.edu.utp.coronyx.backend.dto.SaveOdontogramaDraftDto;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.HallazgoOdontogramaRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.VersionOdontogramaRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class OdontogramService {

    private final VersionOdontogramaRepository versionRepository;
    private final HallazgoOdontogramaRepository hallazgoRepository;
    private final PatientRepository patientRepository;

    public OdontogramService(VersionOdontogramaRepository versionRepository,
                             HallazgoOdontogramaRepository hallazgoRepository,
                             PatientRepository patientRepository) {
        this.versionRepository = versionRepository;
        this.hallazgoRepository = hallazgoRepository;
        this.patientRepository = patientRepository;
    }

    private void validateSecurity(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("No se encontró una sesión activa autorizada");
        }
        if (principal.isSuperAdmin()) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso clínico operativo");
        }
    }

    private Patient validateAndGetPatient(Long pacienteId, UserPrincipal principal) {
        Patient patient = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));
        if (patient.getClinica() == null || !patient.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId);
        }
        if ("PACIENTE".equalsIgnoreCase(principal.getRol())) {
            if (principal.getPatient() == null || !patient.getId().equals(principal.getPatient().getId())) {
                throw new AccessDeniedException("Acceso denegado: un paciente solo puede consultar su propio odontograma");
            }
        }
        return patient;
    }

    /**
     * D07: Consulta la versión vigente de odontograma del paciente.
     * Retorna el borrador activo si existe; si no, la última versión confirmada;
     * si el paciente no tiene ninguna versión aún, crea un borrador inicial v1.
     */
    @Transactional
    public OdontogramaVersionDto getCurrentOdontogram(Long pacienteId, UserPrincipal principal) {
        validateSecurity(principal);
        Patient patient = validateAndGetPatient(pacienteId, principal);

        // 1. Si hay borrador activo, retornarlo
        var borradorOpt = versionRepository.findBorradorByPacienteId(patient.getId());
        if (borradorOpt.isPresent()) {
            return toDto(borradorOpt.get());
        }

        // 2. Si hay versión confirmada, retornarla
        var confirmadaOpt = versionRepository.findLatestConfirmadoByPacienteId(patient.getId());
        if (confirmadaOpt.isPresent()) {
            return toDto(confirmadaOpt.get());
        }

        // 3. Si no existe ninguno y es clínico, inicializar borrador v1 vacío
        if ("ODONTOLOGO".equalsIgnoreCase(principal.getRol()) || "ADMIN_CLINICA".equalsIgnoreCase(principal.getRol())) {
            VersionOdontograma v1 = new VersionOdontograma();
            v1.setClinica(principal.getClinic());
            v1.setPaciente(patient);
            v1.setNumeroVersion(1);
            v1.setEstado("BORRADOR");
            v1.setFechaCreacion(OffsetDateTime.now());
            VersionOdontograma saved = versionRepository.save(v1);
            return toDto(saved);
        }

        // Para paciente sin odontograma aún, retornar DTO vacío
        OdontogramaVersionDto emptyDto = new OdontogramaVersionDto();
        emptyDto.setPacienteId(patient.getId().toString());
        emptyDto.setNumeroVersion(1);
        emptyDto.setEstado("BORRADOR");
        emptyDto.setHallazgos(new ArrayList<>());
        return emptyDto;
    }

    /**
     * Historial de versiones del paciente.
     */
    @Transactional(readOnly = true)
    public List<OdontogramaVersionDto> getHistory(Long pacienteId, UserPrincipal principal) {
        validateSecurity(principal);
        Patient patient = validateAndGetPatient(pacienteId, principal);
        return versionRepository.findByPacienteIdOrderByNumeroVersionDesc(patient.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Guarda o actualiza hallazgos en el borrador activo del odontograma.
     */
    @Transactional
    public OdontogramaVersionDto saveDraft(Long pacienteId, SaveOdontogramaDraftDto dto, UserPrincipal principal) {
        validateSecurity(principal);
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el odontólogo tratante puede editar el odontograma");
        }
        Patient patient = validateAndGetPatient(pacienteId, principal);

        VersionOdontograma version = versionRepository.findBorradorByPacienteId(patient.getId())
                .orElseGet(() -> {
                    int nextVer = versionRepository.findMaxNumeroVersionByPacienteId(patient.getId()) + 1;
                    VersionOdontograma newDraft = new VersionOdontograma();
                    newDraft.setClinica(principal.getClinic());
                    newDraft.setPaciente(patient);
                    newDraft.setNumeroVersion(nextVer);
                    newDraft.setEstado("BORRADOR");
                    newDraft.setFechaCreacion(OffsetDateTime.now());
                    return versionRepository.save(newDraft);
                });

        if ("CONFIRMADO".equalsIgnoreCase(version.getEstado())) {
            throw new IllegalStateException("La versión activa del odontograma ya fue confirmada y es inmutable.");
        }

        // Reemplazar hallazgos del borrador
        version.getHallazgos().clear();
        if (dto.getHallazgos() != null) {
            for (HallazgoDto h : dto.getHallazgos()) {
                HallazgoOdontograma entity = new HallazgoOdontograma(
                        principal.getClinic(),
                        version,
                        h.getCodigoPiezaDental(),
                        h.getCodigoSuperficieCara().toUpperCase(),
                        h.getCodigoCondicion().toUpperCase(),
                        h.getNotaObservacion()
                );
                version.getHallazgos().add(entity);
            }
        }

        VersionOdontograma saved = versionRepository.save(version);
        return toDto(saved);
    }

    /**
     * Confirma la versión exacta del odontograma, haciéndola inmutable.
     * Es idempotente frente a reintentos.
     */
    @Transactional
    public OdontogramaVersionDto confirmVersion(Long versionId, ConfirmOdontogramaDto dto, UserPrincipal principal) {
        validateSecurity(principal);
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo un odontólogo puede confirmar una versión de odontograma");
        }

        VersionOdontograma version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Versión de odontograma no encontrada con ID: " + versionId));

        if (!version.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Versión de odontograma no encontrada");
        }

        // Idempotencia: si ya estaba confirmada, retornar sin mutar
        if ("CONFIRMADO".equalsIgnoreCase(version.getEstado())) {
            return toDto(version);
        }

        version.setEstado("CONFIRMADO");
        version.setFechaConfirmacion(OffsetDateTime.now());
        version.setFirmadoPor(principal.getMembership());

        VersionOdontograma confirmed = versionRepository.save(version);
        return toDto(confirmed);
    }

    /**
     * Crea una nueva versión inmutable a partir de la versión confirmada previa.
     */
    @Transactional
    public OdontogramaVersionDto createCorrectionVersion(Long versionId, SaveOdontogramaDraftDto dto, UserPrincipal principal) {
        validateSecurity(principal);
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo un odontólogo puede generar una nueva versión de odontograma");
        }

        VersionOdontograma prevVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Versión de odontograma no encontrada con ID: " + versionId));

        if (!prevVersion.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Versión de odontograma no encontrada");
        }

        if (!"CONFIRMADO".equalsIgnoreCase(prevVersion.getEstado())) {
            throw new IllegalStateException("La versión previa debe estar confirmada antes de generar una nueva versión.");
        }

        // Si ya hay un borrador pendiente, reutilizarlo o avisar
        var existingDraft = versionRepository.findBorradorByPacienteId(prevVersion.getPaciente().getId());
        if (existingDraft.isPresent()) {
            throw new IllegalStateException("Ya existe un borrador pendiente de confirmación (versión " +
                    existingDraft.get().getNumeroVersion() + ").");
        }

        VersionOdontograma newVersion = new VersionOdontograma();
        newVersion.setClinica(principal.getClinic());
        newVersion.setPaciente(prevVersion.getPaciente());
        newVersion.setVersionAnterior(prevVersion);
        newVersion.setNumeroVersion(prevVersion.getNumeroVersion() + 1);
        newVersion.setEstado("BORRADOR");
        newVersion.setFechaCreacion(OffsetDateTime.now());

        if (dto != null && dto.getHallazgos() != null) {
            for (HallazgoDto h : dto.getHallazgos()) {
                HallazgoOdontograma entity = new HallazgoOdontograma(
                        principal.getClinic(),
                        newVersion,
                        h.getCodigoPiezaDental(),
                        h.getCodigoSuperficieCara().toUpperCase(),
                        h.getCodigoCondicion().toUpperCase(),
                        h.getNotaObservacion()
                );
                newVersion.getHallazgos().add(entity);
            }
        }

        VersionOdontograma saved = versionRepository.save(newVersion);
        return toDto(saved);
    }

    @Transactional
    public OdontogramaVersionDto updateDraftByVersion(Long versionId, SaveOdontogramaDraftDto dto, UserPrincipal principal) {
        validateSecurity(principal);
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el odontólogo tratante puede editar el odontograma");
        }

        VersionOdontograma version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Versión de odontograma no encontrada con ID: " + versionId));

        if (!version.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Versión de odontograma no encontrada");
        }

        if ("CONFIRMADO".equalsIgnoreCase(version.getEstado()) || "CONFIRMADA".equalsIgnoreCase(version.getEstado())) {
            throw new IllegalStateException("La versión activa del odontograma ya fue confirmada y es inmutable.");
        }

        version.getHallazgos().clear();
        if (dto != null && dto.getHallazgos() != null) {
            for (HallazgoDto h : dto.getHallazgos()) {
                HallazgoOdontograma entity = new HallazgoOdontograma(
                        principal.getClinic(),
                        version,
                        h.getCodigoPiezaDental(),
                        h.getCodigoSuperficieCara().toUpperCase(),
                        h.getCodigoCondicion().toUpperCase(),
                        h.getNotaObservacion()
                );
                version.getHallazgos().add(entity);
            }
        }

        VersionOdontograma saved = versionRepository.save(version);
        return toDto(saved);
    }

    @Transactional
    public OdontogramaVersionDto createNewVersionForPatient(Long pacienteId, SaveOdontogramaDraftDto dto, UserPrincipal principal) {
        validateSecurity(principal);
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo el odontólogo tratante puede generar una nueva versión");
        }
        Patient patient = validateAndGetPatient(pacienteId, principal);

        var existingDraft = versionRepository.findBorradorByPacienteId(patient.getId());
        if (existingDraft.isPresent()) {
            return toDto(existingDraft.get());
        }

        int nextVer = versionRepository.findMaxNumeroVersionByPacienteId(patient.getId()) + 1;
        VersionOdontograma lastConfirmed = versionRepository.findLatestConfirmadoByPacienteId(patient.getId()).orElse(null);

        VersionOdontograma newVersion = new VersionOdontograma();
        newVersion.setClinica(principal.getClinic());
        newVersion.setPaciente(patient);
        newVersion.setVersionAnterior(lastConfirmed);
        newVersion.setNumeroVersion(nextVer);
        newVersion.setEstado("BORRADOR");
        newVersion.setFechaCreacion(OffsetDateTime.now());

        if (dto != null && dto.getHallazgos() != null && !dto.getHallazgos().isEmpty()) {
            for (HallazgoDto h : dto.getHallazgos()) {
                HallazgoOdontograma entity = new HallazgoOdontograma(
                        principal.getClinic(),
                        newVersion,
                        h.getCodigoPiezaDental(),
                        h.getCodigoSuperficieCara().toUpperCase(),
                        h.getCodigoCondicion().toUpperCase(),
                        h.getNotaObservacion()
                );
                newVersion.getHallazgos().add(entity);
            }
        } else if (lastConfirmed != null) {
            for (HallazgoOdontograma oldH : lastConfirmed.getHallazgos()) {
                HallazgoOdontograma copied = new HallazgoOdontograma(
                        principal.getClinic(),
                        newVersion,
                        oldH.getCodigoPiezaDental(),
                        oldH.getCodigoSuperficieCara(),
                        oldH.getCodigoCondicion(),
                        oldH.getNotaObservacion()
                );
                newVersion.getHallazgos().add(copied);
            }
        }

        VersionOdontograma saved = versionRepository.save(newVersion);
        return toDto(saved);
    }

    private OdontogramaVersionDto toDto(VersionOdontograma v) {
        OdontogramaVersionDto dto = new OdontogramaVersionDto();
        dto.setId(v.getId() != null ? v.getId().toString() : null);
        dto.setPacienteId(v.getPaciente() != null && v.getPaciente().getId() != null ? v.getPaciente().getId().toString() : null);
        dto.setNumeroVersion(v.getNumeroVersion());
        dto.setEstado(v.getEstado());
        dto.setFechaCreacion(v.getFechaCreacion());
        dto.setFechaConfirmacion(v.getFechaConfirmacion());

        if (v.getFirmadoPor() != null && v.getFirmadoPor().getUsuario() != null) {
            dto.setFirmadoPorId(v.getFirmadoPor().getId().toString());
            dto.setFirmadoPorNombre("Dr. " + v.getFirmadoPor().getUsuario().getNombres() + " " + v.getFirmadoPor().getUsuario().getApellidos());
        }

        List<HallazgoDto> hallazgosList = new ArrayList<>();
        if (v.getHallazgos() != null) {
            for (HallazgoOdontograma h : v.getHallazgos()) {
                hallazgosList.add(new HallazgoDto(
                        h.getId() != null ? h.getId().toString() : null,
                        h.getCodigoPiezaDental(),
                        h.getCodigoSuperficieCara(),
                        h.getCodigoCondicion(),
                        h.getNotaObservacion()
                ));
            }
        }
        dto.setHallazgos(hallazgosList);
        return dto;
    }
}
