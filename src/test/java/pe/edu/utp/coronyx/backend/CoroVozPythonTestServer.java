package pe.edu.utp.coronyx.backend;

import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Starts the Python component for the focused CoroVoz integration tests. */
final class CoroVozPythonTestServer {
    private static Process process;
    private static int port;
    private CoroVozPythonTestServer() {}
    static synchronized void ensureStarted() {
        if (process != null && ready()) return;
        try {
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            System.setProperty("corovoz.python.url", "http://127.0.0.1:" + port);
            String python = System.getenv().getOrDefault("COROVOZ_TEST_PYTHON", "python");
            var command = new ProcessBuilder(python, "corovoz_python/server.py")
                    .directory(Path.of("").toAbsolutePath().toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            command.environment().put("COROVOZ_PYTHON_PORT", Integer.toString(port));
            process = command.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (process != null) process.destroy(); }));
            for (int attempt = 0; attempt < 40; attempt++) {
                if (ready()) return;
                Thread.sleep(100);
            }
            throw new IllegalStateException("CoroVoz Python no inició");
        } catch (Exception error) { throw new IllegalStateException("Se necesita Python para las pruebas de CoroVoz", error); }
    }
    private static boolean ready() {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/status")).timeout(Duration.ofSeconds(1)).build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }
}
