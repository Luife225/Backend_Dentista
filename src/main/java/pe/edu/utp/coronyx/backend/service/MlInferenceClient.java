package pe.edu.utp.coronyx.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP desacoplado hacia el servicio local de inferencia ML (Fase H /
 * Fase I).
 * Distingue claramente entre:
 * - Imagen inválida o formato no soportado (HTTP 400 ->
 * IllegalArgumentException)
 * - Servicio apagado, timeout o pesos no disponibles (HTTP 503/500 ->
 * MlServiceUnavailableException)
 */
@Component
public class MlInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(MlInferenceClient.class);

    private final String baseUrl;
    private final double defaultThreshold;
    private final long timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MlInferenceClient() {
        this("http://127.0.0.1:8091", 0.25, 120000L);
    }

    @Autowired
    public MlInferenceClient(
            @Value("${coronyx.ml.base-url:http://127.0.0.1:8091}") String baseUrl,
            @Value("${coronyx.ml.default-threshold:0.25}") double defaultThreshold,
            @Value("${coronyx.ml.timeout-ms:120000}") long timeoutMs) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : (baseUrl != null ? baseUrl : "http://127.0.0.1:8091");
        this.defaultThreshold = defaultThreshold;
        this.timeoutMs = Math.max(1000L, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(10000L, this.timeoutMs)))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public double getDefaultThreshold() {
        return defaultThreshold;
    }

    public Map<String, Object> checkHealth() {
        URI uri = URI.create(baseUrl + "/health");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.min(5000L, timeoutMs)))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = parseJsonMap(response.body());
            if (response.statusCode() != 200 || !Boolean.TRUE.equals(body.get("model_ready"))) {
                throw new MlServiceUnavailableException(
                        "El servicio ML respondió, pero el modelo no está listo para inferencia (HTTP "
                                + response.statusCode() + ").");
            }
            return body;
        } catch (MlServiceUnavailableException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new MlServiceUnavailableException("Tiempo de espera agotado al consultar el estado del servicio ML.",
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MlServiceUnavailableException("Consulta al servicio ML interrumpida.", ex);
        } catch (IOException ex) {
            throw new MlServiceUnavailableException(
                    "No se pudo conectar con el servicio local de inferencia ML en " + baseUrl
                            + ". Verifique que servicio_http_ml esté activo.",
                    ex);
        }
    }

    public Map<String, Object> analyzeRadiograph(byte[] imageBytes, String fileName, Double customThreshold) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("El contenido binario de la radiografía está vacío.");
        }

        double threshold = customThreshold != null ? customThreshold : defaultThreshold;
        if (Double.isNaN(threshold) || threshold < 0.01 || threshold > 0.99) {
            throw new IllegalArgumentException("El umbral de confianza ML debe estar entre 0.01 y 0.99.");
        }

        String safeFileName = (fileName != null && !fileName.isBlank())
                ? fileName.replaceAll("[\\r\\n\"\\\\]", "_")
                : "radiografia.png";

        String formattedThreshold = String.format(Locale.US, "%.4f", threshold);
        URI uri = URI.create(baseUrl + "/predict?threshold=" + formattedThreshold);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/octet-stream")
                .header("X-File-Name", safeFileName)
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            Map<String, Object> payload = parseJsonMap(response.body());
            String status = payload.get("status") != null ? String.valueOf(payload.get("status")) : "";

            if (statusCode == 400 || "invalid_image_error".equals(status)) {
                String errorMessage = extractErrorMessage(payload,
                        "La imagen enviada no es una radiografía PNG/JPEG válida.");
                throw new IllegalArgumentException(errorMessage);
            }

            if (statusCode == 503 || statusCode >= 500
                    || "model_unavailable_error".equals(status)
                    || "inference_runtime_error".equals(status)) {
                String errorMessage = extractErrorMessage(payload,
                        "El motor de inferencia ML no está disponible temporalmente (HTTP " + statusCode + ").");
                throw new MlServiceUnavailableException(errorMessage);
            }

            if (statusCode != 200) {
                throw new MlServiceUnavailableException(
                        "Respuesta inesperada del servicio ML (HTTP " + statusCode + ").");
            }

            log.info("[ML-CLIENT] Inferencia completada para '{}': status={}, findings_count={}",
                    safeFileName, status, payload.get("findings_count"));
            return payload;

        } catch (IllegalArgumentException | MlServiceUnavailableException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new MlServiceUnavailableException(
                    "El análisis de la radiografía superó el tiempo máximo permitido (" + timeoutMs + " ms).", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MlServiceUnavailableException("La petición al servicio ML fue interrumpida.", ex);
        } catch (IOException ex) {
            throw new MlServiceUnavailableException(
                    "El servicio de inferencia ML no está disponible en " + baseUrl
                            + ". Inicie el servicio local antes de analizar.",
                    ex);
        }
    }

    private Map<String, Object> parseJsonMap(String rawJson) throws IOException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IOException("El servicio ML devolvió una respuesta vacía.");
        }
        return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private String extractErrorMessage(Map<String, Object> payload, String fallback) {
        if (payload == null) {
            return fallback;
        }
        Object errObj = payload.get("error");
        if (errObj instanceof Map<?, ?> errMap) {
            Object msg = ((Map<String, Object>) errMap).get("message");
            if (msg != null && !String.valueOf(msg).isBlank()) {
                return String.valueOf(msg);
            }
        }
        return fallback;
    }
}
