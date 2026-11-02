package pe.edu.utp.coronyx.backend.service.corovoz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Authorized Spring endpoints call the local Python AI service; no clinical writes are delegated. */
@Service
public class PythonCoroVozClient {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String base;
    private final String token;

    public PythonCoroVozClient() {
        base = System.getProperty("corovoz.python.url", System.getenv().getOrDefault("COROVOZ_PYTHON_URL", "http://127.0.0.1:8787")).replaceAll("/+$", "");
        token = System.getenv().getOrDefault("COROVOZ_SERVICE_TOKEN", "");
        URI uri = URI.create(base);
        boolean loopback = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()) || "::1".equals(uri.getHost());
        if (!loopback && (token.isBlank() || !"https".equals(uri.getScheme())))
            throw new IllegalStateException("CoroVoz remoto requiere HTTPS y COROVOZ_SERVICE_TOKEN");
    }

    public JsonNode get(String path) { return call(path, "GET", null, null); }
    public URI baseUri() { return URI.create(base); }
    public JsonNode post(String path, Object body) {
        try { return call(path, "POST", json.writeValueAsBytes(body), "application/json"); }
        catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo preparar CoroVoz", e); }
    }
    public JsonNode audio(byte[] body) { return call("/transcribir", "POST", body, "audio/wav"); }

    private JsonNode call(String path, String method, byte[] body, String contentType) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(65));
            if (!token.isBlank()) builder.header("X-CoroVoz-Token", token);
            if (contentType != null) builder.header("Content-Type", contentType);
            var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode data = json.readTree(response.body());
            if (response.statusCode() >= 400) {
                String message = data.path("message").asText("CoroVoz no pudo completar la solicitud");
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), message);
            }
            return data;
        } catch (ResponseStatusException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CoroVoz se interrumpió", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio Python de CoroVoz no está disponible", e);
        }
    }
}
