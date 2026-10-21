package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.NotificationDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.Notification;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.NotificationRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("No se encontró una sesión activa autorizada");
        }
    }

    /**
     * V15: Emite una notificación transaccional persistida en PostgreSQL ligada al destinatario real.
     */
    @Transactional
    public Notification createNotification(Clinic clinica, User destinatario, String tipoEvento, String titulo, String mensaje) {
        if (clinica == null || destinatario == null) return null;
        Notification notif = new Notification(clinica, destinatario, tipoEvento, titulo, mensaje);
        return notificationRepository.save(notif);
    }

    /**
     * Consulta el buzón de notificaciones del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(UserPrincipal principal) {
        validatePrincipal(principal);
        return notificationRepository.findByDestinatarioIdOrderByFechaCreacionDesc(principal.getUserId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Conteo de notificaciones no leídas.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UserPrincipal principal) {
        validatePrincipal(principal);
        return notificationRepository.countByDestinatarioIdAndEstado(principal.getUserId(), "ENVIADO");
    }

    /**
     * Marca una notificación como leída con validación estricta de propiedad (403 si es ajena).
     */
    @Transactional
    public NotificationDto markAsRead(Long notifId, UserPrincipal principal) {
        validatePrincipal(principal);
        Notification notif = notificationRepository.findById(notifId)
                .orElseThrow(() -> new NoSuchElementException("Notificación no encontrada con ID: " + notifId));

        if (!notif.getDestinatario().getId().equals(principal.getUserId())) {
            throw new AccessDeniedException("Acceso denegado: no tiene autorización para modificar notificaciones de otro usuario.");
        }

        notif.setEstado("LEIDO");
        notif.setFechaLectura(OffsetDateTime.now());
        Notification saved = notificationRepository.save(notif);
        return toDto(saved);
    }

    /**
     * Marca todas las notificaciones pendientes del usuario como leídas.
     */
    @Transactional
    public long markAllAsRead(UserPrincipal principal) {
        validatePrincipal(principal);
        List<Notification> pending = notificationRepository.findByDestinatarioIdOrderByFechaCreacionDesc(principal.getUserId())
                .stream()
                .filter(n -> "ENVIADO".equalsIgnoreCase(n.getEstado()))
                .collect(Collectors.toList());

        OffsetDateTime now = OffsetDateTime.now();
        for (Notification n : pending) {
            n.setEstado("LEIDO");
            n.setFechaLectura(now);
            notificationRepository.save(n);
        }
        return pending.size();
    }

    private NotificationDto toDto(Notification n) {
        NotificationDto dto = new NotificationDto();
        dto.setId(n.getId() != null ? n.getId().toString() : null);
        dto.setTipoEvento(n.getTipoEvento());
        dto.setTitulo(n.getTitulo());
        dto.setCuerpoMensaje(n.getCuerpoMensaje());
        dto.setEstado(n.getEstado());
        dto.setFechaCreacion(n.getFechaCreacion());
        dto.setFechaLectura(n.getFechaLectura());
        return dto;
    }
}
