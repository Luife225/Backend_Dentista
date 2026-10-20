package pe.edu.utp.coronyx.backend.service.corovoz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Ejecuta Whisper local por frase. El audio temporal se elimina al terminar. */
@Service
public class LocalSpeechService {
    public static final int MAX_AUDIO_BYTES = 960_044;
    private final Path modelPath;
    private final Path executable;
    private final ObjectMapper json = new ObjectMapper();
    private final Semaphore gate = new Semaphore(1);
    private final AtomicReference<Process> running = new AtomicReference<>();

    public LocalSpeechService(
            @Value("${COROVOZ_MODEL_PATH:.corovoz/whisper/ggml-base-q5_1.bin}") String model,
            @Value("${COROVOZ_WHISPER_COMMAND:.corovoz/whisper/runtime/Release/whisper-cli.exe}") String command) {
        modelPath = resolveProjectPath(model);
        executable = resolveProjectPath(command);
    }

    private Path resolveProjectPath(String value) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (Files.exists(path) || Path.of(value).isAbsolute()) return path;
        try {
            Path source = Path.of(LocalSpeechService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; source != null && i < 5; i++, source = source.getParent()) {
                Path candidate = source.resolve(value).normalize();
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        return path;
    }

    public boolean installed() {
        try { return Files.isRegularFile(executable) && Files.isRegularFile(modelPath) && Files.size(modelPath) > 50_000_000; }
        catch (Exception e) { return false; }
    }

    public String transcribe(byte[] wav) {
        validateAudio(wav);
        if (!installed()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta instalar la voz local. Ejecuta java tools/InstallCoroVozModel.java en Backend_Dentista.");
        if (!gate.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "CoroVoz está procesando otro audio. Intenta nuevamente.");
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("corovoz-");
            Path audio = directory.resolve("input.wav"), output = directory.resolve("result");
            Files.write(audio, wav);
            List<String> command = new ArrayList<>(List.of(executable.toString(), "-m", modelPath.toString(),
                    "-f", audio.toString(), "-l", "es", "-t", "4", "-nt", "-np", "-oj", "-of", output.toString(),
                    "--no-gpu", "--no-fallback", "--prompt",
                    "Coronyx, CoroVoz, odontograma, agendar cita, pieza dental, caries, corona, oclusal, vestibular, lingual, mesial, distal, endodoncia."));
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(directory.resolve("engine.log").toFile()).start();
            running.set(process);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "La transcripción tardó demasiado. Dicta una frase más corta.");
            }
            Path result = directory.resolve("result.json");
            if (process.exitValue() != 0 || !Files.isRegularFile(result)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo iniciar la voz local. Revisa la instalación del modelo.");
            StringBuilder text = new StringBuilder();
            for (var segment : json.readTree(result.toFile()).path("transcription")) text.append(segment.path("text").asText("")).append(' ');
            String transcript = text.toString().replaceAll("\\s+", " ").trim();
            if (transcript.length() > 600) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La frase es demasiado larga. Dicta una tarea por vez.");
            return transcript;
        } catch (ResponseStatusException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La transcripción se interrumpió");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo procesar el audio local. Vuelve a intentarlo.");
        } finally {
            if (process != null) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    try { process.waitFor(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
                running.compareAndSet(process, null);
            }
            if (directory != null) {
                for (String file : List.of("input.wav", "result.json", "engine.log")) {
                    try { Files.deleteIfExists(directory.resolve(file)); } catch (Exception ignored) {}
                }
                try { Files.deleteIfExists(directory); } catch (Exception ignored) {}
            }
            gate.release();
        }
    }

    public static void validateAudio(byte[] wav) {
        if (wav.length < 44 || wav.length > MAX_AUDIO_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graba una frase de hasta 30 segundos");
        try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            var format = audio.getFormat();
            if (format.getChannels() != 1 || format.getSampleSizeInBits() != 16 || format.getSampleRate() != 16000
                    || format.isBigEndian() || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere audio WAV PCM16 mono de 16 kHz");
            }
            byte[] pcm = audio.readNBytes(MAX_AUDIO_BYTES);
            double energy = 0;
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                double sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8)) / 32768.0;
                energy += sample * sample;
            }
            if (pcm.length < 6400 || Math.sqrt(energy / Math.max(1, pcm.length / 2)) < 0.002) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se detectó voz suficiente. Acércate al micrófono y vuelve a hablar.");
            }
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El audio recibido no es WAV válido"); }
    }

    @PreDestroy public void close() {
        Process process = running.getAndSet(null);
        if (process != null && process.isAlive()) process.destroyForcibly();
    }
}
