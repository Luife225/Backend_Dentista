package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.coronyx.backend.dto.AttachmentResponseDto;
import pe.edu.utp.coronyx.backend.dto.UpdateVisibilityRequestDto;
import pe.edu.utp.coronyx.backend.model.Attachment;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AttachmentService;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/attachments", "/api/v1/archivos"})
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Carga de archivo binario y persistencia de metadata con compensación.
     */
    @PostMapping(value = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pacienteId") Long pacienteId,
            @RequestParam(value = "tipoArchivo", required = false) String tipoArchivo,
            @RequestParam(value = "regionAnatomica", required = false) String regionAnatomica,
            @RequestParam(value = "citaId", required = false) Long citaId,
            @RequestParam(value = "esVisiblePaciente", required = false, defaultValue = "false") Boolean esVisiblePaciente,
            @AuthenticationPrincipal UserPrincipal principal) {

        AttachmentResponseDto response = attachmentService.upload(
                file, pacienteId, tipoArchivo, regionAnatomica, citaId, esVisiblePaciente, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Descarga de binario por proxy autenticado.
     * En cada solicitud se revalida visibilidad y pertenencia a la clínica en tiempo real.
     */
    @GetMapping({"/{id}/download", "/{id}/descarga"})
    public ResponseEntity<Resource> download(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Attachment attachment = attachmentService.getAttachmentMetadataEntity(id, principal);
        Resource resource = attachmentService.download(id, principal);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.getTipoMime());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String safeFilename = attachment.getNombreOriginal() != null
                ? attachment.getNombreOriginal().replace("\"", "")
                : "adjunto.bin";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(attachment.getPesoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename + "\"")
                .body(resource);
    }

    /**
     * Consulta de metadata de un archivo.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AttachmentResponseDto> getMetadata(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AttachmentResponseDto dto = attachmentService.getAttachmentMetadata(id, principal);
        return ResponseEntity.ok(dto);
    }

    /**
     * Publicar o revocar visibilidad de un archivo para el paciente.
     */
    @PatchMapping({"/{id}/visibility", "/{id}/visibilidad"})
    public ResponseEntity<AttachmentResponseDto> updateVisibility(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateVisibilityRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {

        AttachmentResponseDto updated = attachmentService.updateVisibility(
                id, request.getEsVisiblePaciente(), principal);
        return ResponseEntity.ok(updated);
    }

    /**
     * Acción directa para publicar archivo.
     */
    @PostMapping({"/{id}/publicar", "/{id}/publish"})
    public ResponseEntity<AttachmentResponseDto> publish(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AttachmentResponseDto updated = attachmentService.updateVisibility(id, true, principal);
        return ResponseEntity.ok(updated);
    }

    /**
     * Acción directa para revocar archivo.
     */
    @PostMapping({"/{id}/revocar", "/{id}/revoke"})
    public ResponseEntity<AttachmentResponseDto> revoke(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AttachmentResponseDto updated = attachmentService.updateVisibility(id, false, principal);
        return ResponseEntity.ok(updated);
    }

    /**
     * Listado de archivos de un paciente específico.
     */
    @GetMapping({"/patient/{pacienteId}", "/paciente/{pacienteId}"})
    public ResponseEntity<List<AttachmentResponseDto>> listByPatient(
            @PathVariable("pacienteId") Long pacienteId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AttachmentResponseDto> list = attachmentService.listByPatient(pacienteId, principal);
        return ResponseEntity.ok(list);
    }

    /**
     * Listado de archivos propios del paciente autenticado (Portal Paciente).
     */
    @GetMapping({"/me", "/mis-archivos"})
    public ResponseEntity<List<AttachmentResponseDto>> listMine(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null || principal.getPacienteId() == null) {
            throw new AccessDeniedException("No se encontró expediente de paciente asociado a esta sesión.");
        }
        List<AttachmentResponseDto> list = attachmentService.listByPatient(principal.getPacienteId(), principal);
        return ResponseEntity.ok(list);
    }
}
