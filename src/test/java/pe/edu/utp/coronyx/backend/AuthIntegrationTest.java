package pe.edu.utp.coronyx.backend;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pe.edu.utp.coronyx.backend.controller.AuthController;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;
import pe.edu.utp.coronyx.backend.service.JwtService;

import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicUserRepository clinicUserRepository;

    @BeforeEach
    void setupSecurityContext() {
        Clinic clinic = clinicRepository.findById(1L).orElse(null);
        User user = userRepository.findByCorreoIgnoreCase("admin@coronyx.pe").orElse(null);
        ClinicUser cu = clinicUserRepository.findById(3L).orElse(null);
        UserPrincipal principal = new UserPrincipal(user, cu, clinic, null, "ADMIN_CLINICA");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Login con Odontólogo: IDs decimal string, sesión con usuario y membresía diferenciados")
    void testLoginWithOdontologo() {
        LoginRequestDto req = new LoginRequestDto("odontologo@coronyx.pe", "123456");
        ResponseEntity<LoginResponseDto> response = authController.login(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = response.getBody();
        assertNotNull(body);
        assertEquals("odontologo@coronyx.pe", body.getCorreo());
        assertEquals("ODONTOLOGO", body.getRol());
        assertEquals("Andrés", body.getNombres());
        assertEquals("Herrera", body.getApellidos());

        // Verificación de contrato F01: IDs en formato decimal string positivo
        assertNotNull(body.getId());
        assertTrue(body.getId().matches("^[1-9]\\d*$"), "El ID general debe ser cadena decimal positiva: " + body.getId());

        assertNotNull(body.getUsuarioId());
        assertTrue(body.getUsuarioId().matches("^[1-9]\\d*$"), "usuarioId debe ser cadena decimal positiva");

        assertNotNull(body.getUsuarioClinicaId());
        assertTrue(body.getUsuarioClinicaId().matches("^[1-9]\\d*$"), "usuarioClinicaId debe ser cadena decimal positiva");

        assertNotNull(body.getClinicaId());
        assertTrue(body.getClinicaId().matches("^[1-9]\\d*$"), "clinicaId debe ser cadena decimal positiva");

        // Verificación de claims en JWT
        assertNotNull(body.getToken());
        assertTrue(jwtService.isTokenValid(body.getToken()), "El token JWT generado debe ser válido");

        Claims claims = jwtService.extractAllClaims(body.getToken());
        assertEquals(body.getUsuarioId(), claims.get("userId", String.class));
        assertEquals(body.getUsuarioClinicaId(), claims.get("usuarioClinicaId", String.class));
        assertEquals(body.getClinicaId(), claims.get("clinicaId", String.class));
        assertEquals("ODONTOLOGO", claims.get("rol", String.class));
    }

    @Test
    @DisplayName("Corte de JWT UUID heredados: tokens con UUID son rechazados tajantemente")
    void testLegacyUuidJwtCutoff() {
        // Token artificial con formato UUID en el claim userId
        String fakeLegacyToken = io.jsonwebtoken.Jwts.builder()
                .claim("userId", "c1111111-aaaa-bbbb-cccc-000000000001")
                .claim("clinicaId", "c2222222-aaaa-bbbb-cccc-000000000002")
                .setSubject("legacy@coronyx.pe")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 3600000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "coronyx-dental-system-secret-key-2026-super-secure-token-jwt-utp-platform".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        // El validador institucional debe rechazar el token heredado con UUID
        assertFalse(jwtService.isTokenValid(fakeLegacyToken),
                "La política F01 de corte de tokens debe rechazar cualquier token con identificador UUID");
    }

    @Test
    @DisplayName("Obtención de usuarios con IDs decimal string")
    void testGetAllUsers() {
        ResponseEntity<List<UserDto>> response = authController.getUsers();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 4);
        assertTrue(response.getBody().stream().anyMatch(u -> "admin@coronyx.pe".equals(u.getCorreo())));

        // Verificar que todos los usuarios devuelvan IDs en cadena decimal
        for (UserDto u : response.getBody()) {
            assertNotNull(u.getId());
            assertTrue(u.getId().matches("^[1-9]\\d*$"), "User ID debe ser cadena decimal: " + u.getId());
            if (u.getUsuarioClinicaId() != null) {
                assertTrue(u.getUsuarioClinicaId().matches("^[1-9]\\d*$"), "usuarioClinicaId debe ser cadena decimal: " + u.getUsuarioClinicaId());
            }
        }
    }
}
