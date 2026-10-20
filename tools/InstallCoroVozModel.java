import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

/** Desde Backend_Dentista: java tools/InstallCoroVozModel.java */
class InstallCoroVozModel {
    static final String MODEL_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898";
    static final String RUNTIME_SHA = "f9ec6c52a2e949b62ab51fa21d0d497958f9e41c3010c157c4e42932d5316f3c";
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".corovoz/whisper").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path model = root.resolve("ggml-base-q5_1.bin");
        System.out.println("Verificando modelo local Whisper base cuantizado (unos 57 MB)...");
        download("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin", model, MODEL_SHA);
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            System.out.println("Modelo instalado. Instala whisper-cli desde ggml-org/whisper.cpp y configura COROVOZ_WHISPER_COMMAND con su ruta absoluta.");
            return;
        }
        if (!Files.isRegularFile(root.resolve("runtime/Release/whisper-cli.exe"))) {
            Path archive = root.resolve("runtime.zip");
            download("https://github.com/ggml-org/whisper.cpp/releases/download/b5130/whisper-bin-x64.zip", archive, RUNTIME_SHA);
            Path runtime = root.resolve("runtime");
            Files.createDirectories(runtime);
            long total = 0;
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = runtime.resolve(entry.getName()).normalize();
                    if (!file.startsWith(runtime)) throw new IllegalStateException("Ruta no permitida en el archivo de instalación");
                    if (entry.isDirectory()) Files.createDirectories(file);
                    else {
                        Files.createDirectories(file.getParent());
                        try (var output = Files.newOutputStream(file)) {
                            byte[] bytes = new byte[16384];
                            for (int count; (count = zip.read(bytes)) != -1;) {
                                total += count;
                                if (total > 150_000_000) throw new IllegalStateException("El ejecutable supera el tamaño esperado");
                                output.write(bytes, 0, count);
                            }
                        }
                    }
                }
            }
            Files.deleteIfExists(archive);
        }
        if (!Files.isRegularFile(root.resolve("runtime/Release/whisper-cli.exe"))) throw new IllegalStateException("Instalación incompleta");
        System.out.println("CoroVoz listo: reconocimiento local, sin claves ni pagos por audio.");
    }

    static void download(String url, Path target, String expected) throws Exception {
        if (Files.isRegularFile(target) && sha(target).equals(expected)) return;
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(4)).GET().build();
        var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) { response.body().close(); throw new IllegalStateException("Descarga rechazada: HTTP " + response.statusCode()); }
        try (var input = response.body(); var output = Files.newOutputStream(partial)) {
            long total = 0;
            byte[] bytes = new byte[32768];
            for (int count; (count = input.read(bytes)) != -1;) {
                total += count;
                if (total > 180_000_000) throw new IllegalStateException("Descarga demasiado grande");
                output.write(bytes, 0, count);
            }
        }
        if (!sha(partial).equals(expected)) throw new IllegalStateException("La descarga no coincide con el SHA256 fijado");
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static String sha(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = new byte[32768];
            for (int count; (count = input.read(bytes)) != -1;) digest.update(bytes, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
