package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClinicalAttentionService {

    private final AtencionClinicaRepository atencionClinicaRepository;
    private final VersionAtencionClinicaRepository versionAtencionClinicaRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public ClinicalAttentionService(AtencionClinicaRepository atencionClinicaRepository,
            VersionAtencionClinicaRepository versionAtencionClinicaRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository) {
        this.atencionClinicaRepository = atencionClinicaRepository;
        this.versionAtencionClinicaRepository = versionAtencionClinicaRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    private UserPrincipal requireAuthorizedOdontologist() {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException(
                    "Acceso restringido: solo odontólogos autorizados pueden acceder o gestionar la historia clínica técnica");
        }
        if (principal.getMembership() == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException(
                    "Acceso denegado: el odontólogo no cuenta con membresía activa en la clínica");
        }
        return principal;
    }

    /**
     * D03: Crea un nuevo encuentro clínico en estado BORRADOR con su versión
     * inicial 1.
     * El principal determina autor, fecha y clínica de forma estricta.
     */
    public ClinicalEncounterResponseDto createDraft(CreateClinicalEncounterDto dto) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        Patient paciente = patientRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + dto.getPacienteId()));

        if (!paciente.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + dto.getPacienteId());
        }

        Appointment cita = null;
        if (dto.getCitaId() != null) {
            cita = appointmentRepository.findById(dto.getCitaId())
                    .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + dto.getCitaId()));

            if (!cita.getClinica().getId().equals(principal.getClinicaId())) {
                throw new NoSuchElementException("Cita no encontrada con ID: " + dto.getCitaId());
            }
            if (!cita.getPaciente().getId().equals(paciente.getId())) {
                throw new IllegalArgumentException("La cita especificada no corresponde al paciente seleccionado");
            }
            if (atencionClinicaRepository.existsByCitaId(dto.getCitaId())) {
                throw new IllegalStateException(
                        "Ya existe una atención clínica registrada para la cita ID: " + dto.getCitaId());
            }
            // Si la atención está asociada a una cita, marcamos la cita como EN_ATENCION
            if (cita != null && !"EN_ATENCION".equals(cita.getEstado())) {
                cita.setEstado("EN_ATENCION");
                appointmentRepository.save(cita);
            }

        }

        AtencionClinica atencion = new AtencionClinica();
        atencion.setClinica(principal.getClinic());
        atencion.setPaciente(paciente);
        atencion.setCita(cita);
        atencion.setOdontologo(principal.getMembership());
        atencion.setEstado("BORRADOR");
        atencion.setFechaCreacion(OffsetDateTime.now());

        AtencionClinica savedAtencion = atencionClinicaRepository.save(atencion);

        VersionAtencionClinica version = new VersionAtencionClinica();
        version.setClinica(principal.getClinic());
        version.setAtencionClinica(savedAtencion);
        version.setNumeroVersion(1);
        version.setVersionAnterior(null);
        version.setFirmadoPor(null);
        version.setEstaConfirmado(false);
        version.setFechaRegistro(OffsetDateTime.now());

        copyClinicalFields(dto, version);

        VersionAtencionClinica savedVersion = versionAtencionClinicaRepository.save(version);
        savedAtencion.getVersiones().add(savedVersion);

        return mapToEncounterDto(savedAtencion);
    }

    /**
     * D03: Modifica el borrador activo de una atención clínica aún no confirmada.
     */
    public ClinicalEncounterResponseDto updateDraft(Long atencionId, UpdateClinicalDraftDto dto) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        AtencionClinica atencion = atencionClinicaRepository.findByIdAndClinicaId(atencionId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Atención clínica no encontrada con ID: " + atencionId));

        if ("CONFIRMADO".equalsIgnoreCase(atencion.getEstado())) {
            throw new IllegalStateException(
                    "La atención clínica está confirmada y es inmutable. Para aplicar cambios debe generar una nueva versión de corrección.");
        }

        VersionAtencionClinica version = versionAtencionClinicaRepository
                .findTopByAtencionClinicaIdOrderByNumeroVersionDesc(atencionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No se encontró versión activa para la atención ID: " + atencionId));

        if (Boolean.TRUE.equals(version.getEstaConfirmado())) {
            throw new IllegalStateException(
                    "La versión activa ya se encuentra confirmada y es inmutable. Genere una nueva versión de corrección.");
        }

        version.setMotivoConsulta(dto.getMotivoConsulta());
        version.setExamenClinico(dto.getExamenClinico());
        version.setDiagnostico(dto.getDiagnostico());
        version.setProcedimientos(dto.getProcedimientos());
        version.setPlanTratamiento(dto.getPlanTratamiento());
        version.setRecetasPrescripciones(dto.getRecetasPrescripciones());
        version.setIndicaciones(dto.getIndicaciones());
        version.setEvolucion(dto.getEvolucion());
        version.setProximaCitaSugerida(dto.getProximaCitaSugerida());
        version.setSignosVitales(dto.getSignosVitales());
        version.setTextoPendienteRevision(dto.getTextoPendienteRevision());
        version.setFechaRegistro(OffsetDateTime.now());

        versionAtencionClinicaRepository.save(version);

        return mapToEncounterDto(atencion);
    }

    /**
     * D04 / G05: Confirma la versión exacta de una atención clínica.
     * Es idempotente frente a reintentos: si la versión ya fue confirmada
     * previamente,
     * retorna la atención confirmada sin duplicar registros ni alterar fechas
     * históricas.
     */
    public ClinicalEncounterResponseDto confirmVersion(Long atencionId, ConfirmVersionDto dto) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        AtencionClinica atencion = atencionClinicaRepository.findByIdAndClinicaId(atencionId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Atención clínica no encontrada con ID: " + atencionId));

        VersionAtencionClinica version;
        if (dto != null && dto.getVersionId() != null) {
            version = versionAtencionClinicaRepository
                    .findByIdAndClinicaId(dto.getVersionId(), principal.getClinicaId())
                    .orElseThrow(
                            () -> new NoSuchElementException("Versión no encontrada con ID: " + dto.getVersionId()));
            if (!version.getAtencionClinica().getId().equals(atencionId)) {
                throw new IllegalArgumentException(
                        "La versión ID " + dto.getVersionId() + " no pertenece a la atención clínica ID " + atencionId);
            }
        } else if (dto != null && dto.getNumeroVersion() != null) {
            version = versionAtencionClinicaRepository
                    .findByAtencionClinicaIdAndNumeroVersion(atencionId, dto.getNumeroVersion())
                    .orElseThrow(() -> new NoSuchElementException("No se encontró la versión número "
                            + dto.getNumeroVersion() + " para la atención ID: " + atencionId));
        } else {
            version = versionAtencionClinicaRepository.findTopByAtencionClinicaIdOrderByNumeroVersionDesc(atencionId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "No se encontraron versiones para la atención ID: " + atencionId));
        }

        // Idempotencia frente a reintento
        if (Boolean.TRUE.equals(version.getEstaConfirmado())) {
            return mapToEncounterDto(atencion);
        }

        OffsetDateTime now = OffsetDateTime.now();
        version.setEstaConfirmado(true);
        version.setFirmadoPor(principal.getMembership());
        version.setFechaConfirmacion(now);
        versionAtencionClinicaRepository.save(version);

        atencion.setEstado("CONFIRMADO");
        atencion.setFechaConfirmacion(now);
        atencionClinicaRepository.save(atencion);

        // nuevo: Sincronización automatica de la cita vinculada
        if (atencion.getCita() != null) {
            Appointment cita = atencion.getCita();
            cita.setEstado("FINALIZADA");
            cita.setEstadoAsistencia("ASISTIO");
            appointmentRepository.save(cita);
        }

        return mapToEncounterDto(atencion);
    }

    /**
     * D06: Corrige creando una nueva versión inmutable que referencia a la versión
     * confirmada anterior.
     * La versión anterior permanece inalterada y trazable.
     */
    public ClinicalEncounterResponseDto createCorrectionVersion(Long atencionId, CreateClinicalVersionDto dto) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        AtencionClinica atencion = atencionClinicaRepository.findByIdAndClinicaId(atencionId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Atención clínica no encontrada con ID: " + atencionId));

        VersionAtencionClinica previousVersion = versionAtencionClinicaRepository
                .findTopByAtencionClinicaIdOrderByNumeroVersionDesc(atencionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No se encontró versión previa para la atención ID: " + atencionId));

        if (!Boolean.TRUE.equals(previousVersion.getEstaConfirmado())) {
            throw new IllegalStateException("La versión actual (número " + previousVersion.getNumeroVersion() +
                    ") aún está en borrador. Confirme la versión vigente antes de generar una nueva corrección.");
        }

        VersionAtencionClinica newVersion = new VersionAtencionClinica();
        newVersion.setClinica(principal.getClinic());
        newVersion.setAtencionClinica(atencion);
        newVersion.setVersionAnterior(previousVersion);
        newVersion.setNumeroVersion(previousVersion.getNumeroVersion() + 1);
        newVersion.setFechaRegistro(OffsetDateTime.now());

        newVersion.setMotivoConsulta(
                dto.getMotivoConsulta() != null ? dto.getMotivoConsulta() : previousVersion.getMotivoConsulta());
        newVersion.setExamenClinico(
                dto.getExamenClinico() != null ? dto.getExamenClinico() : previousVersion.getExamenClinico());
        newVersion
                .setDiagnostico(dto.getDiagnostico() != null ? dto.getDiagnostico() : previousVersion.getDiagnostico());
        newVersion.setProcedimientos(
                dto.getProcedimientos() != null ? dto.getProcedimientos() : previousVersion.getProcedimientos());
        newVersion.setPlanTratamiento(
                dto.getPlanTratamiento() != null ? dto.getPlanTratamiento() : previousVersion.getPlanTratamiento());
        newVersion.setRecetasPrescripciones(dto.getRecetasPrescripciones() != null ? dto.getRecetasPrescripciones()
                : previousVersion.getRecetasPrescripciones());
        newVersion.setIndicaciones(
                dto.getIndicaciones() != null ? dto.getIndicaciones() : previousVersion.getIndicaciones());
        newVersion.setEvolucion(dto.getEvolucion() != null ? dto.getEvolucion() : previousVersion.getEvolucion());
        newVersion.setProximaCitaSugerida(dto.getProximaCitaSugerida() != null ? dto.getProximaCitaSugerida()
                : previousVersion.getProximaCitaSugerida());
        newVersion.setSignosVitales(
                dto.getSignosVitales() != null ? dto.getSignosVitales() : previousVersion.getSignosVitales());
        newVersion.setTextoPendienteRevision(dto.getTextoPendienteRevision() != null ? dto.getTextoPendienteRevision()
                : previousVersion.getTextoPendienteRevision());

        if (Boolean.TRUE.equals(dto.getConfirmarInmediatamente())) {
            OffsetDateTime now = OffsetDateTime.now();
            newVersion.setEstaConfirmado(true);
            newVersion.setFirmadoPor(principal.getMembership());
            newVersion.setFechaConfirmacion(now);
            atencion.setFechaConfirmacion(now);
            atencion.setEstado("CONFIRMADO");
        } else {
            newVersion.setEstaConfirmado(false);
            newVersion.setFirmadoPor(null);
            atencion.setEstado("BORRADOR");
        }

        versionAtencionClinicaRepository.save(newVersion);
        atencionClinicaRepository.save(atencion);

        return mapToEncounterDto(atencion);
    }

    /**
     * D01 / D05: Consulta historial de atenciones clínicas de un paciente en la
     * clínica.
     */
    @Transactional(readOnly = true)
    public List<ClinicalEncounterResponseDto> getEncountersByPatient(Long pacienteId) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        Patient paciente = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));

        if (!paciente.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId);
        }

        List<AtencionClinica> atenciones = atencionClinicaRepository
                .findByPacienteIdAndClinicaIdWithVersions(pacienteId, principal.getClinicaId());

        return atenciones.stream().map(this::mapToEncounterDto).collect(Collectors.toList());
    }

    /**
     * D01 / D05: Consulta ficha de un encuentro clínico por su ID.
     */
    @Transactional(readOnly = true)
    public ClinicalEncounterResponseDto getEncounterById(Long id) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        AtencionClinica atencion = atencionClinicaRepository
                .findByIdAndClinicaIdWithVersions(id, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Atención clínica no encontrada con ID: " + id));

        return mapToEncounterDto(atencion);
    }

    /**
     * D05: Consulta todas las versiones históricas de un encuentro clínico.
     */
    @Transactional(readOnly = true)
    public List<ClinicalVersionResponseDto> getVersionsByEncounterId(Long id) {
        UserPrincipal principal = requireAuthorizedOdontologist();

        AtencionClinica atencion = atencionClinicaRepository.findByIdAndClinicaId(id, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Atención clínica no encontrada con ID: " + id));

        List<VersionAtencionClinica> versiones = versionAtencionClinicaRepository
                .findByAtencionClinicaIdOrderByNumeroVersionAsc(atencion.getId());

        return versiones.stream().map(this::mapToVersionDto).collect(Collectors.toList());
    }

    private void copyClinicalFields(CreateClinicalEncounterDto dto, VersionAtencionClinica version) {
        version.setMotivoConsulta(dto.getMotivoConsulta());
        version.setExamenClinico(dto.getExamenClinico());
        version.setDiagnostico(dto.getDiagnostico());
        version.setProcedimientos(dto.getProcedimientos());
        version.setPlanTratamiento(dto.getPlanTratamiento());
        version.setRecetasPrescripciones(dto.getRecetasPrescripciones());
        version.setIndicaciones(dto.getIndicaciones());
        version.setEvolucion(dto.getEvolucion());
        version.setProximaCitaSugerida(dto.getProximaCitaSugerida());
        version.setSignosVitales(dto.getSignosVitales());
        version.setTextoPendienteRevision(dto.getTextoPendienteRevision());
    }

    private ClinicalEncounterResponseDto mapToEncounterDto(AtencionClinica atencion) {
        ClinicalEncounterResponseDto dto = new ClinicalEncounterResponseDto();
        dto.setId(atencion.getId());
        dto.setClinicaId(atencion.getClinica().getId());
        dto.setPacienteId(atencion.getPaciente().getId());
        dto.setPacienteNombreCompleto(
                atencion.getPaciente().getNombres() + " " + atencion.getPaciente().getApellidos());
        dto.setPacienteNumeroDocumento(atencion.getPaciente().getNumeroDocumento());
        dto.setCitaId(atencion.getCita() != null ? atencion.getCita().getId() : null);
        dto.setOdontologoId(atencion.getOdontologo().getId());
        dto.setOdontologoNombreCompleto(
                atencion.getOdontologo().getUsuario() != null
                        ? atencion.getOdontologo().getUsuario().getNombres() + " "
                                + atencion.getOdontologo().getUsuario().getApellidos()
                        : "Dr. Odontólogo");
        dto.setEstado(atencion.getEstado());
        dto.setFechaCreacion(atencion.getFechaCreacion());
        dto.setFechaConfirmacion(atencion.getFechaConfirmacion());

        List<VersionAtencionClinica> versiones = versionAtencionClinicaRepository
                .findByAtencionClinicaIdOrderByNumeroVersionAsc(atencion.getId());

        dto.setTotalVersiones(versiones.size());
        List<ClinicalVersionResponseDto> versionDtos = versiones.stream()
                .map(this::mapToVersionDto)
                .collect(Collectors.toList());
        dto.setVersiones(versionDtos);

        if (!versionDtos.isEmpty()) {
            dto.setVersionActual(versionDtos.get(versionDtos.size() - 1));
        }

        return dto;
    }

    private ClinicalVersionResponseDto mapToVersionDto(VersionAtencionClinica v) {
        ClinicalVersionResponseDto dto = new ClinicalVersionResponseDto();
        dto.setId(v.getId());
        dto.setClinicaId(v.getClinica().getId());
        dto.setAtencionClinicaId(v.getAtencionClinica().getId());
        dto.setVersionAnteriorId(v.getVersionAnterior() != null ? v.getVersionAnterior().getId() : null);
        dto.setFirmadoPorId(v.getFirmadoPor() != null ? v.getFirmadoPor().getId() : null);
        dto.setFirmadoPorNombre(
                v.getFirmadoPor() != null && v.getFirmadoPor().getUsuario() != null
                        ? v.getFirmadoPor().getUsuario().getNombres() + " "
                                + v.getFirmadoPor().getUsuario().getApellidos()
                        : null);
        dto.setNumeroVersion(v.getNumeroVersion());
        dto.setMotivoConsulta(v.getMotivoConsulta());
        dto.setExamenClinico(v.getExamenClinico());
        dto.setDiagnostico(v.getDiagnostico());
        dto.setProcedimientos(v.getProcedimientos());
        dto.setPlanTratamiento(v.getPlanTratamiento());
        dto.setRecetasPrescripciones(v.getRecetasPrescripciones());
        dto.setIndicaciones(v.getIndicaciones());
        dto.setEvolucion(v.getEvolucion());
        dto.setProximaCitaSugerida(v.getProximaCitaSugerida());
        dto.setSignosVitales(v.getSignosVitales());
        dto.setTextoPendienteRevision(v.getTextoPendienteRevision());
        dto.setEstaConfirmado(v.getEstaConfirmado());
        dto.setFechaRegistro(v.getFechaRegistro());
        dto.setFechaConfirmacion(v.getFechaConfirmacion());
        return dto;
    }
}
