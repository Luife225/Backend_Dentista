package pe.edu.utp.coronyx.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.service.JwtService;

import java.io.IOException;
import java.util.Objects;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserRepository users;
    private final SessionIdentityService identities;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, SessionIdentityService identities) {
        this.jwtService = jwtService;
        this.users = users;
        this.identities = identities;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.clearContext();
        String jwt = header.substring(7).trim();
        try {
            Claims claims = jwtService.extractAllClaims(jwt);
            String role = claims.get("rol", String.class);
            if (!SessionIdentityService.OPERATIVE_ROLES.contains(role == null ? "" : role)) {
                throw new IllegalArgumentException("Rol fuera del incremento operativo");
            }
            User user = users.findById(id(claims.get("userId", String.class)))
                    .orElseThrow(() -> new IllegalArgumentException("Cuenta no vigente"));
            String version = claims.get("sessionVersion", String.class);
            if (version == null || !version.matches("0|[1-9]\\d*")
                    || !user.getSessionVersion().equals(Long.valueOf(version))) {
                throw new IllegalArgumentException("Sesión revocada; vuelva a iniciar sesión");
            }
            if (!Objects.equals(user.getCorreo(), claims.getSubject())) {
                throw new IllegalArgumentException("Identidad del token no vigente");
            }
            String membershipClaim = claims.get("usuarioClinicaId", String.class);
            Long membershipId = membershipClaim == null ? null : id(membershipClaim);
            UserPrincipal principal = identities.resolve(user, membershipId);
            Long clinicId = id(claims.get("clinicaId", String.class));
            String patientClaim = claims.get("pacienteId", String.class);
            Long patientId = patientClaim == null ? null : id(patientClaim);
            if (!role.equals(principal.getRol()) || !clinicId.equals(principal.getClinicaId())
                    || !Objects.equals(patientId, principal.getPacienteId())) {
                throw new IllegalArgumentException("El rol, clínica o expediente de la sesión cambió");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            // Spring responde 401 en endpoints protegidos; no se exponen claims ni tokens.
            SecurityContextHolder.clearContext();
            log.debug("Sesión rechazada en {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        }
        chain.doFilter(request, response);
    }

    private Long id(String value) {
        if (value == null || !value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException("Identificador de sesión inválido");
        }
        return Long.valueOf(value);
    }
}
