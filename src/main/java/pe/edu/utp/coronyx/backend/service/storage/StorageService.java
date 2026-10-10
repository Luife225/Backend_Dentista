package pe.edu.utp.coronyx.backend.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Contrato de almacenamiento desacoplado de binarios duraderos para Coronyx (F00 / F06).
 * Soporta implementaciones locales duraderas (fuera del sistema de archivos efímero de contenedores)
 * y compatibles con buckets de objetos (S3 / Railway persistent volumes).
 */
public interface StorageService {

    /**
     * Almacena un archivo MultipartFile de forma durable y retorna su clave_almacenamiento única.
     */
    String store(MultipartFile file, Long clinicaId, Long pacienteId);

    /**
     * Almacena un flujo de bytes con metadatos explícitos y retorna la clave_almacenamiento.
     */
    String store(InputStream inputStream, String originalFilename, String contentType, long size, Long clinicaId, Long pacienteId);

    /**
     * Carga el recurso binario a partir de la clave de almacenamiento.
     */
    Resource loadAsResource(String storageKey);

    /**
     * Elimina físicamente el archivo binario correspondiente a la clave de almacenamiento.
     * Crucial para la compensación de fallos cuando una inserción en base de datos falla.
     */
    void delete(String storageKey);

    /**
     * Comprueba si el archivo binario existe físicamente en el almacenamiento durable.
     */
    boolean exists(String storageKey);

    /**
     * Obtiene la ruta raíz configurada del almacenamiento durable.
     */
    Path getRootLocation();
}
