package pe.edu.utp.coronyx.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.service.JwtService;

import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityFilterChainIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private ClinicUserRepository clinicUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Clinic clinicCentral;
    private Clinic clinicSecundaria;
    private User adminUser;
    private User odontologoUser;
    private User recepcionUser;
    private User superadminUser;
    private Patient patientClinic1;
    private Patient patientClinic2;
    private Patient patientVecino;

    @BeforeEach
    void setupTestData() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();

        // Asegurar clínica central
        clinicCentral = clinicRepository.findFirstByOrderByFechaCreacionAsc()
                .orElseGet(() -> clinicRepository.save(new Clinic("Clínica Dental Coronyx - Sede Central", "Coronyx Dental S.A.C.", "America/Lima", "ABIERTO")));

        // Asegurar segunda clínica para control de aislamiento multi-tenant
        clinicSecundaria = clinicRepository.findAll().stream()
                .filter(c -> !c.getId().equals(clinicCentral.getId()))
                .findFirst()
                .orElseGet(() -> clinicRepository.save(new Clinic("Clínica San Isidro Negativo", "San Isidro SAC", "America/Lima", "ABIERTO")));

        // Obtener roles de catálogo institucional
        Role rolAdmin = roleRepository.findByCodigo("ADMIN_CLINICA").orElseThrow();
        Role rolOdonto = roleRepository.findByCodigo("ODONTOLOGO").orElseThrow();
        Role rolRecep = roleRepository.findByCodigo("RECEPCIONISTA").orElseThrow();

        // Asegurar usuarios con hashes BCrypt válidos
        String bcHash = passwordEncoder.encode("123456");

        adminUser = userRepository.findByCorreoIgnoreCase("admin@coronyx.pe").orElseGet(() ->
                userRepository.save(new User("admin@coronyx.pe", bcHash, "Carlos", "Admin")));
        adminUser.setClaveHash(bcHash);
        adminUser.setEstado("ACTIVO");
        userRepository.save(adminUser);

        odontologoUser = userRepository.findByCorreoIgnoreCase("odontologo@coronyx.pe").orElseGet(() ->
                userRepository.save(new User("odontologo@coronyx.pe", bcHash, "Andrés", "Herrera")));
        odontologoUser.setClaveHash(bcHash);
        odontologoUser.setEstado("ACTIVO");
        userRepository.save(odontologoUser);

        recepcionUser = userRepository.findByCorreoIgnoreCase("recepcion@coronyx.pe").orElseGet(() ->
                userRepository.save(new User("recepcion@coronyx.pe", bcHash, "Paula", "Suárez")));
        recepcionUser.setClaveHash(bcHash);
        recepcionUser.setEstado("ACTIVO");
        userRepository.save(recepcionUser);

        superadminUser = userRepository.findByCorreoIgnoreCase("superadmin@coronyx.pe").orElseGet(() ->
                userRepository.save(new User("superadmin@coronyx.pe", bcHash, "Super", "Admin")));
        superadminUser.setClaveHash(bcHash);
        superadminUser.setEstado("ACTIVO");
        userRepository.save(superadminUser);

        // Membresías en clínica central
        if (clinicUserRepository.findByUsuarioIdAndClinicaId(adminUser.getId(), clinicCentral.getId()).isEmpty()) {
            clinicUserRepository.save(new ClinicUser(clinicCentral, adminUser, rolAdmin, adminUser, "ACTIVO"));
        }
        if (clinicUserRepository.findByUsuarioIdAndClinicaId(odontologoUser.getId(), clinicCentral.getId()).isEmpty()) {
            clinicUserRepository.save(new ClinicUser(clinicCentral, odontologoUser, rolOdonto, adminUser, "ACTIVO"));
        }
        if (clinicUserRepository.findByUsuarioIdAndClinicaId(recepcionUser.getId(), clinicCentral.getId()).isEmpty()) {
            clinicUserRepository.save(new ClinicUser(clinicCentral, recepcionUser, rolRecep, adminUser, "ACTIVO"));
        }

        // Paciente en clínica 1 con antecedentes clínicos
        patientClinic1 = patientRepository.findAll().stream()
                .filter(p -> p.getClinica().getId().equals(clinicCentral.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Patient p = new Patient(clinicCentral, "Juan", "Perez", "DNI", "74829104");
                    p.setFechaNacimiento(LocalDate.of(1990, 5, 10));
                    p.setAlergias("Penicilina");
                    p.setAntecedentesMedicos("Hipertensión");
                    p.setMedicamentos("Enalapril");
                    return patientRepository.save(p);
                });

        // Paciente vecino en clínica 1
        patientVecino = patientRepository.findAll().stream()
                .filter(p -> p.getClinica().getId().equals(clinicCentral.getId()) && !p.getId().equals(patientClinic1.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Patient p = new Patient(clinicCentral, "Vecino", "Gomez", "DNI", "74829105");
                    p.setFechaNacimiento(LocalDate.of(1992, 8, 15));
                    return patientRepository.save(p);
                });

        // Paciente en clínica 2 (para control multi-sede negativo)
        patientClinic2 = patientRepository.findAll().stream()
                .filter(p -> p.getClinica().getId().equals(clinicSecundaria.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Patient p = new Patient(clinicSecundaria, "SanIsidro", "Paciente", "DNI", "74829199");
                    p.setFechaNacimiento(LocalDate.of(1985, 3, 20));
                    return patientRepository.save(p);
                });
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String bodyJson = "{\"correo\":\"" + email + "\",\"clave\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("token").asText();
    }

    @Test
    @DisplayName("Filtro: Acceso sin token a endpoints protegidos retorna 401 RFC-7807 sin 500")
    void testEndpointsWithoutTokenReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/citas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Filtro: Token inválido, expirado o con formato UUID retorna 401")
    void testInvalidOrLegacyTokenReturns401() throws Exception {
        // Token arbitrario corrupto
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Token con formato UUID heredado
        String legacyToken = io.jsonwebtoken.Jwts.builder()
                .claim("userId", "11111111-2222-3333-4444-555555555555")
                .claim("rol", "ODONTOLOGO")
                .setSubject("odontologo@coronyx.pe")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                "coronyx-dental-system-secret-key-2026-super-secure-token-jwt-utp-platform".getBytes()),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Filtro: Validación actual en cada petición: usuario desactivado en BD retorna 401 de inmediato")
    void testDeactivatedUserReturns401OnNextRequest() throws Exception {
        // 1. Obtener token válido mientras el usuario está ACTIVO
        String token = loginAndGetToken("odontologo@coronyx.pe", "123456");

        // 2. Desactivar usuario en tiempo real en la base de datos (estado válido según check constraint: DESHABILITADO)
        odontologoUser.setEstado("DESHABILITADO");
        userRepository.save(odontologoUser);

        try {
            // 3. La siguiente petición debe ser rechazada inmediatamente por el filtro con 401
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        } finally {
            // Restaurar estado activo
            odontologoUser.setEstado("ACTIVO");
            userRepository.save(odontologoUser);
        }
    }

    @Test
    @DisplayName("Filtro: Rol incorrecto retorna 403 Forbidden")
    void testWrongRoleReturns403() throws Exception {
        // Recepcionista intentando acceder al directorio administrativo de usuarios
        String tokenRecep = loginAndGetToken("recepcion@coronyx.pe", "123456");

        mockMvc.perform(get("/api/v1/auth/users")
                        .header("Authorization", "Bearer " + tokenRecep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // SUPER_ADMIN no inicia una sesión operativa en este incremento.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("superadmin@coronyx.pe", "123456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Aislamiento Multi-Sede: Intentar acceder a paciente de otra clínica retorna 404 (ocultación de datos)")
    void testCrossClinicPatientAccessReturns404() throws Exception {
        String tokenAdminClinica1 = loginAndGetToken("admin@coronyx.pe", "123456");

        // Consultar paciente que pertenece a la Clínica 2
        mockMvc.perform(get("/api/v1/patients/" + patientClinic2.getId())
                        .header("Authorization", "Bearer " + tokenAdminClinica1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Intentar modificar paciente de Clínica 2 desde Clínica 1 sin mutación
        String updateJson = "{\"nombres\":\"MutacionIlegal\"}";
        mockMvc.perform(put("/api/v1/patients/" + patientClinic2.getId())
                        .header("Authorization", "Bearer " + tokenAdminClinica1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Aislamiento Multi-Sede: Consultar citas de otra clínica retorna 403 Forbidden")
    void testCrossClinicAppointmentsReturns403() throws Exception {
        String tokenAdminClinica1 = loginAndGetToken("admin@coronyx.pe", "123456");

        mockMvc.perform(get("/api/v1/citas?clinicaId=" + clinicSecundaria.getId())
                        .header("Authorization", "Bearer " + tokenAdminClinica1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Aislamiento de Paciente: Paciente intentando ver ID vecino retorna 403 Forbidden")
    void testPatientAccessingNeighborIdReturns403() throws Exception {
        // Crear usuario paciente titular vinculado a patientClinic1
        User userPaciente = userRepository.findByCorreoIgnoreCase("paciente.test@coronyx.pe")
                .orElseGet(() -> userRepository.save(new User("paciente.test@coronyx.pe", passwordEncoder.encode("123456"), "Luis", "Paciente")));
        userPaciente.setClaveHash(passwordEncoder.encode("123456"));
        userPaciente.setEstado("ACTIVO");
        userRepository.save(userPaciente);

        // Limpiar asignaciones previas para garantizar que userPaciente apunte de forma unívoca a patientClinic1
        for (Patient p : patientRepository.findByUsuarioId(userPaciente.getId())) {
            p.setUsuario(null);
            patientRepository.save(p);
        }

        patientClinic1.setUsuario(userPaciente);
        patientRepository.save(patientClinic1);

        String tokenPaciente = loginAndGetToken("paciente.test@coronyx.pe", "123456");

        // 1. Consulta de su propio expediente -> 200 OK
        mockMvc.perform(get("/api/v1/patients/" + patientClinic1.getId())
                        .header("Authorization", "Bearer " + tokenPaciente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patientClinic1.getId().toString()));

        // 2. Consulta de expediente vecino (patientVecino) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/patients/" + patientVecino.getId())
                        .header("Authorization", "Bearer " + tokenPaciente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 3. Consulta de citas de paciente vecino -> 403 Forbidden
        mockMvc.perform(get("/api/v1/citas/paciente/" + patientVecino.getId())
                        .header("Authorization", "Bearer " + tokenPaciente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DTO Administrativo: Excluye datos clínicos (alergias, antecedentes, medicamentos) para ADMIN y RECEPCIONISTA")
    void testAdminDtoExcludesClinicalData() throws Exception {
        // Asegurar que patientClinic1 tenga datos clínicos cargados
        patientClinic1.setAlergias("Penicilina");
        patientClinic1.setAntecedentesMedicos("Hipertensión");
        patientClinic1.setMedicamentos("Enalapril");
        patientRepository.save(patientClinic1);

        String tokenAdmin = loginAndGetToken("admin@coronyx.pe", "123456");
        String tokenOdonto = loginAndGetToken("odontologo@coronyx.pe", "123456");

        // Consulta como ADMIN_CLINICA: datos clínicos excluidos (null)
        MvcResult resultAdmin = mockMvc.perform(get("/api/v1/patients/" + patientClinic1.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonAdmin = objectMapper.readTree(resultAdmin.getResponse().getContentAsString());
        assertTrue(jsonAdmin.get("alergias").isNull(), "Alergias debe ser null en DTO administrativo");
        assertTrue(jsonAdmin.get("antecedentesMedicos").isNull(), "Antecedentes debe ser null en DTO administrativo");
        assertTrue(jsonAdmin.get("medicamentos").isNull(), "Medicamentos debe ser null en DTO administrativo");

        // Consulta como ODONTOLOGO: datos clínicos presentes
        MvcResult resultOdonto = mockMvc.perform(get("/api/v1/patients/" + patientClinic1.getId())
                        .header("Authorization", "Bearer " + tokenOdonto))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonOdonto = objectMapper.readTree(resultOdonto.getResponse().getContentAsString());
        assertEquals("Penicilina", jsonOdonto.get("alergias").asText());
        assertEquals("Hipertensión", jsonOdonto.get("antecedentesMedicos").asText());
        assertEquals("Enalapril", jsonOdonto.get("medicamentos").asText());
    }

    @Test
    @DisplayName("Sesión: GET /api/v1/auth/me retorna contrato acordado con IDs decimal string diferenciados")
    void testAuthMeReturnsAgreedContract() throws Exception {
        String tokenOdonto = loginAndGetToken("odontologo@coronyx.pe", "123456");

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokenOdonto))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode me = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(me.get("usuarioId"));
        assertTrue(me.get("usuarioId").asText().matches("^[1-9]\\d*$"));
        assertEquals(me.get("usuarioId").asText(), me.get("id").asText());

        assertNotNull(me.get("usuarioClinicaId"));
        assertTrue(me.get("usuarioClinicaId").asText().matches("^[1-9]\\d*$"));

        assertNotNull(me.get("clinicaId"));
        assertTrue(me.get("clinicaId").asText().matches("^[1-9]\\d*$"));

        assertEquals("ODONTOLOGO", me.get("rol").asText());
        assertEquals("odontologo@coronyx.pe", me.get("correo").asText());
        assertEquals("Andrés", me.get("nombres").asText());
        assertEquals("Herrera", me.get("apellidos").asText());
        assertNotNull(me.get("clinicaNombre").asText());

        // /me NO expone token
        assertTrue(me.get("token") == null || me.get("token").isNull(), "El endpoint /me no debe exponer tokens");
    }
}
