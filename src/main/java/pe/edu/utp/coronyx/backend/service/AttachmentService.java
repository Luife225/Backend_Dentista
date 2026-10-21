package pe.edu.utp.coronyx.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.coronyx.backend.dto.AttachmentResponseDto;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.storage.StorageService;

import java.util.*;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private static final Set<String> ALLOWED_TIPOS_ARCHIVO = Set.of(
            "RADIOGRAFIA", "FOTOGRAFIA", "CONSENTIMIENTO", "LABORATORIO",
            "DOCUMENTO", "INFORME", "RECETA", "OTRO"
    );

    private final AttachmentRepository attachmentRepository;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             PatientRepository patientRepository,
                             ClinicRepository clinicRepository,
                             UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             StorageService storageService,
                             NotificationService notificationService) {
        this.attachmentRepository = attachmentRepository;
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
    }

    /**
     * Carga de archivo binario con compensación atómica ante fallos de base de datos.
     * Permite a ODONTOLOGO, ADMIN_CLINICA, RECEPCIONISTA y al propio PACIENTE (P05).
     */
    public AttachmentResponseDto upload(MultipartFile file,
                                        Long pacienteId,
                                        String tipoArchivo,
                                        String regionAnatomica,
                                        Long citaId,
                                        Boolean esVisiblePaciente,
                                        UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no válida o falta clínica activa.");
        }

        String userRol = principal.getRol();
        if ("PACIENTE".equalsIgnoreCase(userRol)) {
            Long userPacienteId = principal.getPacienteId();
            if (userPacienteId == null || !userPacienteId.equals(pacienteId)) {
                throw new AccessDeniedException("No tiene autorización para cargar archivos al expediente de otro paciente.");
            }
            // Los archivos subidos por el propio paciente son visibles para él de forma predeterminada
            esVisiblePaciente = true;
        } else if (!"ODONTOLOGO".equalsIgnoreCase(userRol) && !isAdministrative(userRol)) {
            throw new AccessDeniedException("Rol no autorizado para cargar archivos en esta clínica.");
        }

        Long clinicaId = principal.getClinicaId();

        // 1. Validar archivo real y tamaño real (máx 25 MB)
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("El archivo binario no puede estar vacío y debe tener un tamaño real mayor a 0 bytes.");
        }
        if (file.getSize() > 25 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 25MB.");
        }

        // 2. Validar paciente en la misma clínica
        Patient paciente = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));
        if (paciente.getClinica() == null || !paciente.getClinica().getId().equals(clinicaId)) {
            throw new NoSuchElementException("El paciente no pertenece a la clínica de la sesión.");
        }

        // 3. Validar tipo de archivo
        String tipoFinal = (tipoArchivo != null && !tipoArchivo.isBlank())
                ? tipoArchivo.trim().toUpperCase()
                : "DOCUMENTO";
        if (!ALLOWED_TIPOS_ARCHIVO.contains(tipoFinal)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido: " + tipoFinal +
                    ". Tipos válidos: " + ALLOWED_TIPOS_ARCHIVO);
        }

        if (isAdministrative(userRol) && !"DOCUMENTO".equals(tipoFinal)) {
            throw new AccessDeniedException("El personal administrativo solo puede subir documentos administrativos");
        }
        // 4. Validar cita si se proporciona
        Appointment cita = null;
        if (citaId != null) {
            cita = appointmentRepository.findById(citaId)
                    .orElseThrow(() -> new NoSuchElementException("Cita no encontrada con ID: " + citaId));
            if (cita.getClinica() == null || !cita.getClinica().getId().equals(clinicaId)) {
                throw new NoSuchElementException("La cita no pertenece a la misma clínica.");
            }
            if (cita.getPaciente() == null || !cita.getPaciente().getId().equals(pacienteId)) {
                throw new IllegalArgumentException("La cita no corresponde al paciente indicado.");
            }
        }

        // 5. Cargar entidades requeridas para la metadata
        Clinic clinica = clinicRepository.findById(clinicaId)
                .orElseThrow(() -> new NoSuchElementException("Clínica no encontrada con ID: " + clinicaId));
        User subidoPor = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Usuario uploader no encontrado con ID: " + principal.getUserId()));

        String originalName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : "archivo_sin_nombre";
        String contentType = FileContentValidator.detect(file);
        long pesoBytes = file.getSize();
        boolean visible = Boolean.TRUE.equals(esVisiblePaciente);

        // 6. Almacenar binario durable primero y aplicar compensación si falla el guardado en base de datos
        String storageKey = null;
        try {
            storageKey = storageService.store(file, clinicaId, pacienteId);
            log.info("[ATTACHMENT] Binario persistido con clave: {}", storageKey);

            Attachment attachment = new Attachment(
                    clinica,
                    paciente,
                    subidoPor,
                    tipoFinal,
                    storageKey,
                    originalName,
                    contentType,
                    pesoBytes,
                    regionAnatomica,
                    visible
            );
            attachment.setAmbito(isAdministrative(userRol) ? "ADMINISTRATIVO" : "CLINICO");
            if (cita != null) {
                attachment.setCita(cita);
            }

            Attachment saved = attachmentRepository.save(attachment);
            log.info("[ATTACHMENT] Metadatos guardados con éxito en BD. ID={}", saved.getId());
            return AttachmentResponseDto.fromEntity(saved);

        } catch (Exception ex) {
            log.error("[ATTACHMENT] Error durante la persistencia de metadatos. Iniciando compensación binaria para clave: {}", storageKey, ex);
            if (storageKey != null) {
                try {
                    storageService.delete(storageKey);
                    log.info("[ATTACHMENT] Compensación exitosa: archivo binario huérfano eliminado.");
                } catch (Exception rollbackEx) {
                    log.error("[ATTACHMENT] Error al compensar archivo binario huérfano: {}", storageKey, rollbackEx);
                }
            }
            throw ex;
        }
    }

    /**
     * Descarga de recurso binario autenticado mediante Proxy.
     * Verifica en tiempo real principal, pertenencia a clínica y permisos de visibilidad para el paciente.
     */
    @Transactional(readOnly = true)
    public Resource download(Long attachmentId, UserPrincipal principal) {
        Attachment attachment = getAttachmentMetadataEntity(attachmentId, principal);
        return storageService.loadAsResource(attachment.getClaveAlmacenamiento());
    }

    /**
     * Obtiene la entidad Attachment verificando aislamiento multi-tenant y reglas de visibilidad.
     */
    @Transactional(readOnly = true)
    public Attachment getAttachmentMetadataEntity(Long attachmentId, UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no válida o falta clínica activa.");
        }

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId));

        // Validación multi-tenant estricta
        if (attachment.getClinica() == null || !attachment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId);
        }

        String userRol = principal.getRol();

        // Control de acceso por rol: PACIENTE
        if ("PACIENTE".equalsIgnoreCase(userRol)) {
            Long userPacienteId = principal.getPacienteId();
            if (userPacienteId == null || !userPacienteId.equals(attachment.getPaciente().getId())) {
                throw new AccessDeniedException("No tiene permisos para acceder a los archivos de otro paciente.");
            }
            if (!attachment.isEsVisiblePaciente()) {
                throw new AccessDeniedException("El archivo es privado y no ha sido publicado para acceso del paciente.");
            }
            return attachment;
        }

        if (isAdministrative(userRol) && "ADMINISTRATIVO".equals(attachment.getAmbito())) return attachment;
        // El odontólogo accede al expediente clínico; administración solo a su ámbito.
        if ("ODONTOLOGO".equalsIgnoreCase(userRol)) {
            return attachment;
        }

        throw new AccessDeniedException("Rol no autorizado para consultar archivos en esta clínica.");
    }

    /**
     * Obtiene el DTO de metadata de un archivo verificando permisos en tiempo real.
     */
    @Transactional(readOnly = true)
    public AttachmentResponseDto getAttachmentMetadata(Long attachmentId, UserPrincipal principal) {
        Attachment attachment = getAttachmentMetadataEntity(attachmentId, principal);
        return AttachmentResponseDto.fromEntity(attachment);
    }

    /**
     * Modifica la visibilidad de un archivo clínico para el paciente (publicar / revocar).
     * Solo ODONTOLOGO y ADMIN_CLINICA de la misma clínica tienen autorización.
     */
    @Transactional
    public AttachmentResponseDto updateVisibility(Long attachmentId, boolean esVisiblePaciente, UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no válida o falta clínica activa.");
        }

        String userRol = principal.getRol();
        if (!"ODONTOLOGO".equalsIgnoreCase(userRol) && !isAdministrative(userRol)) {
            throw new AccessDeniedException("Rol no autorizado para publicar archivos");
        }

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId));

        if (attachment.getClinica() == null || !attachment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId);
        }

        if (isAdministrative(userRol) && !"ADMINISTRATIVO".equals(attachment.getAmbito())) {
            throw new AccessDeniedException("Solo el odontólogo puede publicar archivos clínicos");
        }
        attachment.setEsVisiblePaciente(esVisiblePaciente);
        Attachment updated = attachmentRepository.save(attachment);
        log.info("[ATTACHMENT] Visibilidad actualizada para ID={}: esVisiblePaciente={}", updated.getId(), esVisiblePaciente);

        if (esVisiblePaciente && updated.getPaciente() != null && updated.getPaciente().getUsuario() != null) {
            notificationService.createNotification(
                    updated.getClinica(),
                    updated.getPaciente().getUsuario(),
                    "DOCUMENTO_PUBLICADO",
                    "Nuevo documento disponible",
                    "Se ha publicado el archivo '" + updated.getNombreOriginal() + "' en su portal de paciente."
            );
        }

        return AttachmentResponseDto.fromEntity(updated);
    }

    /**
     * Lista los archivos adjuntos de un paciente con proyección explícita.
     * Si es PACIENTE: únicamente archivos propios con es_visible_paciente = true.
     * Si es personal clínico: todos los archivos del paciente en la clínica.
     */
    @Transactional(readOnly = true)
    public List<AttachmentResponseDto> listByPatient(Long pacienteId, UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no válida o falta clínica activa.");
        }

        Patient paciente = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId));

        if (paciente.getClinica() == null || !paciente.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado con ID: " + pacienteId);
        }

        String userRol = principal.getRol();
        List<Attachment> list;

        if ("PACIENTE".equalsIgnoreCase(userRol)) {
            Long userPacienteId = principal.getPacienteId();
            if (userPacienteId == null || !userPacienteId.equals(pacienteId)) {
                throw new AccessDeniedException("No tiene permisos para consultar archivos de otro paciente.");
            }
            list = attachmentRepository.findByClinicaIdAndPacienteIdAndEsVisiblePacienteTrueOrderByFechaCreacionDesc(
                    principal.getClinicaId(), pacienteId);
        } else if ("ODONTOLOGO".equalsIgnoreCase(userRol)) {
            list = attachmentRepository.findByClinicaIdAndPacienteIdOrderByFechaCreacionDesc(
                    principal.getClinicaId(), pacienteId);
        } else if (isAdministrative(userRol)) {
            list = attachmentRepository.findByClinicaIdAndPacienteIdOrderByFechaCreacionDesc(principal.getClinicaId(), pacienteId)
                    .stream().filter(a -> "ADMINISTRATIVO".equals(a.getAmbito())).toList();
        } else {
            throw new AccessDeniedException("Rol no autorizado para consultar archivos.");
        }

        return list.stream()
                .map(AttachmentResponseDto::fromEntity)
                .toList();
    }
    private boolean isAdministrative(String role) {
        return "ADMIN_CLINICA".equalsIgnoreCase(role) || "RECEPCIONISTA".equalsIgnoreCase(role);
    }
}
