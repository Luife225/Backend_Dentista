package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pe.edu.utp.coronyx.backend.controller.PatientController;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.service.PatientService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PatientIntegrationTest {

    @Autowired
    private PatientController patientController;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicUserRepository clinicUserRepository;

    @Autowired
    private PatientService patientService;

    @org.junit.jupiter.api.BeforeEach
    void setupSecurityContext() {
        Clinic clinic = clinicRepository.findById(1L).orElse(null);
        User user = userRepository.findByCorreoIgnoreCase("odontologo@coronyx.pe").orElse(null);
        ClinicUser cu = clinicUserRepository.findById(1L).orElse(null);
        pe.edu.utp.coronyx.backend.security.UserPrincipal principal =
                new pe.edu.utp.coronyx.backend.security.UserPrincipal(user, cu, clinic, null, "ODONTOLOGO");
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Crear paciente y consultar por ID: verificación de IDs decimal string")
    void testCreateAndGetPatient() {
        String docUnico = String.valueOf(10000000 + (int)(Math.random() * 89999999));
        PatientDto dto = new PatientDto();
        dto.setNombres("Carlos");
        dto.setApellidos("Gamboa");
        dto.setTipoDocumento("DNI");
        dto.setNumeroDocumento(docUnico);
        dto.setFechaNacimiento(LocalDate.of(1995, 5, 20));
        dto.setTelefono("+51 987654321");
        dto.setCorreo("carlos.gamboa." + docUnico + "@coronyx.pe");
        dto.setAlergias("Penicilina");
        dto.setAntecedentesMedicos("Ninguno relevante");
        dto.setEstado("ACTIVO");

        // 1. Crear paciente vía Controller
        ResponseEntity<PatientDto> createResponse = patientController.createPatient(dto);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());

        String createdId = createResponse.getBody().getId();
        assertNotNull(createdId);
        assertTrue(createdId.matches("^[1-9]\\d*$"), "El ID del paciente creado debe ser una cadena decimal positiva: " + createdId);
        assertEquals("Carlos", createResponse.getBody().getNombres());
        assertEquals("Gamboa", createResponse.getBody().getApellidos());
        assertEquals(docUnico, createResponse.getBody().getNumeroDocumento());

        // 2. Obtener paciente por ID
        Long numericId = Long.parseLong(createdId);
        ResponseEntity<PatientDto> getResponse = patientController.getPatientById(numericId);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertEquals(createdId, getResponse.getBody().getId());
        assertEquals(docUnico, getResponse.getBody().getNumeroDocumento());

        // 3. Obtener lista de pacientes y comprobar presencia
        ResponseEntity<List<PatientDto>> listResponse = patientController.getAllPatients();
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertTrue(listResponse.getBody().stream().anyMatch(p -> docUnico.equals(p.getNumeroDocumento())));
    }

    @Test
    @DisplayName("Alta concurrente: inserción paralela sin colisión de secuencias identity")
    void testConcurrentPatientRegistration() throws InterruptedException, ExecutionException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            tasks.add(() -> {
                setupSecurityContext();
                String doc = String.valueOf(20000000 + (int)(Math.random() * 70000000) + index);
                PatientDto dto = new PatientDto();
                dto.setNombres("Concurrente" + index);
                dto.setApellidos("Test" + index);
                dto.setTipoDocumento("DNI");
                dto.setNumeroDocumento(doc);
                dto.setFechaNacimiento(LocalDate.of(1990, 1, 15));
                dto.setTelefono("+51 90000000" + index);
                dto.setCorreo("concurrent." + doc + "@coronyx.pe");

                ResponseEntity<PatientDto> resp = patientController.createPatient(dto);
                assertEquals(HttpStatus.CREATED, resp.getStatusCode());
                return resp.getBody().getId();
            });
        }

        List<Future<String>> results = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        List<String> generatedIds = new ArrayList<>();
        for (Future<String> f : results) {
            String id = f.get();
            assertNotNull(id);
            assertTrue(id.matches("^[1-9]\\d*$"));
            assertFalse(generatedIds.contains(id), "No debe haber colisión de IDs en altas concurrentes: " + id);
            generatedIds.add(id);
        }

        assertEquals(threads, generatedIds.size(), "Todos los hilos concurrentes deben generar IDs únicos");
    }

    @Test
    @DisplayName("Overflow y ID superior a Number.MAX_SAFE_INTEGER (9007199254740995) sin redondeo")
    void testOverflowAndLargeIdAboveMaxSafeInteger() {
        // En JavaScript, Number.MAX_SAFE_INTEGER es 9007199254740991.
        // Cualquier valor superior como 9007199254740995 se redondea a 9007199254740996 si no se trata como string.
        long largeIdValue = 9007199254740995L;
        assertTrue(largeIdValue > 9007199254740991L, "El ID de prueba debe superar Number.MAX_SAFE_INTEGER");

        Clinic defaultClinic = clinicRepository.findById(1L).orElse(null);

        Patient largePatient = new Patient();
        largePatient.setId(largeIdValue);
        largePatient.setClinica(defaultClinic);
        largePatient.setNombres("Paciente");
        largePatient.setApellidos("Extremo");
        largePatient.setNumeroDocumento("99999999");
        largePatient.setTipoDocumento("DNI");
        largePatient.setFechaNacimiento(LocalDate.of(1988, 3, 10));
        largePatient.setEstado("ACTIVO");

        // Convertir a DTO y comprobar que la serialización a String no pierde precisión ni un solo dígito
        PatientDto dto = patientService.toDto(largePatient);
        assertEquals("9007199254740995", dto.getId(),
                "La cadena decimal debe ser exactamente 9007199254740995 sin redondeo IEEE-754");
        assertNotEquals("9007199254740996", dto.getId(),
                "No debe redondearse al entero par más cercano como haría JavaScript con tipo numérico");
    }
}
