package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pe.edu.utp.coronyx.backend.controller.AuthController;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private AuthController authController;

    @Test
    void testLoginWithOdontologo() {
        LoginRequestDto req = new LoginRequestDto("odontologo@coronyx.pe", "123456");
        ResponseEntity<LoginResponseDto> response = authController.login(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("odontologo@coronyx.pe", response.getBody().getCorreo());
        assertEquals("ODONTOLOGO", response.getBody().getRol());
        assertEquals("Andrés", response.getBody().getNombres());
        assertEquals("Herrera", response.getBody().getApellidos());
    }

    @Test
    void testGetAllUsers() {
        ResponseEntity<List<UserDto>> response = authController.getUsers();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5);
        assertTrue(response.getBody().stream().anyMatch(u -> "admin@coronyx.pe".equals(u.getCorreo())));
    }
}
