package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.SessionIdentityService;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final ClinicUserRepository memberships;
    private final SessionIdentityService identities;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, ClinicUserRepository memberships,
                       SessionIdentityService identities, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.memberships = memberships;
        this.identities = identities;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        String email = request.getCorreo() == null ? "" : request.getCorreo().trim().toLowerCase(Locale.ROOT);
        User user = users.findByCorreoIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));
        if (user.getClaveHash() == null || !passwordEncoder.matches(request.getClave(), user.getClaveHash())) {
            throw new BadCredentialsException("Credenciales incorrectas");
        }
        // El correo nunca concede un rol; se resuelven vínculos y estados actuales.
        UserPrincipal principal = identities.forLogin(user);
        LoginResponseDto response = getMe(principal);
        response.setToken(jwtService.generateToken(user, principal.getRol(), principal.getClinicaId(),
                principal.getUsuarioClinicaId(), principal.getPacienteId()));
        response.setTipoToken("Bearer");
        return response;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto getMe(UserPrincipal principal) {
        if (principal == null || principal.getRol() == null || !SessionIdentityService.OPERATIVE_ROLES.contains(principal.getRol())) {
            throw new AccessDeniedException("No se encontró una sesión operativa activa");
        }
        User user = principal.getUser();
        LoginResponseDto response = new LoginResponseDto();
        response.setId(user.getId().toString());
        response.setUsuarioId(user.getId().toString());
        response.setCorreo(user.getCorreo());
        response.setNombres(user.getNombres());
        response.setApellidos(user.getApellidos());
        response.setNombreCompleto(user.getNombres() + " " + user.getApellidos());
        response.setRol(principal.getRol());
        if (principal.getUsuarioClinicaId() != null) {
            response.setUsuarioClinicaId(principal.getUsuarioClinicaId().toString());
        }
        if (principal.getClinic() != null) {
            response.setClinicaId(principal.getClinicaId().toString());
            response.setClinicaNombre(principal.getClinic().getNombre());
        }
        if (principal.getPacienteId() != null) {
            response.setPacienteId(principal.getPacienteId().toString());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null
                || !"ADMIN_CLINICA".equals(principal.getRol())) {
            throw new AccessDeniedException("Solo ADMIN_CLINICA puede consultar el directorio de su clínica");
        }
        return memberships.findByClinicaId(principal.getClinicaId()).stream().map(membership -> {
            User user = membership.getUsuario();
            UserDto dto = new UserDto();
            dto.setId(user.getId().toString());
            dto.setUsuarioClinicaId(membership.getId().toString());
            dto.setCorreo(user.getCorreo());
            dto.setNombres(user.getNombres());
            dto.setApellidos(user.getApellidos());
            dto.setEstado(membership.getEstado());
            dto.setRol(membership.getRol() == null ? "SIN_ROL" : membership.getRol().getCodigo());
            dto.setClinicaNombre(membership.getClinica().getNombre());
            return dto;
        }).toList();
    }
}
