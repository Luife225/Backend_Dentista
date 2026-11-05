package pe.edu.utp.coronyx.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlBboxDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlFindingReviewItemDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentMlDtos.MlReviewRequestDto;
import pe.edu.utp.coronyx.backend.dto.AttachmentResponseDto;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.storage.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private static final Set<String> ALLOWED_TIPOS_ARCHIVO = Set.of(
            "RADIOGRAFIA", "FOTOGRAFIA", "CONSENTIMIENTO", "LABORATORIO",
            "DOCUMENTO", "INFORME", "RECETA", "OTRO"
    );

    private static final Set<String> ALLOWED_ML_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg"
    );

    private static final Set<String> ALLOWED_ML_REVIEW_DECISIONS = Set.of(
            "ACEPTADO", "RECHAZADO", "CORREGIDO"
    );

    private static final Set<String> ALLOWED_ML_CLASS_NAMES = Set.of(
            "caries", "caries_profunda"
    );

    private final AttachmentRepository attachmentRepository;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final MlInferenceClient mlInferenceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AttachmentService(AttachmentRepository attachmentRepository,
                             PatientRepository patientRepository,
                             ClinicRepository clinicRepository,
                             UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             StorageService storageService,
                             NotificationService notificationService) {
        this(attachmentRepository, patientRepository, clinicRepository, userRepository,
                appointmentRepository, storageService, notificationService, new MlInferenceClient());
    }

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository,
                             PatientRepository patientRepository,
                             ClinicRepository clinicRepository,
                             UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             StorageService storageService,
                             NotificationService notificationService,
                             MlInferenceClient mlInferenceClient) {
        this.attachmentRepository = attachmentRepository;
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.mlInferenceClient = mlInferenceClient != null ? mlInferenceClient : new MlInferenceClient();
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

    /**
     * Fase I: Ejecuta el análisis asistido de caries y caries_profunda sobre una radiografía PNG/JPEG.
     * Restricciones de negocio y seguridad:
     * - Exclusivo para ODONTOLOGO de la misma clínica.
     * - Valida formato PNG/JPEG y ámbito CLINICO.
     * - Preserva intacta la predicción original del modelo en originalModelOutput.
     * - Guarda ia_estado_revision = 'PENDIENTE' sin alterar el odontograma ni la historia clínica.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public AttachmentResponseDto analyzeRadiographWithMl(Long attachmentId, Double customThreshold, UserPrincipal principal) {
        Attachment attachment = requireDentistSameClinicAttachment(attachmentId, principal,
                "Solo el odontólogo de la clínica está autorizado para ejecutar el análisis asistido de radiografías.");

        if ("ADMINISTRATIVO".equalsIgnoreCase(attachment.getAmbito())) {
            throw new IllegalArgumentException("El análisis ML solo aplica a archivos de ámbito CLINICO.");
        }

        String mime = attachment.getTipoMime() != null ? attachment.getTipoMime().trim().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_ML_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException(
                    "Formato de archivo incompatible para análisis ML (" + attachment.getTipoMime() + "). Solo se admiten imágenes PNG o JPEG.");
        }

        byte[] rawBytes;
        try {
            Resource resource = storageService.loadAsResource(attachment.getClaveAlmacenamiento());
            if (resource == null || !resource.exists()) {
                throw new NoSuchElementException("El archivo binario no se encontró en el almacenamiento para ID: " + attachmentId);
            }
            try (InputStream is = resource.getInputStream()) {
                rawBytes = is.readAllBytes();
            }
        } catch (NoSuchElementException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo binario almacenado para el análisis ML.", ex);
        }

        Map<String, Object> mlPayload = mlInferenceClient.analyzeRadiograph(
                rawBytes, attachment.getNombreOriginal(), customThreshold);

        String modelName = "coronyx-dentex-yolov8n";
        String modelVersion = "1.0.0-mvp";
        Object modelObj = mlPayload.get("model");
        if (modelObj instanceof Map<?, ?> modelMap) {
            Object nameVal = ((Map<String, Object>) modelMap).get("name");
            Object verVal = ((Map<String, Object>) modelMap).get("version");
            if (nameVal != null && !String.valueOf(nameVal).isBlank()) {
                modelName = String.valueOf(nameVal);
            }
            if (verVal != null && !String.valueOf(verVal).isBlank()) {
                modelVersion = String.valueOf(verVal);
            }
        }

        List<Map<String, Object>> rawFindings = new ArrayList<>();
        Object findingsObj = mlPayload.get("findings");
        if (findingsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> itemMap) {
                    rawFindings.add(new LinkedHashMap<>((Map<String, Object>) itemMap));
                }
            }
        }

        List<Map<String, Object>> originalModelOutput = new ArrayList<>();
        List<Map<String, Object>> reviewableFindings = new ArrayList<>();

        int idx = 1;
        for (Map<String, Object> rawFinding : rawFindings) {
            String findingId = rawFinding.get("finding_id") != null
                    ? String.valueOf(rawFinding.get("finding_id"))
                    : (rawFinding.get("findingId") != null ? String.valueOf(rawFinding.get("findingId")) : "hallazgo-" + idx);
            int classId = toInt(rawFinding.get("class_id") != null ? rawFinding.get("class_id") : rawFinding.get("classId"), 0);
            String className = rawFinding.get("class_name") != null
                    ? String.valueOf(rawFinding.get("class_name"))
                    : (classId == 1 ? "caries_profunda" : "caries");
            double score = toDouble(rawFinding.get("score"), 0.0);
            Map<String, Object> bboxMap = extractBboxMap(
                    rawFinding.get("bbox_pixels") != null ? rawFinding.get("bbox_pixels") : rawFinding.get("bboxPixels"));

            // Copia inmutable de la predicción original del modelo
            Map<String, Object> immutableOriginal = new LinkedHashMap<>();
            immutableOriginal.put("finding_id", findingId);
            immutableOriginal.put("class_id", classId);
            immutableOriginal.put("class_name", className);
            immutableOriginal.put("score", score);
            immutableOriginal.put("bbox_pixels", new LinkedHashMap<>(bboxMap));
            originalModelOutput.add(immutableOriginal);

            // Hallazgo enriquecido con estado de revisión odontológica
            Map<String, Object> reviewable = new LinkedHashMap<>();
            reviewable.put("finding_id", findingId);
            reviewable.put("findingId", findingId);
            reviewable.put("class_id", classId);
            reviewable.put("classId", classId);
            reviewable.put("class_name", className);
            reviewable.put("className", className);
            reviewable.put("original_class_id", classId);
            reviewable.put("originalClassId", classId);
            reviewable.put("original_class_name", className);
            reviewable.put("originalClassName", className);
            reviewable.put("score", score);
            reviewable.put("bbox_pixels", new LinkedHashMap<>(bboxMap));
            reviewable.put("bboxPixels", new LinkedHashMap<>(bboxMap));
            reviewable.put("original_bbox_pixels", new LinkedHashMap<>(bboxMap));
            reviewable.put("originalBboxPixels", new LinkedHashMap<>(bboxMap));
            reviewable.put("review_status", "PENDIENTE");
            reviewable.put("reviewStatus", "PENDIENTE");
            reviewable.put("clinical_note", null);
            reviewable.put("clinicalNote", null);
            reviewable.put("reviewed_by_usuario_clinica_id", null);
            reviewable.put("reviewedByUsuarioClinicaId", null);
            reviewable.put("reviewed_by_nombre", null);
            reviewable.put("reviewedByNombre", null);
            reviewable.put("reviewed_at_utc", null);
            reviewable.put("reviewedAtUtc", null);
            reviewableFindings.add(reviewable);
            idx++;
        }

        Map<String, Object> persistedPayload = new LinkedHashMap<>();
        persistedPayload.put("schema_version", mlPayload.getOrDefault("schema_version", "coronyx.ml.panoramic_caries.v1"));
        persistedPayload.put("request_id", mlPayload.get("request_id"));
        persistedPayload.put("analyzed_at_utc", mlPayload.getOrDefault("analyzed_at_utc",
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        persistedPayload.put("status", mlPayload.getOrDefault("status",
                reviewableFindings.isEmpty() ? "analysis_completed_no_findings" : "analysis_completed_with_findings"));
        persistedPayload.put("model", mlPayload.get("model"));
        persistedPayload.put("image", mlPayload.get("image"));
        persistedPayload.put("timing_ms", mlPayload.get("timing_ms"));
        persistedPayload.put("clinical_disclaimer", mlPayload.getOrDefault("clinical_disclaimer",
                "Sugerencias automatizadas pendientes de revisión odontológica."));
        persistedPayload.put("findings_count", reviewableFindings.size());
        persistedPayload.put("originalModelOutput", originalModelOutput);
        persistedPayload.put("findings", reviewableFindings);
        persistedPayload.put("review_history", new ArrayList<>());
        persistedPayload.put("overall_clinical_note", null);

        try {
            attachment.setIaNombreModelo(modelName);
            attachment.setIaVersionModelo(modelVersion);
            attachment.setIaHallazgosJson(objectMapper.writeValueAsString(persistedPayload));
            attachment.setIaEstadoRevision("PENDIENTE");
            attachment.setIaRevisadoPor(null);
        } catch (IOException ex) {
            throw new IllegalStateException("Error al serializar los hallazgos ML.", ex);
        }

        Attachment saved = attachmentRepository.save(attachment);
        log.info("[ATTACHMENT-ML] Análisis persistido para attachmentId={}, modelo={} {}, hallazgos={}",
                saved.getId(), modelName, modelVersion, reviewableFindings.size());
        return AttachmentResponseDto.fromEntity(saved);
    }

    /**
     * Fase I: Registra la revisión del odontólogo (ACEPTADO, RECHAZADO, CORREGIDO) sobre los hallazgos ML,
     * preservando siempre intacta la predicción original del modelo (originalModelOutput y campos original_*).
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public AttachmentResponseDto reviewRadiographMl(Long attachmentId, MlReviewRequestDto request, UserPrincipal principal) {
        Attachment attachment = requireDentistSameClinicAttachment(attachmentId, principal,
                "Solo el odontólogo de la clínica está autorizado para revisar hallazgos de IA.");

        if (attachment.getIaHallazgosJson() == null || attachment.getIaHallazgosJson().isBlank()) {
            throw new IllegalStateException("El archivo aún no cuenta con un análisis ML para revisar. Ejecute primero el análisis.");
        }
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud de revisión ML no puede ser nulo.");
        }

        Map<String, Object> persistedPayload;
        try {
            persistedPayload = objectMapper.readValue(
                    attachment.getIaHallazgosJson(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el JSON de hallazgos almacenado para este archivo.", ex);
        }

        List<MlFindingReviewItemDto> itemsToApply = new ArrayList<>();
        if (request.getReviews() != null) {
            itemsToApply.addAll(request.getReviews());
        }
        if (request.getFindingId() != null && !request.getFindingId().isBlank()) {
            itemsToApply.add(new MlFindingReviewItemDto(
                    request.getFindingId(),
                    request.getDecision(),
                    request.getCorrectedClassName(),
                    request.getCorrectedBboxPixels(),
                    request.getClinicalNote()
            ));
        }

        String overallDecision = null;
        if (request.getOverallDecision() != null && !request.getOverallDecision().isBlank()) {
            overallDecision = request.getOverallDecision().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_ML_REVIEW_DECISIONS.contains(overallDecision)) {
                throw new IllegalArgumentException("Decisión global no válida: " + overallDecision +
                        ". Valores permitidos: " + ALLOWED_ML_REVIEW_DECISIONS);
            }
        }

        if (itemsToApply.isEmpty() && overallDecision == null) {
            throw new IllegalArgumentException("Debe enviar al menos una revisión de hallazgo o una decisión global ('ACEPTADO', 'RECHAZADO' o 'CORREGIDO').");
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        Object findingsObj = persistedPayload.get("findings");
        if (findingsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> itemMap) {
                    findings.add(new LinkedHashMap<>((Map<String, Object>) itemMap));
                }
            }
        }

        String reviewerClinicaUserId = principal.getUsuarioClinicaId() != null
                ? String.valueOf(principal.getUsuarioClinicaId())
                : null;
        String reviewerName = principal.getUser() != null ? principal.getUser().getNombreCompleto() : null;
        String reviewedAtUtc = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        for (MlFindingReviewItemDto reviewItem : itemsToApply) {
            if (reviewItem == null || reviewItem.getFindingId() == null || reviewItem.getFindingId().isBlank()) {
                throw new IllegalArgumentException("Cada elemento de revisión debe indicar su 'findingId'.");
            }
            if (reviewItem.getDecision() == null || reviewItem.getDecision().isBlank()) {
                throw new IllegalArgumentException("El hallazgo '" + reviewItem.getFindingId() + "' requiere una decisión ('ACEPTADO', 'RECHAZADO' o 'CORREGIDO').");
            }
            String decision = reviewItem.getDecision().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_ML_REVIEW_DECISIONS.contains(decision)) {
                throw new IllegalArgumentException("Decisión no válida '" + decision + "' para hallazgo '" +
                        reviewItem.getFindingId() + "'. Valores permitidos: " + ALLOWED_ML_REVIEW_DECISIONS);
            }

            String targetId = reviewItem.getFindingId().trim();
            Map<String, Object> targetFinding = findings.stream()
                    .filter(f -> targetId.equals(String.valueOf(f.getOrDefault("findingId", f.get("finding_id")))))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No existe el hallazgo con ID: " + targetId));

            int origClassId = toInt(targetFinding.getOrDefault("originalClassId", targetFinding.get("original_class_id")),
                    toInt(targetFinding.getOrDefault("classId", targetFinding.get("class_id")), 0));
            String origClassName = String.valueOf(targetFinding.getOrDefault("originalClassName",
                    targetFinding.getOrDefault("original_class_name", origClassId == 1 ? "caries_profunda" : "caries")));
            Map<String, Object> origBbox = extractBboxMap(targetFinding.getOrDefault("originalBboxPixels",
                    targetFinding.getOrDefault("original_bbox_pixels", targetFinding.get("bbox_pixels"))));

            int effectiveClassId = origClassId;
            String effectiveClassName = origClassName;
            Map<String, Object> effectiveBbox = new LinkedHashMap<>(origBbox);

            if ("CORREGIDO".equals(decision)) {
                if (reviewItem.getCorrectedClassName() != null && !reviewItem.getCorrectedClassName().isBlank()) {
                    String correctedClass = reviewItem.getCorrectedClassName().trim().toLowerCase(Locale.ROOT);
                    if (!ALLOWED_ML_CLASS_NAMES.contains(correctedClass)) {
                        throw new IllegalArgumentException("Clase corregida no válida: '" + correctedClass +
                                "'. Clases permitidas: " + ALLOWED_ML_CLASS_NAMES);
                    }
                    effectiveClassName = correctedClass;
                    effectiveClassId = "caries_profunda".equals(correctedClass) ? 1 : 0;
                }
                if (reviewItem.getCorrectedBboxPixels() != null) {
                    effectiveBbox = validateAndConvertBbox(reviewItem.getCorrectedBboxPixels());
                }
            }

            // Preservar campos originales intactos y actualizar únicamente los campos efectivos y de revisión
            targetFinding.put("original_class_id", origClassId);
            targetFinding.put("originalClassId", origClassId);
            targetFinding.put("original_class_name", origClassName);
            targetFinding.put("originalClassName", origClassName);
            targetFinding.put("original_bbox_pixels", new LinkedHashMap<>(origBbox));
            targetFinding.put("originalBboxPixels", new LinkedHashMap<>(origBbox));

            targetFinding.put("class_id", effectiveClassId);
            targetFinding.put("classId", effectiveClassId);
            targetFinding.put("class_name", effectiveClassName);
            targetFinding.put("className", effectiveClassName);
            targetFinding.put("bbox_pixels", effectiveBbox);
            targetFinding.put("bboxPixels", new LinkedHashMap<>(effectiveBbox));
            targetFinding.put("review_status", decision);
            targetFinding.put("reviewStatus", decision);
            targetFinding.put("clinical_note", reviewItem.getClinicalNote());
            targetFinding.put("clinicalNote", reviewItem.getClinicalNote());
            targetFinding.put("reviewed_by_usuario_clinica_id", reviewerClinicaUserId);
            targetFinding.put("reviewedByUsuarioClinicaId", reviewerClinicaUserId);
            targetFinding.put("reviewed_by_nombre", reviewerName);
            targetFinding.put("reviewedByNombre", reviewerName);
            targetFinding.put("reviewed_at_utc", reviewedAtUtc);
            targetFinding.put("reviewedAtUtc", reviewedAtUtc);
        }

        if (overallDecision != null) {
            for (Map<String, Object> finding : findings) {
                String currentStatus = String.valueOf(finding.getOrDefault("reviewStatus", finding.getOrDefault("review_status", "PENDIENTE")));
                if ("PENDIENTE".equalsIgnoreCase(currentStatus)) {
                    finding.put("review_status", overallDecision);
                    finding.put("reviewStatus", overallDecision);
                    finding.put("reviewed_by_usuario_clinica_id", reviewerClinicaUserId);
                    finding.put("reviewedByUsuarioClinicaId", reviewerClinicaUserId);
                    finding.put("reviewed_by_nombre", reviewerName);
                    finding.put("reviewedByNombre", reviewerName);
                    finding.put("reviewed_at_utc", reviewedAtUtc);
                    finding.put("reviewedAtUtc", reviewedAtUtc);
                    if (request.getOverallClinicalNote() != null && finding.get("clinicalNote") == null) {
                        finding.put("clinical_note", request.getOverallClinicalNote());
                        finding.put("clinicalNote", request.getOverallClinicalNote());
                    }
                }
            }
        }

        String aggregateStatus = computeAggregateReviewStatus(findings, overallDecision);
        persistedPayload.put("findings", findings);
        if (request.getOverallClinicalNote() != null) {
            persistedPayload.put("overall_clinical_note", request.getOverallClinicalNote());
        }
        persistedPayload.put("last_reviewed_by_usuario_clinica_id", reviewerClinicaUserId);
        persistedPayload.put("last_reviewed_by_nombre", reviewerName);
        persistedPayload.put("last_reviewed_at_utc", reviewedAtUtc);

        List<Map<String, Object>> history = new ArrayList<>();
        Object historyObj = persistedPayload.get("review_history");
        if (historyObj instanceof List<?> rawHistory) {
            for (Object h : rawHistory) {
                if (h instanceof Map<?, ?> hMap) {
                    history.add(new LinkedHashMap<>((Map<String, Object>) hMap));
                }
            }
        }
        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("reviewed_at_utc", reviewedAtUtc);
        auditEntry.put("reviewed_by_usuario_clinica_id", reviewerClinicaUserId);
        auditEntry.put("reviewed_by_nombre", reviewerName);
        auditEntry.put("resulting_status", aggregateStatus);
        auditEntry.put("items_reviewed", itemsToApply.size());
        auditEntry.put("overall_clinical_note", request.getOverallClinicalNote());
        history.add(auditEntry);
        persistedPayload.put("review_history", history);

        try {
            attachment.setIaHallazgosJson(objectMapper.writeValueAsString(persistedPayload));
            attachment.setIaEstadoRevision(aggregateStatus);
            attachment.setIaRevisadoPor(principal.getMembership());
        } catch (IOException ex) {
            throw new IllegalStateException("Error al guardar la revisión de hallazgos ML.", ex);
        }

        Attachment saved = attachmentRepository.save(attachment);
        log.info("[ATTACHMENT-ML] Revisión registrada para attachmentId={}, estado={}, revisadoPor={}",
                saved.getId(), aggregateStatus, reviewerClinicaUserId);
        return AttachmentResponseDto.fromEntity(saved);
    }

    private Attachment requireDentistSameClinicAttachment(Long attachmentId, UserPrincipal principal, String roleDeniedMessage) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no válida o falta clínica activa.");
        }
        if (!"ODONTOLOGO".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException(roleDeniedMessage);
        }
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId));
        if (attachment.getClinica() == null || !attachment.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Archivo no encontrado con ID: " + attachmentId);
        }
        return attachment;
    }

    private String computeAggregateReviewStatus(List<Map<String, Object>> findings, String overallDecision) {
        if (findings == null || findings.isEmpty()) {
            return overallDecision != null ? overallDecision : "ACEPTADO";
        }
        boolean anyPending = false;
        boolean anyCorrected = false;
        boolean anyAccepted = false;
        for (Map<String, Object> f : findings) {
            String st = String.valueOf(f.getOrDefault("reviewStatus", f.getOrDefault("review_status", "PENDIENTE")));
            if ("PENDIENTE".equalsIgnoreCase(st)) {
                anyPending = true;
            } else if ("CORREGIDO".equalsIgnoreCase(st)) {
                anyCorrected = true;
            } else if ("ACEPTADO".equalsIgnoreCase(st)) {
                anyAccepted = true;
            }
        }
        if (anyPending) {
            return "PENDIENTE";
        }
        if (anyCorrected) {
            return "CORREGIDO";
        }
        if (anyAccepted) {
            return "ACEPTADO";
        }
        return "RECHAZADO";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractBboxMap(Object bboxObj) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (bboxObj instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            result.put("xMin", toDouble(m.get("xMin"), 0.0));
            result.put("yMin", toDouble(m.get("yMin"), 0.0));
            result.put("xMax", toDouble(m.get("xMax"), 0.0));
            result.put("yMax", toDouble(m.get("yMax"), 0.0));
        } else {
            result.put("xMin", 0.0);
            result.put("yMin", 0.0);
            result.put("xMax", 0.0);
            result.put("yMax", 0.0);
        }
        return result;
    }

    private Map<String, Object> validateAndConvertBbox(MlBboxDto dto) {
        if (dto.getxMin() == null || dto.getyMin() == null || dto.getxMax() == null || dto.getyMax() == null) {
            throw new IllegalArgumentException("Las coordenadas de la caja corregida (xMin, yMin, xMax, yMax) son obligatorias.");
        }
        if (dto.getxMin() < 0 || dto.getyMin() < 0 || dto.getxMax() <= dto.getxMin() || dto.getyMax() <= dto.getyMin()) {
            throw new IllegalArgumentException("Las coordenadas de la caja corregida no son válidas (se requiere 0 <= xMin < xMax y 0 <= yMin < yMax).");
        }
        Map<String, Object> bbox = new LinkedHashMap<>();
        bbox.put("xMin", dto.getxMin());
        bbox.put("yMin", dto.getyMin());
        bbox.put("xMax", dto.getxMax());
        bbox.put("yMax", dto.getyMax());
        return bbox;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean isAdministrative(String role) {
        return "ADMIN_CLINICA".equalsIgnoreCase(role) || "RECEPCIONISTA".equalsIgnoreCase(role);
    }
}
