package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.coronyx.backend.controller.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.corovoz.PythonCoroVozClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoroVozSpeechTest {
    private final PythonCoroVozClient speech = mock(PythonCoroVozClient.class);
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CoroVozSpeechController(speech))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

    @AfterEach void clearSession() { SecurityContextHolder.clearContext(); }
    private void login(String role) {
        var principal = mock(UserPrincipal.class);
        when(principal.getRol()).thenReturn(role);
        when(principal.hasOperativeClinic()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test void detectsBackendVersionAndModelAvailability() throws Exception {
        login("RECEPCIONISTA");
        when(speech.get("/status")).thenReturn(json.readTree("{\"version\":4,\"speechReady\":true}"));
        mvc.perform(get("/api/v1/corovoz/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4)).andExpect(jsonPath("$.speechReady").value(true));
    }

    @Test void acceptsWavAndReturnsActualServiceTranscript() throws Exception {
        login("ODONTOLOGO");
        when(speech.audio(any(byte[].class))).thenReturn(json.readTree("{\"text\":\"corona en pieza catorce\"}"));
        mvc.perform(post("/api/v1/corovoz/transcribir").contentType("audio/wav").content(wav(16000, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("corona en pieza catorce"));
    }

    @Test void rejectsOversizedAudioBeforeProcessingAndReportsErrorsClearly() throws Exception {
        login("ODONTOLOGO");
        mvc.perform(post("/api/v1/corovoz/transcribir").contentType("audio/wav").content(new byte[960_045]))
                .andExpect(status().isPayloadTooLarge());
        verify(speech, never()).audio(any());
        when(speech.audio(any(byte[].class))).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Modelo no instalado"));
        mvc.perform(post("/api/v1/corovoz/transcribir").contentType("audio/wav").content(wav(16000, false)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Modelo no instalado"));
    }

    @Test void requiresAuthorizedRoleAndAudioFormat() throws Exception {
        login("PACIENTE");
        mvc.perform(get("/api/v1/corovoz/status")).andExpect(status().isForbidden());
        login("ODONTOLOGO");
        mvc.perform(post("/api/v1/corovoz/transcribir").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private byte[] wav(int sampleRate, boolean silence) {
        var buffer = ByteBuffer.allocate(44 + 32000).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(32036).put("WAVEfmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(32000);
        for (int i = 0; i < 16000; i++) buffer.putShort((short) (silence ? 0 : Math.sin(i * 0.1) * 12000));
        return buffer.array();
    }
}
