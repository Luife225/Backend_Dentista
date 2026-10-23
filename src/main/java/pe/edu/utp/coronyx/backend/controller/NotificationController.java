package pe.edu.utp.coronyx.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.NotificationDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/v1/notificaciones"})
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) return principal;
        try {
            UserPrincipal current = SecurityUtils.getCurrentPrincipal();
            if (current != null) return current;
        } catch (Exception ignored) {
        }
        throw new AccessDeniedException("No se encontró una sesión activa o autorizada.");
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getMyNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.getMyNotifications(resolvePrincipal(principal)));
    }

    @GetMapping({"/unread-count", "/no-leidas"})
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = notificationService.getUnreadCount(resolvePrincipal(principal));
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping({"/{id}/read", "/{id}/leer"})
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.markAsRead(id, resolvePrincipal(principal)));
    }

    @PatchMapping({"/read-all", "/leer-todas"})
    public ResponseEntity<Map<String, Long>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        long marked = notificationService.markAllAsRead(resolvePrincipal(principal));
        return ResponseEntity.ok(Map.of("markedCount", marked));
    }
}
