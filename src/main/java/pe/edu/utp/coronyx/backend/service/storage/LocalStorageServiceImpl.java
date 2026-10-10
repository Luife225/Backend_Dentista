package pe.edu.utp.coronyx.backend.service.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Adaptador duradero de almacenamiento en sistema de archivos local / volumen persistente.
 * Configurado externamente para evitar almacenamiento efímero en contenedores Railway.
 */
@Service
public class LocalStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    private final Path rootLocation;

    public LocalStorageServiceImpl(@Value("${storage.local.directory}") String storageDir) {
        if (storageDir == null || storageDir.isBlank()) {
            this.rootLocation = Paths.get(System.getProperty("user.home"), ".coronyx", "storage").toAbsolutePath().normalize();
        } else {
            this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        }
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
            log.info("[STORAGE] Adaptador de almacenamiento durable inicializado en: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("[STORAGE] Error crítico al inicializar directorio de almacenamiento: {}", this.rootLocation, e);
            throw new IllegalStateException("No se pudo inicializar el almacenamiento duradero en " + this.rootLocation, e);
        }
    }

    @Override
    public String store(MultipartFile file, Long clinicaId, Long pacienteId) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("El archivo binario no puede estar vacío ni tener tamaño 0.");
        }
        try (InputStream is = file.getInputStream()) {
            return store(is, file.getOriginalFilename(), file.getContentType(), file.getSize(), clinicaId, pacienteId);
        } catch (IOException e) {
            throw new RuntimeException("Error de E/S al leer el flujo binario del archivo", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String originalFilename, String contentType, long size, Long clinicaId, Long pacienteId) {
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño real del archivo debe ser mayor a 0 bytes.");
        }
        if (clinicaId == null || pacienteId == null) {
            throw new IllegalArgumentException("Identificadores de clínica y paciente requeridos para estructurar la ruta de almacenamiento.");
        }

        String safeOriginalName = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "archivo.bin";

        // Estructura de clave duradera con UUID para garantizar unicidad y cero sobrescritura
        String uniqueName = UUID.randomUUID() + "_" + safeOriginalName;
        String relativeKey = String.format("clinica_%d/paciente_%d/%s", clinicaId, pacienteId, uniqueName);

        Path destinationFile = this.rootLocation.resolve(relativeKey).normalize();

        // Control de seguridad: prevenir Path Traversal
        if (!destinationFile.startsWith(this.rootLocation)) {
            throw new SecurityException("Intento de acceso denegado: ruta fuera del directorio raíz de almacenamiento.");
        }

        try {
            Files.createDirectories(destinationFile.getParent());
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("[STORAGE] Archivo binario almacenado con éxito: key={}, size={} bytes", relativeKey, size);
            return relativeKey;
        } catch (IOException e) {
            log.error("[STORAGE] Fallo al persistir archivo en disco: key={}", relativeKey, e);
            try {
                Files.deleteIfExists(destinationFile);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Fallo al escribir binario en almacenamiento durable: " + e.getMessage(), e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("La clave de almacenamiento no puede ser nula o vacía.");
        }

        Path file = this.rootLocation.resolve(storageKey).normalize();

        if (!file.startsWith(this.rootLocation)) {
            throw new SecurityException("Intento de acceso denegado: ruta fuera del directorio de almacenamiento.");
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("El archivo binario no fue encontrado o no es legible en: " + storageKey);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error al resolver URL del recurso binario: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path file = this.rootLocation.resolve(storageKey).normalize();
        if (!file.startsWith(this.rootLocation)) {
            log.warn("[STORAGE] Intento de eliminación fuera del directorio raíz bloqueado: {}", storageKey);
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("[STORAGE] Archivo binario compensado/eliminado con éxito: {}", storageKey);
            }
        } catch (IOException e) {
            log.error("[STORAGE] No se pudo eliminar el archivo físico durante la compensación: {}", storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        Path file = this.rootLocation.resolve(storageKey).normalize();
        return file.startsWith(this.rootLocation) && Files.exists(file);
    }

    @Override
    public Path getRootLocation() {
        return this.rootLocation;
    }
}
