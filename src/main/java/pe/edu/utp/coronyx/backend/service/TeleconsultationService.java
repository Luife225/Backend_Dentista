package pe.edu.utp.coronyx.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.TeleconsultationDtos.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.SecurityUtils;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class TeleconsultationService {

    private final TeleconsultationSessionRepository sessionRepository;
    private final AppointmentRepository appointmentRepository;

    @Value("${coronyx.teleconsulta.jwt-secret:${jwt.secret:coronyx-dental-system-secret-key-2026-super-secure-token-jwt-utp-platform}}")
    private String jwtSecret = "coronyx-dental-system-secret-key-2026-super-secure-token-jwt-utp-platform";

    @Value("${coronyx.teleconsulta.jitsi-url:https://localhost:8443}")
    private String jitsiServerUrl = "https://localhost:8443";

    @Value("${coronyx.teleconsulta.app-id:coronyx-telemed}")
    private String appId = "coronyx-telemed";

    public TeleconsultationService(TeleconsultationSessionRepository sessionRepository,
                                  AppointmentRepository appointmentRepository) {
        this.sessionRepository = sessionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public TeleconsultationJoinResponse joinSession(Long citaId) {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no vinculada a una clínica");
        }

        Appointment cita = appointmentRepository.findByIdAndClinicaId(citaId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada en su clínica"));

        if (!"VIRTUAL".equalsIgnoreCase(cita.getModalidad())) {
            throw new IllegalStateException("La cita seleccionada es presencial; la teleconsulta solo aplica para citas virtuales");
        }

        if (Set.of("CANCELADA", "NO_ASISTIO", "FINALIZADA").contains(cita.getEstado())) {
            throw new IllegalStateException("No es posible unirse a la teleconsulta: la cita se encuentra en estado " + cita.getEstado());
        }

        // Validación estricta de autorización por rol y vínculo con la cita
        boolean isOdontologo = false;
        boolean isAdmin = "ADMIN_CLINICA".equals(principal.getRol());

        if ("PACIENTE".equals(principal.getRol())) {
            if (principal.getPacienteId() == null || !cita.getPaciente().getId().equals(principal.getPacienteId())) {
                throw new AccessDeniedException("No está autorizado para ingresar a la teleconsulta de este paciente");
            }
        } else if ("ODONTOLOGO".equals(principal.getRol())) {
            if (principal.getMembership() == null || !cita.getOdontologo().getId().equals(principal.getMembership().getId())) {
                throw new AccessDeniedException("Solo el odontólogo asignado a la cita puede ingresar como profesional");
            }
            isOdontologo = true;
        } else if (!isAdmin) {
            throw new AccessDeniedException("Rol no autorizado para participar en teleconsultas clínicas");
        }

        boolean esModerador = isOdontologo || isAdmin;

        // Recuperar o inicializar sesión de teleconsulta con sala opaca
        TeleconsultationSession session = sessionRepository.findByClinicaIdAndCitaId(principal.getClinicaId(), citaId)
                .orElse(null);

        if (session != null && "FINALIZADA".equals(session.estado)) {
            throw new IllegalStateException("La sala de teleconsulta ha sido cerrada por el profesional responsable");
        }

        if (session == null) {
            session = new TeleconsultationSession();
            session.clinica = cita.getClinica();
            session.cita = cita;
            // Identificador opaco criptográfico: NUNCA usar nombres de pacientes, DNI ni diagnósticos
            session.salaId = "cx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            session.estado = "EN_CURSO";
            session.iniciadaPor = principal.getMembership();
            session.fechaInicio = OffsetDateTime.now();
            session.fechaCreacion = OffsetDateTime.now();
            session = sessionRepository.saveAndFlush(session);
        }

        String displayName = principal.getUser().getNombres() + " " + principal.getUser().getApellidos();
        String roleLabel = esModerador ? "MODERADOR" : "PARTICIPANTE";

        // Generar JWT limitado a la sala exacta y con expiración controlada (2 horas)
        String jwt = generateJitsiToken(session.salaId, principal.getUserId().toString(), displayName,
                principal.getUser().getCorreo(), esModerador);

        return new TeleconsultationJoinResponse(
                citaId.toString(),
                session.salaId,
                jwt,
                jitsiServerUrl,
                roleLabel,
                esModerador,
                displayName,
                session.estado
        );
    }

    public TeleconsultationSessionResponse closeSession(Long citaId) {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Sesión no vinculada a una clínica");
        }

        Appointment cita = appointmentRepository.findByIdAndClinicaId(citaId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada"));

        // Solo el odontólogo asignado o el administrador de la clínica pueden cerrar la sala
        if ("PACIENTE".equals(principal.getRol())) {
            throw new AccessDeniedException("El paciente no tiene permisos para cerrar la sala de teleconsulta para todos");
        }
        if ("ODONTOLOGO".equals(principal.getRol()) && !cita.getOdontologo().getId().equals(principal.getMembership().getId())) {
            throw new AccessDeniedException("Solo el odontólogo asignado puede finalizar la sala");
        }

        TeleconsultationSession session = sessionRepository.findByClinicaIdAndCitaId(principal.getClinicaId(), citaId)
                .orElseThrow(() -> new NoSuchElementException("No existe una sesión de teleconsulta activa para esta cita"));

        session.estado = "FINALIZADA";
        session.fechaFin = OffsetDateTime.now();
        session.finalizadaPor = principal.getMembership();
        session = sessionRepository.saveAndFlush(session);

        return new TeleconsultationSessionResponse(
                session.id.toString(),
                citaId.toString(),
                session.salaId,
                session.estado,
                session.fechaInicio,
                session.fechaFin
        );
    }

    @Transactional(readOnly = true)
    public TeleconsultationSessionResponse getSessionStatus(Long citaId) {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        Appointment cita = appointmentRepository.findByIdAndClinicaId(citaId, principal.getClinicaId())
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada"));

        // Autorizar a los participantes legítimos
        if ("PACIENTE".equals(principal.getRol()) && !cita.getPaciente().getId().equals(principal.getPacienteId())) {
            throw new AccessDeniedException("Acceso denegado a la teleconsulta de otro paciente");
        }

        TeleconsultationSession session = sessionRepository.findByClinicaIdAndCitaId(principal.getClinicaId(), citaId)
                .orElseThrow(() -> new NoSuchElementException("Sesión de teleconsulta no encontrada"));

        return new TeleconsultationSessionResponse(
                session.id.toString(),
                citaId.toString(),
                session.salaId,
                session.estado,
                session.fechaInicio,
                session.fechaFin
        );
    }

    /**
     * Valida la autenticidad y correspondencia estricta de un token de sala.
     * Rechaza tokens vacíos, manipulados, expirados o pertenecientes a otra sala.
     */
    public VerifyTokenResponse verifyRoomToken(String salaId, String token) {
        if (token == null || token.isBlank()) {
            return new VerifyTokenResponse(false, salaId, null, false, "Token no proporcionado o vacío; invitados anónimos no admitidos");
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token.trim())
                    .getBody();

            if (claims.getExpiration().before(new Date())) {
                return new VerifyTokenResponse(false, salaId, null, false, "El token de teleconsulta ha expirado");
            }

            String tokenRoom = claims.get("room", String.class);
            if (tokenRoom == null || !tokenRoom.equals(salaId)) {
                return new VerifyTokenResponse(false, salaId, null, false, "El token pertenece a una sala diferente (" + tokenRoom + "); acceso denegado");
            }

            // Comprobar si la sala está finalizada en base de datos
            Optional<TeleconsultationSession> optSession = sessionRepository.findBySalaId(salaId);
            if (optSession.isPresent() && "FINALIZADA".equals(optSession.get().estado)) {
                return new VerifyTokenResponse(false, salaId, null, false, "La sala de teleconsulta ya fue finalizada por el profesional");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> context = claims.get("context", Map.class);
            boolean isModerator = false;
            String role = "PARTICIPANTE";
            if (context != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) context.get("user");
                if (user != null && Boolean.TRUE.equals(user.get("moderator"))) {
                    isModerator = true;
                    role = "MODERADOR";
                }
            }

            return new VerifyTokenResponse(true, salaId, role, isModerator, "Token válido para la sala " + salaId);
        } catch (Exception ex) {
            return new VerifyTokenResponse(false, salaId, null, false, "Firma de token inválida o token corrupto: " + ex.getMessage());
        }
    }

    private String generateJitsiToken(String room, String userId, String name, String email, boolean isModerator) {
        long now = System.currentTimeMillis();
        long expirationMs = 2 * 3600 * 1000L; // 2 horas

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("avatar", "");
        userMap.put("moderator", isModerator);

        Map<String, Object> featuresMap = new HashMap<>();
        featuresMap.put("recording", false);
        featuresMap.put("livestreaming", false);
        featuresMap.put("screen-sharing", true);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("user", userMap);
        contextMap.put("features", featuresMap);

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setIssuer(appId)
                .setAudience("jitsi")
                .setSubject("*")
                .claim("room", room)
                .claim("context", contextMap)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
