package pe.edu.utp.coronyx.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.service.JwtService;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP real con Spring Security/Flyway/PostgreSQL; nunca usa la BD normal de la aplicación. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("block1-test")
@ContextConfiguration(initializers = Block1SessionIntegrationTest.IsolationGuard.class)
class Block1SessionIntegrationTest {
    static class IsolationGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            if (!"jdbc:postgresql://127.0.0.1:55431/coronyx_block1".equals(environment.getProperty("spring.datasource.url"))
                    || !"coronyx_test".equals(environment.getProperty("spring.datasource.username"))) {
                throw new IllegalStateException("La campaña solo admite el PostgreSQL aislado del bloque 1");
            }
        }
    }

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired ClinicRepository clinics;
    @Autowired ClinicUserRepository memberships;
    @Autowired PatientRepository patients;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicInteger serial = new AtomicInteger();
    private static String hash;
    private String suffix;
    private Clinic a, b;
    private User admin, doctor, reception, owner, foreignOwner;
    private ClinicUser adminMembership, doctorMembership, ownerMembership;
    private Patient patient, foreignPatient;
    private String adminToken;
    private static final String PASSWORD = "block1-test-password";
    private static final String SECRET = "block1-isolated-test-secret-not-for-any-production-environment-2026";

    @BeforeEach void fixture() {
        if (hash == null) hash = encoder.encode(PASSWORD);
        suffix = "b1-" + serial.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 12);
        a = clinics.save(new Clinic("Prueba A " + suffix, "Pruebas", "America/Lima", "ABIERTO"));
        b = clinics.save(new Clinic("Prueba B " + suffix, "Pruebas", "America/Lima", "ABIERTO"));
        admin = account("admin", a, "ADMIN_CLINICA");
        doctor = account("doctor", a, "ODONTOLOGO");
        reception = account("recepcion", a, "RECEPCIONISTA");
        owner = account("paciente", a, "PACIENTE");
        foreignOwner = account("paciente-b", b, "PACIENTE");
        adminMembership = membership(admin, a);
        doctorMembership = membership(doctor, a);
        ownerMembership = membership(owner, a);
        patient = record(a, owner);
        foreignPatient = record(b, foreignOwner);
        adminToken = token(admin, adminMembership, null);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE"})
    void fourRolesLoginAndRevalidate(String role) throws Exception {
        User user = switch (role) { case "ADMIN_CLINICA" -> admin; case "RECEPCIONISTA" -> reception;
            case "ODONTOLOGO" -> doctor; default -> owner; };
        Reply login = login(user, PASSWORD);
        assertEquals(200, login.status);
        assertEquals(role, login.body.path("rol").asText());
        assertEquals(user.getId().toString(), login.body.path("usuarioId").asText());
        assertTrue(login.body.path("usuarioClinicaId").isTextual());
        Reply me = call("GET", "/auth/me", login.body.path("token").asText(), null);
        assertEquals(200, me.status);
        assertEquals(role, me.body.path("rol").asText());
        if (role.equals("PACIENTE")) assertEquals(patient.getId().toString(), me.body.path("pacienteId").asText());
    }

    @Test void emailCannotGrantSuperAdmin() throws Exception {
        User unattached = users.save(new User("superadmin-" + suffix + "@example.test", hash, "Cuenta", "Sin rol"));
        assertEquals(401, login(unattached, PASSWORD).status);
        User legitimate = account("superadmin-legitimo", a, "RECEPCIONISTA");
        assertEquals("RECEPCIONISTA", login(legitimate, PASSWORD).body.path("rol").asText());
        String oldSuper = jwt.generateToken(unattached, "SUPER_ADMIN", null, null, null);
        assertEquals(401, call("GET", "/auth/me", oldSuper, null).status);
    }

    @Test void disabledRoleRejectsOldTokenAndNewLogin() throws Exception {
        String previous = token(doctor, doctorMembership, null);
        Role role = roles.findByCodigo("ODONTOLOGO").orElseThrow();
        role.setActivo(false); roles.save(role);
        try {
            assertEquals(401, call("GET", "/auth/me", previous, null).status);
            assertEquals(401, login(doctor, PASSWORD).status);
            assertEquals(400, call("POST", "/clinic/users", adminToken, creation("disabled-role", "ODONTOLOGO", null)).status);
        } finally { role.setActivo(true); roles.save(role); }
    }

    @Test void directPatientRequiresOpenClinicAndActivePatientRole() throws Exception {
        User direct = users.save(new User("directo-" + suffix + "@example.test", hash, "Paciente", "Directo"));
        Patient directPatient = record(a, direct);
        Reply session = login(direct, PASSWORD);
        assertEquals(200, session.status);
        String previous = session.body.path("token").asText();
        Role role = roles.findByCodigo("PACIENTE").orElseThrow();
        role.setActivo(false); roles.save(role);
        try { assertEquals(401, call("GET", "/auth/me", previous, null).status); }
        finally { role.setActivo(true); roles.save(role); }
        a.setEstado("CERRADO"); clinics.save(a);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        assertEquals(401, login(direct, PASSWORD).status);
        assertEquals(directPatient.getId(), patients.findById(directPatient.getId()).orElseThrow().getId());
    }

    @ParameterizedTest @ValueSource(strings = {"usuario", "membresia", "clinica", "paciente"})
    void currentStatesRevokeSession(String target) throws Exception {
        String previous = token(owner, ownerMembership, patient);
        switch (target) {
            case "usuario" -> { owner.setEstado("DESHABILITADO"); users.save(owner); }
            case "membresia" -> { ownerMembership.setEstado("INACTIVO"); memberships.save(ownerMembership); }
            case "clinica" -> { a.setEstado("CERRADO"); clinics.save(a); }
            default -> { patient.setEstado("INACTIVO"); patients.save(patient); }
        }
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        assertEquals(401, login(owner, PASSWORD).status);
    }

    @Test void disablingAndReactivatingDoesNotReviveToken() throws Exception {
        String previous = token(doctor, doctorMembership, null);
        assertEquals(200, call("PATCH", "/clinic/users/" + doctorMembership.getId() + "/status", adminToken, Map.of("estado", "INACTIVO")).status);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        assertEquals(200, call("PATCH", "/clinic/users/" + doctorMembership.getId() + "/status", adminToken, Map.of("estado", "ACTIVO")).status);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        assertEquals(200, login(doctor, PASSWORD).status);
    }

    @Test void roleAndPasswordChangesRevokeTokens() throws Exception {
        String previous = token(doctor, doctorMembership, null);
        assertEquals(200, call("PUT", "/clinic/users/" + doctorMembership.getId(), adminToken, Map.of("rol", "RECEPCIONISTA")).status);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        Reply session = login(doctor, PASSWORD);
        assertEquals("RECEPCIONISTA", session.body.path("rol").asText());
        assertEquals(200, call("PUT", "/clinic/users/" + doctorMembership.getId(), adminToken, Map.of("clave", "nueva-clave-prueba")).status);
        assertEquals(401, call("GET", "/auth/me", session.body.path("token").asText(), null).status);
        assertEquals(401, login(doctor, PASSWORD).status);
        assertEquals(200, login(doctor, "nueva-clave-prueba").status);
    }

    @Test void unlinkRelinkAndReassignInvalidateOldOwnership() throws Exception {
        String previous = token(owner, ownerMembership, patient);
        assertEquals(200, link(patient, null).status);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        assertEquals(401, login(owner, PASSWORD).status);
        assertEquals(200, link(patient, owner).status);
        assertEquals(401, call("GET", "/auth/me", previous, null).status);
        Reply refreshed = login(owner, PASSWORD);
        assertEquals(200, refreshed.status);
        User replacement = account("reemplazo", a, "PACIENTE");
        assertEquals(200, link(patient, replacement).status);
        assertEquals(401, call("GET", "/auth/me", refreshed.body.path("token").asText(), null).status);
        assertEquals(401, login(owner, PASSWORD).status);
        assertEquals(patient.getId().toString(), login(replacement, PASSWORD).body.path("pacienteId").asText());
    }

    @ParameterizedTest @ValueSource(strings = {"staff", "otra-clinica", "inactivo", "otro-expediente"})
    void rejectsIneligibleLinkWithoutMutation(String reason) throws Exception {
        User target = switch (reason) { case "staff" -> doctor; case "otra-clinica" -> foreignOwner;
            default -> account("objetivo-" + reason, a, "PACIENTE"); };
        if (reason.equals("inactivo")) { target.setEstado("DESHABILITADO"); users.save(target); }
        if (reason.equals("otro-expediente")) record(a, target);
        int expected = reason.equals("otro-expediente") ? 409 : 400;
        long version = users.findById(owner.getId()).orElseThrow().getSessionVersion();
        assertEquals(expected, link(patient, target).status);
        assertEquals(owner.getId(), patients.findById(patient.getId()).orElseThrow().getUsuario().getId());
        assertEquals(version, users.findById(owner.getId()).orElseThrow().getSessionVersion());
    }

    @Test void receptionCanLinkEligiblePatientAccount() throws Exception {
        User target = account("recepcion-vincula", a, "PACIENTE");
        String receptionToken = token(reception, membership(reception, a), null);
        assertEquals(200, call("PATCH", "/patients/" + patient.getId() + "/link-user", receptionToken,
                Map.of("usuarioId", target.getId().toString())).status);
        assertEquals(target.getId(), patients.findById(patient.getId()).orElseThrow().getUsuario().getId());
    }

    @Test void genericPatientRoutesCannotBypassLinkPolicy() throws Exception {
        assertEquals(400, call("PUT", "/patients/" + patient.getId(), adminToken, Map.of("usuarioId", doctor.getId().toString())).status);
        String doctorToken = token(doctor, doctorMembership, null);
        User available = account("paciente-libre", a, "PACIENTE");
        assertEquals(403, call("PUT", "/patients/" + patient.getId(), doctorToken, Map.of("usuarioId", available.getId().toString())).status);
        long count = patients.count();
        assertEquals(400, call("POST", "/patients", adminToken, Map.of("nombres", "Paciente", "apellidos", "Prueba",
                "numeroDocumento", "77889900", "tipoDocumento", "DNI", "fechaNacimiento", "2000-01-01",
                "usuarioId", foreignOwner.getId().toString())).status);
        assertEquals(count, patients.count());
        assertEquals(owner.getId(), patients.findById(patient.getId()).orElseThrow().getUsuario().getId());
    }

    @Test void existingForeignEmailCannotBeReused() throws Exception {
        long userCount = users.count(), membershipCount = memberships.count();
        Map<String, Object> body = creation("reuse", "ADMIN_CLINICA", null);
        body.put("correo", foreignOwner.getCorreo());
        assertEquals(409, call("POST", "/clinic/users", adminToken, body).status);
        assertEquals(userCount, users.count());
        assertEquals(membershipCount, memberships.count());
        assertEquals(hash, users.findById(foreignOwner.getId()).orElseThrow().getClaveHash());
    }

    @Test void sharedLegacyIdentityCannotBeModifiedByOneClinic() throws Exception {
        memberships.save(new ClinicUser(b, doctor, roles.findByCodigo("ODONTOLOGO").orElseThrow(), null, "ACTIVO"));
        assertEquals(409, call("PUT", "/clinic/users/" + doctorMembership.getId(), adminToken,
                Map.of("nombres", "Nombre no autorizado", "clave", "clave-no-autorizada", "estado", "INACTIVO")).status);
        assertEquals(409, call("PATCH", "/clinic/users/" + doctorMembership.getId() + "/status", adminToken, Map.of("estado", "INACTIVO")).status);
        User unchanged = users.findById(doctor.getId()).orElseThrow();
        assertEquals(hash, unchanged.getClaveHash());
        assertEquals("ACTIVO", unchanged.getEstado());
        assertEquals("ACTIVO", memberships.findById(doctorMembership.getId()).orElseThrow().getEstado());
        assertEquals(401, login(doctor, PASSWORD).status); // No selección de clínica implícita.
    }

    @ParameterizedTest @ValueSource(strings = {"incorrecto", "0", "-1", "9223372036854775808"})
    void invalidPatientIdCannotCreatePartialAccount(String id) throws Exception {
        long count = users.count();
        assertEquals(400, call("POST", "/clinic/users", adminToken, creation("invalid-id", "PACIENTE", id)).status);
        assertEquals(count, users.count());
        assertEquals(400, call("PATCH", "/patients/" + patient.getId() + "/link-user", adminToken, Map.of("usuarioId", id)).status);
    }

    @Test void foreignOrOccupiedPatientCannotBeTakenByNewAccount() throws Exception {
        long count = users.count();
        assertEquals(404, call("POST", "/clinic/users", adminToken, creation("foreign-record", "PACIENTE", foreignPatient.getId().toString())).status);
        assertEquals(409, call("POST", "/clinic/users", adminToken, creation("occupied-record", "PACIENTE", patient.getId().toString())).status);
        assertEquals(count, users.count());
        assertEquals(owner.getId(), patients.findById(patient.getId()).orElseThrow().getUsuario().getId());
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE"})
    void createsFourRolesWithSeparateIdsAndPersistentHash(String role) throws Exception {
        Patient free = record(a, null);
        Map<String, Object> body = creation("new-" + role, role, role.equals("PACIENTE") ? free.getId().toString() : null);
        Reply created = call("POST", "/clinic/users", adminToken, body);
        assertEquals(201, created.status);
        User saved = users.findByCorreoIgnoreCase(body.get("correo").toString()).orElseThrow();
        assertTrue(encoder.matches(PASSWORD, saved.getClaveHash()));
        Reply session = login(saved, PASSWORD);
        assertEquals(200, session.status);
        assertEquals(role, session.body.path("rol").asText());
        if (role.equals("PACIENTE")) assertEquals(free.getId().toString(), session.body.path("pacienteId").asText());
    }

    @Test void accountWithoutPatientCannotEnterPortal() throws Exception {
        User missing = account("sin-expediente", a, "PACIENTE");
        assertEquals(401, login(missing, PASSWORD).status);
        assertEquals(401, call("GET", "/auth/me", token(missing, membership(missing, a), null), null).status);
    }

    @Test void selfDeactivationAndSuperRoleAreRejected() throws Exception {
        assertEquals(400, call("PUT", "/clinic/users/" + adminMembership.getId(), adminToken, Map.of("estado", "INACTIVO")).status);
        assertEquals(400, call("PATCH", "/clinic/users/" + adminMembership.getId() + "/status", adminToken, Map.of("estado", "INACTIVO")).status);
        assertEquals(400, call("PUT", "/clinic/users/" + doctorMembership.getId(), adminToken, Map.of("rol", "SUPER_ADMIN")).status);
        assertEquals(400, call("POST", "/clinic/users", adminToken, creation("super", "SUPER_ADMIN", null)).status);
        assertEquals(200, call("GET", "/auth/me", adminToken, null).status);
    }

    @Test void nonAdminsCannotManageAccountsAndForeignObjectsStayUnchanged() throws Exception {
        for (User user : List.of(doctor, reception, owner)) {
            assertEquals(403, call("GET", "/clinic/users", token(user, membership(user, a), user == owner ? patient : null), null).status);
        }
        ClinicUser foreign = membership(foreignOwner, b);
        assertEquals(404, call("PUT", "/clinic/users/" + foreign.getId(), adminToken, Map.of("clave", "otra-clave-prueba")).status);
        assertEquals(404, link(foreignPatient, owner).status);
        assertEquals(hash, users.findById(foreignOwner.getId()).orElseThrow().getClaveHash());
        assertEquals(401, call("GET", "/auth/me", null, null).status);
    }

    @Test void signedStaleOrInconsistentClaimsNeverGrantAccess() throws Exception {
        List<Map<String, Object>> claims = new ArrayList<>();
        for (String field : List.of("usuarioClinicaId", "clinicaId", "pacienteId", "sessionVersion")) {
            Map<String, Object> wrong = claims(owner, ownerMembership, patient);
            wrong.put(field, switch (field) { case "usuarioClinicaId" -> membership(foreignOwner, b).getId().toString();
                case "clinicaId" -> b.getId().toString(); case "pacienteId" -> foreignPatient.getId().toString(); default -> "99"; });
            claims.add(wrong);
        }
        Map<String, Object> staffWithPatient = claims(doctor, doctorMembership, null);
        staffWithPatient.put("pacienteId", patient.getId().toString()); claims.add(staffWithPatient);
        Map<String, Object> missingVersion = claims(owner, ownerMembership, patient);
        missingVersion.remove("sessionVersion"); claims.add(missingVersion);
        for (Map<String, Object> wrong : claims) {
            User subject = wrong.get("userId").equals(doctor.getId().toString()) ? doctor : owner;
            assertEquals(401, call("GET", "/auth/me", signed(subject, wrong, new Date(System.currentTimeMillis() + 60000)), null).status);
        }
        String expired = signed(owner, claims(owner, ownerMembership, patient), new Date(System.currentTimeMillis() - 10000));
        assertEquals(401, call("GET", "/auth/me", expired, null).status);
        assertEquals(401, call("GET", "/auth/me", adminToken + "tampered", null).status);
    }

    @Test void simultaneousLinksCannotAssignOneUserToTwoPatients() throws Exception {
        User available = account("concurrente", a, "PACIENTE");
        Patient first = record(a, null), second = record(a, null);
        var one = CompletableFuture.supplyAsync(() -> uncheckedLink(first, available));
        var two = CompletableFuture.supplyAsync(() -> uncheckedLink(second, available));
        List<Integer> results = new ArrayList<>(List.of(one.get().status, two.get().status));
        Collections.sort(results);
        assertEquals(List.of(200, 409), results);
        assertEquals(1, patients.findByUsuarioId(available.getId()).size());
        assertEquals(200, login(available, PASSWORD).status);
    }

    @Test void simultaneousAccountCreationReturnsOneSuccessAndOneConflict() throws Exception {
        Map<String, Object> body = creation("correo-concurrente", "RECEPCIONISTA", null);
        var one = CompletableFuture.supplyAsync(() -> uncheckedCreate(body));
        var two = CompletableFuture.supplyAsync(() -> uncheckedCreate(body));
        List<Integer> results = new ArrayList<>(List.of(one.get().status, two.get().status));
        Collections.sort(results);
        assertEquals(List.of(201, 409), results);
        User created = users.findByCorreoIgnoreCase(body.get("correo").toString()).orElseThrow();
        assertEquals(1, memberships.findByUsuarioId(created.getId()).size());
    }

    @Test void migrationsPreserveSeedsAndUseBigint() {
        assertEquals(10, jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class));
        assertEquals("bigint", jdbc.queryForObject("select data_type from information_schema.columns where table_name='usuario' and column_name='session_version'", String.class));
        assertTrue(users.findByCorreoIgnoreCase("admin@coronyx.pe").isPresent());
        assertTrue(users.findByCorreoIgnoreCase("superadmin@coronyx.pe").isPresent());
        assertEquals(0L, users.findByCorreoIgnoreCase("admin@coronyx.pe").orElseThrow().getSessionVersion());
    }

    private User account(String name, Clinic clinic, String role) {
        User user = users.save(new User(name + "-" + suffix + "@example.test", hash, "Prueba", name));
        memberships.save(new ClinicUser(clinic, user, roles.findByCodigo(role).orElseThrow(), null, "ACTIVO"));
        return user;
    }
    private ClinicUser membership(User user, Clinic clinic) {
        return memberships.findByUsuarioIdAndClinicaId(user.getId(), clinic.getId()).orElseThrow();
    }
    private Patient record(Clinic clinic, User user) {
        Patient p = new Patient(clinic, "Paciente", "Prueba", "DNI", "77889900");
        p.setUsuario(user); return patients.save(p);
    }
    private String token(User user, ClinicUser membership, Patient patient) {
        return jwt.generateToken(user, membership.getRol().getCodigo(), membership.getClinica().getId(), membership.getId(), patient == null ? null : patient.getId());
    }
    private Reply login(User user, String password) throws Exception {
        return call("POST", "/auth/login", null, Map.of("correo", user.getCorreo(), "clave", password));
    }
    private Reply link(Patient patient, User user) throws Exception {
        Map<String, Object> body = new HashMap<>(); body.put("usuarioId", user == null ? null : user.getId().toString());
        return call("PATCH", "/patients/" + patient.getId() + "/link-user", adminToken, body);
    }
    private Reply uncheckedLink(Patient patient, User user) {
        try { return link(patient, user); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private Reply uncheckedCreate(Map<String, Object> body) {
        try { return call("POST", "/clinic/users", adminToken, body); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private Map<String, Object> creation(String name, String role, String patientId) {
        Map<String, Object> body = new HashMap<>(Map.of("correo", name + "-" + suffix + "@example.test",
                "clave", PASSWORD, "nombres", "Cuenta", "apellidos", "Prueba", "rol", role));
        if (patientId != null) body.put("pacienteId", patientId);
        return body;
    }
    private Map<String, Object> claims(User user, ClinicUser membership, Patient patient) {
        Map<String, Object> result = new HashMap<>(Map.of("userId", user.getId().toString(),
                "rol", membership.getRol().getCodigo(), "usuarioClinicaId", membership.getId().toString(),
                "clinicaId", membership.getClinica().getId().toString(), "sessionVersion", user.getSessionVersion().toString()));
        if (patient != null) result.put("pacienteId", patient.getId().toString());
        return result;
    }
    private String signed(User user, Map<String, Object> claims, Date expiration) {
        return Jwts.builder().setClaims(claims).setSubject(user.getCorreo()).setExpiration(expiration)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }
    private Reply call(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()));
    }
    private record Reply(int status, JsonNode body) {}
}
