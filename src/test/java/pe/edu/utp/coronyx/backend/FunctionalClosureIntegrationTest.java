package pe.edu.utp.coronyx.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.service.JwtService;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exclusivamente una BD temporal indicada explícitamente; nunca usa la BD por defecto. */
@EnabledIfEnvironmentVariable(named="CORONYX_TEST_DB_URL", matches=".+")
@SpringBootTest(properties={
        "spring.datasource.url=${CORONYX_TEST_DB_URL}", "spring.datasource.username=postgres", "spring.datasource.password=",
        "storage.local.directory=${CORONYX_TEST_STORAGE}", "jwt.secret=coronyx-closing-test-key-only-64-characters-not-production-2026"})
@Transactional
class FunctionalClosureIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired ClinicRepository clinics;
    @Autowired UserRepository users;
    @Autowired ClinicUserRepository memberships;
    @Autowired RoleRepository roles;
    @Autowired PatientRepository patients;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    MockMvc mvc; final ObjectMapper json=new ObjectMapper();
    Clinic clinic; Patient patient; String adminToken,doctorToken,patientToken; ClinicUser doctor;
    String start,end;
    @BeforeEach void fixture() {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        clinic=clinics.saveAndFlush(new Clinic("Clínica cierre "+UUID.randomUUID(),null,"America/Lima","ABIERTO"));
        ClinicUser admin=member("ADMIN_CLINICA"); doctor=member("ODONTOLOGO"); ClinicUser pm=member("PACIENTE");
        patient=new Patient(clinic,"Paciente","Cierre","DNI",UUID.randomUUID().toString().substring(0,8)); patient.setUsuario(pm.getUsuario()); patient=patients.saveAndFlush(patient);
        adminToken=token(admin,null); doctorToken=token(doctor,null); patientToken=token(pm,patient);
        LocalDate day=LocalDate.now(ZoneId.of("America/Lima")).plusDays(1); while(day.getDayOfWeek()==DayOfWeek.SUNDAY) day=day.plusDays(1);
        start=day+"T10:00:00-05:00"; end=day+"T10:30:00-05:00";
    }
    ClinicUser member(String role) {
        User u=users.saveAndFlush(new User(UUID.randomUUID()+"@example.invalid",encoder.encode("Cierre123!"),"Prueba",role));
        return memberships.saveAndFlush(new ClinicUser(clinic,u,roles.findByCodigo(role).orElseThrow(),null,"ACTIVO"));
    }
    String token(ClinicUser m, Patient p) { return jwt.generateToken(m.getUsuario(),m.getRol().getCodigo(),clinic.getId(),m.getId(),p==null?null:p.getId()); }
    JsonNode postJson(String url,String token,Object data,int expected) throws Exception {
        return json.readTree(mvc.perform(post(url).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(data)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String url,String token,int expected) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization","Bearer "+token)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    JsonNode appointment() throws Exception {
        return postJson("/api/v1/citas",adminToken,Map.of("pacienteId",patient.getId().toString(),"odontologoId",doctor.getId().toString(),"inicioEn",start,"finEn",end,"modalidad","PRESENCIAL","consultorio","Sillón 1","motivo","Control"),201);
    }
    @Test void realChargeAbonosAndAnulacion() throws Exception {
        JsonNode charge=postJson("/api/v1/caja/cargos",adminToken,Map.of("pacienteId",patient.getId().toString(),"concepto","Control","importe","100.00","solicitudId","cargo-1"),200);
        String path="/api/v1/caja/cargos/"+charge.get("id").asText()+"/pagos";
        Map<String,String> payment=Map.of("importe","40.00","medio","EFECTIVO","solicitudId","pago-1");
        JsonNode paid=postJson(path,adminToken,payment,200); assertEquals(60,paid.get("saldo").asInt());
        JsonNode repeat=postJson(path,adminToken,payment,200); assertEquals(1,repeat.get("pagos").size());
        JsonNode voided=postJson("/api/v1/caja/pagos/"+paid.get("pagos").get(0).get("id").asText()+"/anular",adminToken,Map.of("motivo","Corrección de registro"),200);
        assertEquals(100,voided.get("saldo").asInt()); assertTrue(voided.get("pagos").get(0).get("anulado").asBoolean());
        assertTrue(charge.get("id").isTextual());
    }
    @Test void actualStockLedgerAndRetry() throws Exception {
        JsonNode item=postJson("/api/v1/inventario/insumos",adminToken,Map.of("nombre","Guantes","categoria","Protección","unidad","cajas","stockMinimo",2,"costoUnitario","15.50","activo",true),200);
        String path="/api/v1/inventario/insumos/"+item.get("id").asText()+"/movimientos";
        postJson(path,adminToken,Map.of("tipo","ENTRADA","cantidad",10,"motivo","Compra","solicitudId","mov-1"),200);
        var dto=Map.of("tipo","SALIDA","cantidad",4,"motivo","Consumo","solicitudId","mov-2");
        assertEquals(6,postJson(path,adminToken,dto,200).get("stock").asInt());
        assertEquals(6,postJson(path,adminToken,dto,200).get("stock").asInt());
        assertEquals(2,getJson(path,adminToken,200).size());
        postJson(path,adminToken,Map.of("tipo","SALIDA","cantidad",7,"motivo","Exceso","solicitudId","mov-3"),409);
    }
    @Test void clinicalAttentionStartsAndSignsThroughHttp() throws Exception {
        JsonNode appt=appointment(); String appointmentId=appt.get("id").asText();
        JsonNode draft=postJson("/api/v1/citas/"+appointmentId+"/iniciar-atencion",doctorToken,Map.of(),200);
        JsonNode retry=postJson("/api/v1/citas/"+appointmentId+"/iniciar-atencion",doctorToken,Map.of(),200);
        assertEquals(draft.get("id"),retry.get("id")); assertTrue(draft.get("id").isTextual());
        String encounterId=draft.get("id").asText();
        String updateBody=json.writeValueAsString(Map.of("motivoConsulta","Control","diagnostico","Diagnóstico documentado","revision",draft.get("versionActual").get("revision").asText()));
        String updatedResponse=mvc.perform(put("/api/v1/clinical-encounters/"+encounterId+"/draft").header("Authorization","Bearer "+doctorToken).contentType("application/json")
                .content(updateBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode updated=json.readTree(updatedResponse);
        assertNotEquals(draft.get("versionActual").get("revision"),updated.get("versionActual").get("revision"));
        mvc.perform(put("/api/v1/clinical-encounters/"+encounterId+"/draft").header("Authorization","Bearer "+doctorToken).contentType("application/json").content(updateBody)).andExpect(status().isConflict());
        JsonNode signed=postJson("/api/v1/clinical-encounters/"+encounterId+"/confirm",doctorToken,
                Map.of("versionId",updated.get("versionActual").get("id").asText(),"revision",updated.get("versionActual").get("revision").asText()),200);
        assertTrue(signed.get("versionActual").get("estaConfirmado").asBoolean());
        JsonNode closed=getJson("/api/v1/citas/"+appointmentId,doctorToken,200);
        assertEquals("FINALIZADA",closed.get("estado").asText()); assertEquals("ASISTIO",closed.get("estadoAsistencia").asText());
        mvc.perform(put("/api/v1/clinical-encounters/"+encounterId+"/draft").header("Authorization","Bearer "+doctorToken).contentType("application/json").content("{\"diagnostico\":\"Sobrescritura\"}")).andExpect(status().isConflict());
    }
    @Test void rescheduledAppointmentStillOccupiesItsInterval() throws Exception {
        String id=appointment().get("id").asText(); String ns=start.replace("T10:","T11:"), ne=end.replace("T10:","T11:");
        mvc.perform(put("/api/v1/citas/"+id+"/reprogramar").header("Authorization","Bearer "+adminToken).param("nuevoInicio",ns).param("nuevoFin",ne)).andExpect(status().isOk());
        postJson("/api/v1/citas",adminToken,Map.of("pacienteId",patient.getId().toString(),"odontologoId",doctor.getId().toString(),"inicioEn",ns,"finEn",ne,"modalidad","PRESENCIAL"),409);
    }
    @Test void rolesAndClinicIsolationAreEnforcedOverHttp() throws Exception {
        getJson("/api/v1/caja/cargos",patientToken,403); getJson("/api/v1/inventario/insumos",doctorToken,403);
        getJson("/api/v1/odontograma/paciente/"+patient.getId(),adminToken,403);
        mvc.perform(put("/api/v1/disponibilidad/horarios").header("Authorization","Bearer "+adminToken).param("clinicaId","999999999")
                .contentType("application/json").content("[]")).andExpect(status().isForbidden());
    }
    @Test void clinicalAndAdministrativeFilesStaySeparated() throws Exception {
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9WQAAAAASUVORK5CYII=");
        String response=mvc.perform(multipart("/api/v1/attachments").file(new MockMultipartFile("file","rx.png","image/png",png))
                .param("pacienteId",patient.getId().toString()).param("tipoArchivo","RADIOGRAFIA").header("Authorization","Bearer "+doctorToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/attachments/"+id+"/download").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attachments/"+id+"/download").header("Authorization","Bearer "+patientToken)).andExpect(status().isForbidden());
        postJson("/api/v1/attachments/"+id+"/publicar",doctorToken,Map.of(),200);
        mvc.perform(get("/api/v1/attachments/"+id+"/download").header("Authorization","Bearer "+patientToken)).andExpect(status().isOk());
        String administrative=mvc.perform(multipart("/api/v1/attachments").file(new MockMultipartFile("file","ficha.pdf","text/plain","%PDF-1.4\nDocumento ficticio".getBytes()))
                .param("pacienteId",patient.getId().toString()).param("tipoArchivo","DOCUMENTO").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals("ADMINISTRATIVO",json.readTree(administrative).get("ambito").asText());
        assertEquals("application/pdf",json.readTree(administrative).get("tipoMime").asText());
        assertEquals(1,getJson("/api/v1/attachments/patient/"+patient.getId(),adminToken,200).size());
        assertEquals(2,getJson("/api/v1/attachments/patient/"+patient.getId(),doctorToken,200).size());
    }

    @Test void absenceBlocksSlotsAndCanBeRemoved() throws Exception {
        String date=start.substring(0,10);
        String absencePath="/api/v1/disponibilidad/ausencias/"+doctor.getId();
        JsonNode absence=postJson(absencePath,adminToken,Map.of("odontologoId",doctor.getId().toString(),"inicioEn",date+"T08:00:00-05:00","finEn",date+"T09:00:00-05:00","motivo","Ausencia ficticia","tipo","PERMISO"),201);
        JsonNode slots=getJson("/api/v1/disponibilidad/slots?odontologoId="+doctor.getId()+"&fecha="+date,patientToken,200);
        assertEquals("08:00",slots.get(0).get("inicio").asText()); assertFalse(slots.get(0).get("disponible").asBoolean());
        mvc.perform(delete("/api/v1/disponibilidad/ausencias/"+absence.get("id").asText()).header("Authorization","Bearer "+adminToken)).andExpect(status().isNoContent());
        assertTrue(getJson("/api/v1/disponibilidad/slots?odontologoId="+doctor.getId()+"&fecha="+date,patientToken,200).get(0).get("disponible").asBoolean());
    }
}
