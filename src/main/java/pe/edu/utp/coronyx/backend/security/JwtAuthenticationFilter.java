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
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.service.JwtService;

import java.io.IOException;
import java.util.Optional;

/**
 * Filtro de autenticación JWT que valida el token y revalida en tiempo real
 * contra la base de datos el estado activo del usuario, su membresía y la clínica en cada petición.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final ClinicRepository clinicRepository;
    private final PatientRepository patientRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   ClinicUserRepository clinicUserRepository,
                                   ClinicRepository clinicRepository,
                                   PatientRepository patientRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.clinicRepository = clinicRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();

        if (!jwtService.isTokenValid(jwt)) {
            log.warn("[SEC] Token inválido o expirado en petición: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.extractAllClaims(jwt);
            String userIdStr = claims.get("userId", String.class);
            if (userIdStr == null || !userIdStr.matches("^[1-9]\\d*$")) {
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = Long.parseLong(userIdStr);
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                log.warn("[SEC] Usuario ID {} del token no existe en BD", userId);
                filterChain.doFilter(request, response);
                return;
            }

            User user = userOpt.get();

            // Validación en tiempo real: el usuario debe estar ACTIVO
            if (!"ACTIVO".equalsIgnoreCase(user.getEstado())) {
                log.warn("[SEC] Usuario {} se encuentra desactivado ({})", user.getCorreo(), user.getEstado());
                filterChain.doFilter(request, response);
                return;
            }

            String tokenRol = claims.get("rol", String.class);
            String usuarioClinicaIdStr = claims.get("usuarioClinicaId", String.class);
            String clinicaIdStr = claims.get("clinicaId", String.class);
            String pacienteIdStr = claims.get("pacienteId", String.class);

            ClinicUser membership = null;
            Clinic clinic = null;
            Patient patient = null;
            String effectiveRol = tokenRol;

            if (usuarioClinicaIdStr != null && usuarioClinicaIdStr.matches("^[1-9]\\d*$")) {
                Long cuId = Long.parseLong(usuarioClinicaIdStr);
                Optional<ClinicUser> cuOpt = clinicUserRepository.findById(cuId);
                if (cuOpt.isPresent()) {
                    membership = cuOpt.get();
                    // Verificar correspondencia estricta con el usuario
                    if (!membership.getUsuario().getId().equals(user.getId())) {
                        log.warn("[SEC] Discrepancia: membresía {} no corresponde a usuario {}", cuId, user.getId());
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // Validación en tiempo real: membresía debe estar ACTIVA
                    if (!"ACTIVO".equalsIgnoreCase(membership.getEstado())) {
                        log.warn("[SEC] Membresía {} se encuentra inactiva ({})", cuId, membership.getEstado());
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // Validación en tiempo real: clínica debe estar ABIERTA
                    clinic = membership.getClinica();
                    if (clinic == null || !"ABIERTO".equalsIgnoreCase(clinic.getEstado())) {
                        log.warn("[SEC] Clínica {} no se encuentra abierta", clinic != null ? clinic.getId() : "null");
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (membership.getRol() != null) {
                        effectiveRol = membership.getRol().getCodigo();
                    }
                } else {
                    log.warn("[SEC] Membresía {} no encontrada en BD", cuId);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            if ("SUPER_ADMIN".equalsIgnoreCase(effectiveRol)) {
                // SUPER_ADMIN operativo queda expresamente sin clínica ni membresía
                membership = null;
                clinic = null;
                effectiveRol = "SUPER_ADMIN";
            } else if ("PACIENTE".equalsIgnoreCase(effectiveRol) || pacienteIdStr != null) {
                effectiveRol = "PACIENTE";
                if (pacienteIdStr != null && pacienteIdStr.matches("^[1-9]\\d*$")) {
                    patient = patientRepository.findById(Long.parseLong(pacienteIdStr)).orElse(null);
                }
                if (patient == null) {
                    patient = patientRepository.findFirstByUsuarioId(user.getId()).orElse(null);
                }
                if (patient != null && clinic == null) {
                    clinic = patient.getClinica();
                } else if (clinic == null && clinicaIdStr != null && clinicaIdStr.matches("^[1-9]\\d*$")) {
                    clinic = clinicRepository.findById(Long.parseLong(clinicaIdStr)).orElse(null);
                }
            }

            UserPrincipal principal = new UserPrincipal(user, membership, clinic, patient, effectiveRol);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.error("[SEC] Error en procesamiento de autenticación: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
