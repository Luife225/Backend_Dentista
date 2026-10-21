package pe.edu.utp.coronyx.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.DashboardSummaryDto;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.DashboardService;

@RestController
@RequestMapping({"/api/v1/dashboard", "/api/v1/panel"})
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
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

    @GetMapping({"/summary", "/resumen"})
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getDashboardSummary(resolvePrincipal(principal)));
    }
}
