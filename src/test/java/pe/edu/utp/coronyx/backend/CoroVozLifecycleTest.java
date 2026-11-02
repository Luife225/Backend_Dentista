package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import pe.edu.utp.coronyx.backend.service.corovoz.PythonCoroVozClient;
import pe.edu.utp.coronyx.backend.service.corovoz.PythonCoroVozLifecycle;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class CoroVozLifecycleTest {
    @Test void backendStartsAndStopsLocalPythonAutomatically() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String previous = System.getProperty("corovoz.python.url");
        System.setProperty("corovoz.python.url", "http://127.0.0.1:" + port);
        try {
            var client = new PythonCoroVozClient();
            var lifecycle = new PythonCoroVozLifecycle(client);
            try {
                lifecycle.run(new DefaultApplicationArguments(new String[0]));
                assertEquals(4, client.get("/status").path("version").asInt());
            } finally { lifecycle.close(); }
            assertThrows(Exception.class, () -> client.get("/status"));
        } finally {
            if (previous == null) System.clearProperty("corovoz.python.url");
            else System.setProperty("corovoz.python.url", previous);
        }
    }
}
