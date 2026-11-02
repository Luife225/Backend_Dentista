package pe.edu.utp.coronyx.backend.service.corovoz;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Starts the Python AI alongside Spring so CoroVoz keeps its one-command startup. */
@Component
public class PythonCoroVozLifecycle implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PythonCoroVozLifecycle.class);
    private final PythonCoroVozClient client;
    private Process child;

    public PythonCoroVozLifecycle(PythonCoroVozClient client) { this.client = client; }

    @Override public synchronized void run(ApplicationArguments args) {
        if ("false".equalsIgnoreCase(System.getenv().getOrDefault("COROVOZ_PYTHON_AUTOSTART", "true"))) return;
        URI url = client.baseUri();
        if (!"127.0.0.1".equals(url.getHost()) && !"localhost".equals(url.getHost()) && !"::1".equals(url.getHost())) return;
        if (ready()) return;
        Path script = findScript();
        if (script == null) {
            log.warn("CoroVoz Python no inició: no se encontró corovoz_python/server.py");
            return;
        }
        try {
            Path root = script.getParent().getParent();
            Path logs = root.resolve(".corovoz");
            Files.createDirectories(logs);
            String python = System.getenv().getOrDefault("COROVOZ_PYTHON_COMMAND", "python");
            var builder = new ProcessBuilder(python, script.toString()).directory(root.toFile())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("python.log").toFile()));
            builder.environment().put("COROVOZ_PYTHON_HOST", "127.0.0.1");
            builder.environment().put("COROVOZ_PYTHON_PORT", Integer.toString(url.getPort() < 0 ? 80 : url.getPort()));
            child = builder.start();
            for (int attempt = 0; attempt < 30; attempt++) {
                if (ready()) {
                    log.info("CoroVoz Python iniciado en {}", url);
                    return;
                }
                if (!child.isAlive()) break;
                Thread.sleep(100);
            }
            log.warn("CoroVoz Python no respondió; revisa .corovoz/python.log");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Arranque de CoroVoz Python interrumpido");
        } catch (Exception error) {
            log.warn("CoroVoz Python no inició: {}", error.getMessage());
        }
    }

    private boolean ready() {
        try { return client.get("/status").path("version").asInt() >= 4; }
        catch (Exception ignored) { return false; }
    }

    private Path findScript() {
        String configured = System.getenv("COROVOZ_PYTHON_SCRIPT");
        if (configured != null && !configured.isBlank()) {
            Path explicit = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        Path location = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && location != null; i++, location = location.getParent()) {
            Path direct = location.resolve("corovoz_python/server.py");
            if (Files.isRegularFile(direct)) return direct;
            Path nested = location.resolve("Backend_Dentista/corovoz_python/server.py");
            if (Files.isRegularFile(nested)) return nested;
        }
        try {
            location = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; i < 6 && location != null; i++, location = location.getParent()) {
                Path candidate = location.resolve("corovoz_python/server.py");
                if (Files.isRegularFile(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @PreDestroy public synchronized void close() {
        if (child != null && child.isAlive()) {
            child.destroy();
            try {
                if (!child.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) child.destroyForcibly();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                child.destroyForcibly();
            }
        }
    }
}
