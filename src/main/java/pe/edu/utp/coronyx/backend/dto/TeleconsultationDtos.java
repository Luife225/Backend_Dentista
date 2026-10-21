package pe.edu.utp.coronyx.backend.dto;

import java.time.OffsetDateTime;

public final class TeleconsultationDtos {
    private TeleconsultationDtos() {}

    public record TeleconsultationJoinResponse(
            String citaId,
            String salaId,
            String jwtToken,
            String jitsiServerUrl,
            String rol,
            boolean esModerador,
            String nombreUsuario,
            String estadoSesion
    ) {}

    public record TeleconsultationSessionResponse(
            String id,
            String citaId,
            String salaId,
            String estado,
            OffsetDateTime fechaInicio,
            OffsetDateTime fechaFin
    ) {}

    public record VerifyTokenRequest(
            String salaId,
            String token
    ) {}

    public record VerifyTokenResponse(
            boolean valido,
            String salaId,
            String rol,
            boolean esModerador,
            String mensaje
    ) {}
}
