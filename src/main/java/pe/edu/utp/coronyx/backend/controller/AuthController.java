package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AuthService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, ClinicRepository clinicRepository, UserRepository userRepository) {
        this.authService = authService;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("[AUTH] Intento de autenticación para usuario: {}", request.getCorreo());
        LoginResponseDto response = authService.login(request);
        // Registro de auditoría seguro: sin imprimir tokens
        log.info("[AUTH] Autenticación EXITOSA -> Usuario: {} (id: {}) | Rol: {} | Sede: {} (id: {})",
                response.getCorreo(), response.getUsuarioId(), response.getRol(),
                response.getClinicaNombre(), response.getClinicaId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDto> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            try {
                principal = SecurityUtils.getCurrentPrincipal();
            } catch (Exception ignored) {
            }
        }
        LoginResponseDto me = authService.getMe(principal);
        return ResponseEntity.ok(me);
    }

    public ResponseEntity<LoginResponseDto> getMe() {
        return getMe(null);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getUsers(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            try {
                principal = SecurityUtils.getCurrentPrincipal();
            } catch (Exception ignored) {
            }
        }
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Acceso denegado: requiere sesión activa con permisos de clínica");
        }
        List<UserDto> users = authService.getAllUsers(principal);
        return ResponseEntity.ok(users);
    }

    public ResponseEntity<List<UserDto>> getUsers() {
        return getUsers(null);
    }
}
