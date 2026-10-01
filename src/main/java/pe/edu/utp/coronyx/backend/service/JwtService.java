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
import java.util.UUID;

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
     * conteniendo los claims institucionales del usuario, rol y sede clínica.
     */
    public String generateToken(User user, String rol, UUID clinicaId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("nombres", user.getNombres());
        extraClaims.put("apellidos", user.getApellidos());
        extraClaims.put("rol", rol);
        if (clinicaId != null) {
            extraClaims.put("clinicaId", clinicaId.toString());
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
     * Valida si el token no ha expirado
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
