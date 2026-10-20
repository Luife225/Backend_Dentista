package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.edu.utp.coronyx.backend.controller.CoroVozController;
import pe.edu.utp.coronyx.backend.controller.GlobalExceptionHandler;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp;
import pe.edu.utp.coronyx.backend.service.corovoz.DentalCommandParser;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP real de Spring MVC sin conexión a la BD del proyecto. */
class CoroVozHttpTest {
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new CoroVozController(new CoroVozHelp(), new DentalCommandParser(), mock(pe.edu.utp.coronyx.backend.service.corovoz.CoroVozPatientQueries.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

    @AfterEach void clearSession() { SecurityContextHolder.clearContext(); }

    private void login(String role) {
        var principal = mock(UserPrincipal.class);
        when(principal.getRol()).thenReturn(role);
        when(principal.hasOperativeClinic()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test void generalReturnsUsableNavigationForReception() throws Exception {
        login("RECEPCIONISTA");
        mvc.perform(post("/api/v1/corovoz/general").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"cómo agendo una cita\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true))
                .andExpect(jsonPath("$.route").value("/app/agenda"));
    }

    @Test void dentalReturnsStructuredProposalWithoutWriting() throws Exception {
        login("ODONTOLOGO");
        mvc.perform(post("/api/v1/corovoz/odontograma/interpretar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"corona en pieza catorce\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proposal.piece").value(14))
                .andExpect(jsonPath("$.proposal.condition").value("CORONA"))
                .andExpect(jsonPath("$.proposal.surface").value("GENERAL"));
    }

    @Test void receptionAndPatientsCannotInterpretDentalCommands() throws Exception {
        for (String role : new String[]{"RECEPCIONISTA", "PACIENTE", "ADMIN_CLINICA", "SUPER_ADMIN"}) {
            login(role);
            mvc.perform(post("/api/v1/corovoz/odontograma/interpretar").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"corona en 14\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void generalRequiresAuthorizedSession() throws Exception {
        mvc.perform(post("/api/v1/corovoz/general").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"agenda\"}"))
                .andExpect(status().isForbidden());
        login("PACIENTE");
        mvc.perform(post("/api/v1/corovoz/general").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"agenda\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void rejectsBlankAndOversizedInput() throws Exception {
        login("ODONTOLOGO");
        for (String text : new String[]{" ", "a".repeat(601)}) {
            mvc.perform(post("/api/v1/corovoz/general").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"" + text + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void rejectsInvalidPatientIdentityBeforeReadingData() throws Exception {
        login("RECEPCIONISTA");
        for (String id : new String[]{"-1", "0", "abc", "99999999999999999999"})
            mvc.perform(post("/api/v1/corovoz/general").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"y su teléfono\",\"patientId\":\"" + id + "\"}"))
                    .andExpect(status().isBadRequest());
    }
}
