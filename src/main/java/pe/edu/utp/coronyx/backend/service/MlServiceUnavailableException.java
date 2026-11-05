package pe.edu.utp.coronyx.backend.service;

/**
 * Excepción lanzada cuando el servicio local de inferencia ML no está disponible,
 * excede el tiempo de espera o reporta ausencia/fallo de los pesos del modelo.
 * Mapeada a HTTP 503 Service Unavailable en GlobalExceptionHandler.
 */
public class MlServiceUnavailableException extends RuntimeException {

    public MlServiceUnavailableException(String message) {
        super(message);
    }

    public MlServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

