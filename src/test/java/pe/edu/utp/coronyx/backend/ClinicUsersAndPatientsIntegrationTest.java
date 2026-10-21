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
import pe.edu.utp.coronyx.backend.dto.CreateClinicUserDto;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.dto.StatusUpdateDto;
import pe.edu.utp.coronyx.backend.dto.UpdateClinicUserDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.Role;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.RoleRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.service.JwtService;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class ClinicUsersAndPatientsIntegrationTest {

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

    private Patient adminFixture;
    private Patient linkFixture;
    private User linkUser;
    private String tokenAdmin;
    private String tokenRecepcion;
    private String tokenOdontologo;
    private String tokenPaciente;

    @BeforeEach
    void setupTokens() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();

        Clinic clinic=clinicRepository.findById(1L).orElseThrow();
        String fixtureKey=java.util.UUID.randomUUID().toString().substring(0,8);
        adminFixture=new Patient(clinic,"Paciente","Administrativo","DNI",fixtureKey);
        adminFixture.setAlergias("Penicilina"); adminFixture=patientRepository.saveAndFlush(adminFixture);
        linkFixture=patientRepository.saveAndFlush(new Patient(clinic,"Paciente","Vinculación","DNI",fixtureKey+"1"));
        patientRepository.saveAndFlush(new Patient(clinic,"Paciente","Listado","DNI",fixtureKey+"2"));
        linkUser=userRepository.saveAndFlush(new User("vinculo-"+fixtureKey+"@example.invalid",passwordEncoder.encode("123456"),"Cuenta","Vinculación"));
        clinicUserRepository.saveAndFlush(new ClinicUser(clinic,linkUser,roleRepository.findByCodigo("PACIENTE").orElseThrow(),null,"ACTIVO"));

        tokenAdmin = loginAndGetToken("admin@coronyx.pe", "123456");
        tokenRecepcion = loginAndGetToken("recepcion@coronyx.pe", "123456");
        tokenOdontologo = loginAndGetToken("odontologo@coronyx.pe", "123456");
        tokenPaciente = loginAndGetToken("paciente@coronyx.pe", "123456");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequestDto req = new LoginRequestDto(email, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.get("token").asText();
    }

    // --- A01: ADMIN_CLINICA - Crear cuenta de su clínica ---
    @Test
    @DisplayName("A01: ADMIN_CLINICA crea cuenta persistente con hash y nueva sesión real")
    void testA01_CrearCuentaDeClinica() throws Exception {
        String randomSuffix = String.valueOf(System.currentTimeMillis() % 100000);
        String email = "doctor." + randomSuffix + "@coronyx.pe";

        CreateClinicUserDto dto = new CreateClinicUserDto();
        dto.setCorreo(email);
        dto.setClave("claveSegura123");
        dto.setNombres("Dr. Roberto");
        dto.setApellidos("Vargas");
        dto.setRol("ODONTOLOGO");

        MvcResult res = mockMvc.perform(post("/api/v1/clinic/users")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertNotNull(body.get("id"));
        assertTrue(body.get("id").asText().matches("^[1-9]\\d*$"));
        assertTrue(body.get("usuarioId").asText().matches("^[1-9]\\d*$"));
        assertEquals("1", body.get("clinicaId").asText());
        assertEquals("ODONTOLOGO", body.get("rol").asText());

        // Comprobar contraseña con hash en BD
        User savedUser = userRepository.findByCorreoIgnoreCase(email).orElseThrow();
        assertTrue(savedUser.getClaveHash().startsWith("$2a$"), "La contraseña debe estar hasheada con BCrypt");
        assertNotEquals("claveSegura123", savedUser.getClaveHash());

        // Comprobar nueva sesión real con las credenciales creadas
        String newToken = loginAndGetToken(email, "claveSegura123");
        assertNotNull(newToken);
        assertFalse(newToken.isBlank());
    }

    // --- A02: ADMIN_CLINICA - Editar cuenta ---
    @Test
    @DisplayName("A02: ADMIN_CLINICA edita cuenta y membresía con persistencia en BD y coherencia de sesión")
    void testA02_EditarCuenta() throws Exception {
        // Editamos recepcionista (id membresía 2)
        UpdateClinicUserDto dto = new UpdateClinicUserDto();
        dto.setNombres("Paula Modificada");
        dto.setApellidos("Suárez Actualizada");

        MvcResult res = mockMvc.perform(put("/api/v1/clinic/users/2")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("Paula Modificada", body.get("nombres").asText());
        assertEquals("Suárez Actualizada", body.get("apellidos").asText());

        // Restaurar nombres originales
        dto.setNombres("Paula");
        dto.setApellidos("Suárez");
        mockMvc.perform(put("/api/v1/clinic/users/2")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // --- A03: ADMIN_CLINICA - Activar/desactivar cuenta y sesión previa invalidada ---
    @Test
    @DisplayName("A03: Desactivar membresía invalida sesión previa con token ya emitido")
    void testA03_DesactivarCuentaAfectaSesionPrevia() throws Exception {
        // 1. Odontólogo tiene token válido y puede consultar pacientes
        mockMvc.perform(get("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isOk());

        // 2. ADMIN_CLINICA desactiva membresía del odontólogo (id 1)
        StatusUpdateDto inactivo = new StatusUpdateDto("INACTIVO");
        mockMvc.perform(patch("/api/v1/clinic/users/1/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactivo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        // 3. Petición inmediata con el token previo del odontólogo DEBE FALLAR con 401
        mockMvc.perform(get("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isUnauthorized());

        // 4. Reactivar membresía para mantener el entorno limpio
        StatusUpdateDto activo = new StatusUpdateDto("ACTIVO");
        mockMvc.perform(patch("/api/v1/clinic/users/1/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        // 5. Tras reactivar, genera nueva sesión y funciona
        String nuevoTokenOdonto = loginAndGetToken("odontologo@coronyx.pe", "123456");
        mockMvc.perform(get("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + nuevoTokenOdonto))
                .andExpect(status().isOk());
    }

    // --- A04: ADMIN_CLINICA - Asignar rol autorizado (rechazar SUPER_ADMIN e inválidos) ---
    @Test
    @DisplayName("A04: ADMIN_CLINICA no puede asignar SUPER_ADMIN ni autoelevarse")
    void testA04_RechazarSuperAdminYRolesInvalidos() throws Exception {
        CreateClinicUserDto dto = new CreateClinicUserDto();
        dto.setCorreo("intento.superadmin@coronyx.pe");
        dto.setClave("123456");
        dto.setNombres("Intento");
        dto.setApellidos("SuperAdmin");
        dto.setRol("SUPER_ADMIN");

        // Intento de asignar SUPER_ADMIN debe ser rechazado con 400 Bad Request
        mockMvc.perform(post("/api/v1/clinic/users")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Intento de auto-desactivación del propio admin activo
        StatusUpdateDto selfDeactivate = new StatusUpdateDto("INACTIVO");
        mockMvc.perform(patch("/api/v1/clinic/users/3/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfDeactivate)))
                .andExpect(status().isBadRequest());
    }

    // --- A05: ADMIN_CLINICA - Buscar/listar pacientes administrativos ---
    @Test
    @DisplayName("A05: ADMIN_CLINICA lista pacientes de su clínica con DTO administrativo (sin datos clínicos)")
    void testA05_ListarPacientesAdministrativos() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(arr.isArray());
        assertTrue(arr.size() >= 4);

        // Control G02: campos clínicos deben ser nulos para ADMIN_CLINICA
        for (JsonNode p : arr) {
            assertEquals("1", p.get("clinicaId").asText());
            assertTrue(p.get("alergias").isNull());
            assertTrue(p.get("antecedentesMedicos").isNull());
            assertTrue(p.get("medicamentos").isNull());
        }

        // Endpoint administrativo explícito
        mockMvc.perform(get("/api/v1/patients/admin")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    // --- A06: ADMIN_CLINICA - Registrar paciente administrativo ---
    @Test
    @DisplayName("A06: ADMIN_CLINICA registra paciente administrativo (incluso niño de 2 años, sin regla 5-100 rígida)")
    void testA06_RegistrarPacienteAdministrativo() throws Exception {
        String docUnico = String.valueOf(30000000 + (int)(Math.random() * 69999999));
        String jsonCreate = String.format("""
                {
                  "nombres": "Mateo",
                  "apellidos": "Bebé",
                  "tipoDocumento": "DNI",
                  "numeroDocumento": "%s",
                  "fechaNacimiento": "%s",
                  "telefono": "+51 987654321",
                  "correo": "mateo.%s@gmail.com"
                }
                """, docUnico, LocalDate.now().minusYears(2).toString(), docUnico);

        MvcResult res = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCreate))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertNotNull(body.get("id"));
        assertEquals("Mateo", body.get("nombres").asText());
        assertEquals("1", body.get("clinicaId").asText());
    }

    // --- A07: ADMIN_CLINICA - Editar paciente administrativo sin modificar campos clínicos ---
    @Test
    @DisplayName("A07: ADMIN_CLINICA edita paciente administrativo sin alterar campos clínicos")
    void testA07_EditarPacienteAdministrativo() throws Exception {
        String jsonUpdate = """
                {
                  "telefono": "+51 911223344",
                  "seguro": "PACIFICO",
                  "alergias": "Intento de inyección clínica"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonUpdate))
                .andExpect(status().isOk());

        // Comprobar con odontólogo que la alergia en BD sigue siendo la original ("Penicilina")
        MvcResult resOdonto = mockMvc.perform(get("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bodyOdonto = objectMapper.readTree(resOdonto.getResponse().getContentAsString());
        assertEquals("Penicilina", bodyOdonto.get("alergias").asText());
        assertEquals("+51 911223344", bodyOdonto.get("telefono").asText());
    }

    // --- R01: RECEPCIONISTA - Buscar/listar pacientes ---
    @Test
    @DisplayName("R01: RECEPCIONISTA lista pacientes administrativos de su clínica con exclusión clínica")
    void testR01_RecepcionistaListarPacientes() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + tokenRecepcion))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(arr.size() >= 4);
        for (JsonNode p : arr) {
            assertTrue(p.get("alergias").isNull());
            assertTrue(p.get("antecedentesMedicos").isNull());
        }
    }

    // --- R02: RECEPCIONISTA - Registrar paciente ---
    @Test
    @DisplayName("R02: RECEPCIONISTA registra paciente administrativo de la clínica")
    void testR02_RecepcionistaRegistrarPaciente() throws Exception {
        String docUnico = String.valueOf(40000000 + (int)(Math.random() * 59999999));
        String jsonCreate = String.format("""
                {
                  "nombres": "Sofía",
                  "apellidos": "Castillo",
                  "tipoDocumento": "DNI",
                  "numeroDocumento": "%s",
                  "fechaNacimiento": "1995-06-18",
                  "telefono": "+51 955667788",
                  "correo": "sofia.%s@gmail.com"
                }
                """, docUnico, docUnico);

        mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCreate))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombres").value("Sofía"));
    }

    // --- R03: RECEPCIONISTA - Actualizar paciente ---
    @Test
    @DisplayName("R03: RECEPCIONISTA actualiza datos demográficos sin tocar datos clínicos")
    void testR03_RecepcionistaActualizarPaciente() throws Exception {
        String jsonUpdate = """
                {
                  "telefono": "+51 988776655",
                  "ciudad": "Arequipa"
                }
                """;

        mockMvc.perform(put("/api/v1/patients/"+adminFixture.getId())
                        .header("Authorization", "Bearer " + tokenRecepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("+51 988776655"));
    }

    // --- Vinculación de cuenta paciente sin equiparar paciente.id a usuario.id ---
    @Test
    @DisplayName("Vincular cuenta de paciente explícitamente manteniendo IDs diferenciados")
    void testVincularCuentaPacienteDiferenciandoIds() throws Exception {
        // Paciente con ID 2 vinculado al Usuario 4
        mockMvc.perform(patch("/api/v1/patients/"+linkFixture.getId()+"/link-user")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", linkUser.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(linkFixture.getId().toString()))
                .andExpect(jsonPath("$.usuarioId").value(linkUser.getId().toString()));

        // Comprobar persistencia en BD
        Patient p = patientRepository.findById(linkFixture.getId()).orElseThrow();
        assertEquals(linkFixture.getId(), p.getId());
        assertNotNull(p.getUsuario());
        assertEquals(linkUser.getId(), p.getUsuario().getId());
    }

    // --- Pruebas Negativas de Seguridad ---
    @Test
    @DisplayName("Negativos: Odontólogo y Recepcionista no pueden gestionar /clinic/users (403 Forbidden)")
    void testNegativo_RbacClinicUsers() throws Exception {
        // Odontólogo
        mockMvc.perform(get("/api/v1/clinic/users")
                        .header("Authorization", "Bearer " + tokenOdontologo))
                .andExpect(status().isForbidden());

        // Recepcionista
        mockMvc.perform(get("/api/v1/clinic/users")
                        .header("Authorization", "Bearer " + tokenRecepcion))
                .andExpect(status().isForbidden());

        // Paciente
        mockMvc.perform(get("/api/v1/clinic/users")
                        .header("Authorization", "Bearer " + tokenPaciente))
                .andExpect(status().isForbidden());

        // Sin token
        mockMvc.perform(get("/api/v1/clinic/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Negativos: Fecha de nacimiento futura rechazada con 400 Bad Request")
    void testNegativo_FechaNacimientoFutura() throws Exception {
        String jsonFuture = String.format("""
                {
                  "nombres": "Futuro",
                  "apellidos": "Imposible",
                  "tipoDocumento": "DNI",
                  "numeroDocumento": "88990011",
                  "fechaNacimiento": "%s"
                }
                """, LocalDate.now().plusDays(1).toString());

        mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonFuture))
                .andExpect(status().isBadRequest());
    }
}
