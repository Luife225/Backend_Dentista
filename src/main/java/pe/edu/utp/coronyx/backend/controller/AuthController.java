package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;
import pe.edu.utp.coronyx.backend.service.AuthService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("[AUTH] Intento de inicio de sesión para correo: {}", request.getCorreo());
        LoginResponseDto response = authService.login(request);
        log.info("[AUTH] Autenticación EXITOSA -> Usuario: {} | Rol: {} | Clínica: {} | Token: {}",
                response.getCorreo(), response.getRol(), response.getClinicaNombre(), response.getToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getUsers() {
        List<UserDto> users = authService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<java.util.Map<String, String>> handleAuthError(RuntimeException ex) {
        log.warn("[AUTH] Error de autenticación: {}", ex.getMessage());
        return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body(java.util.Map.of("message", ex.getMessage()));
    }
}
