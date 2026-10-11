package pe.edu.utp.coronyx.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utp.coronyx.backend.model.User;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret:coronyx-dental-system-secret-key-2026-super-secure-token-jwt-utp-platform}")
    private String secretKey;

    @Value("${jwt.expiration-ms:86400000}") // 24 horas por defecto
    private long expirationMs;

    private Key getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Genera un token JWT firmado con algoritmo HMAC-SHA256 (HS256)
     * conteniendo los claims institucionales con IDs serializados como cadenas decimales positivas.
     * Diferencia claramente usuarioId de usuarioClinicaId (membresía de clínica).
     */
    public String generateToken(User user, String rol, Long clinicaId, Long usuarioClinicaId, Long pacienteId) {
        Map<String, Object> extraClaims = new HashMap<>();
        String userIdStr = user.getId().toString();
        extraClaims.put("userId", userIdStr);
        extraClaims.put("usuarioId", userIdStr);
        extraClaims.put("nombres", user.getNombres());
        extraClaims.put("apellidos", user.getApellidos());
        extraClaims.put("rol", rol);
        extraClaims.put("sessionVersion", user.getSessionVersion().toString());

        if (usuarioClinicaId != null) {
            extraClaims.put("usuarioClinicaId", usuarioClinicaId.toString());
        }
        if (clinicaId != null) {
            extraClaims.put("clinicaId", clinicaId.toString());
        }
        if (pacienteId != null) {
            extraClaims.put("pacienteId", pacienteId.toString());
        }

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getCorreo())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(User user, String rol, Long clinicaId, Long usuarioClinicaId) {
        return generateToken(user, rol, clinicaId, usuarioClinicaId, null);
    }

    public String generateToken(User user, String rol, Long clinicaId) {
        return generateToken(user, rol, clinicaId, null, null);
    }

    /**
     * Extrae todos los claims del token
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extrae el correo (subject) del token
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Valida si el token no ha expirado y cumple con el corte de tokens heredados con UUID.
     * Cualquier token con IDs en formato UUID es rechazado tajantemente, exigiendo nuevo inicio de sesión.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (claims.getExpiration().before(new Date())) {
                return false;
            }

            // Regla de corte F01: rechazar tokens con identificadores heredados UUID
            String userId = claims.get("userId", String.class);
            if (userId != null && !userId.matches("^[1-9]\\d*$")) {
                return false; // Token heredado con UUID u otro formato incompatible
            }
            String usuarioClinicaId = claims.get("usuarioClinicaId", String.class);
            if (usuarioClinicaId != null && !usuarioClinicaId.matches("^[1-9]\\d*$")) {
                return false;
            }
            String clinicaId = claims.get("clinicaId", String.class);
            if (clinicaId != null && !clinicaId.matches("^[1-9]\\d*$")) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
