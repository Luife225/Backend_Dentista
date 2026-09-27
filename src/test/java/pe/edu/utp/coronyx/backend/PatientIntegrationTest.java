package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pe.edu.utp.coronyx.backend.controller.PatientController;
import pe.edu.utp.coronyx.backend.dto.PatientDto;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PatientIntegrationTest {

    @Autowired
    private PatientController patientController;

    @Test
    void testCreateAndGetPatient() {
        PatientDto dto = new PatientDto();
        dto.setNombres("Carlos");
        dto.setApellidos("Gamboa");
        dto.setTipoDocumento("DNI");
        dto.setNumeroDocumento("77889900");
        dto.setFechaNacimiento(LocalDate.of(1995, 5, 20));
        dto.setTelefono("+51 987654321");
        dto.setCorreo("carlos.gamboa@coronyx.pe");
        dto.setAlergias("Penicilina");
        dto.setAntecedentesMedicos("Ninguno relevante");
        dto.setEstado("ACTIVO");

        // 1. Crear paciente vía Controller
        ResponseEntity<PatientDto> createResponse = patientController.createPatient(dto);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        assertNotNull(createResponse.getBody().getId());
        assertEquals("Carlos", createResponse.getBody().getNombres());
        assertEquals("Gamboa", createResponse.getBody().getApellidos());
        assertEquals("77889900", createResponse.getBody().getNumeroDocumento());

        // 2. Obtener lista de pacientes
        ResponseEntity<List<PatientDto>> listResponse = patientController.getAllPatients();
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertTrue(listResponse.getBody().stream().anyMatch(p -> "77889900".equals(p.getNumeroDocumento())));
    }
}
