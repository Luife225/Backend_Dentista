package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Role;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.RoleRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final RoleRepository roleRepository;
    private final ClinicRepository clinicRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       ClinicUserRepository clinicUserRepository,
                       RoleRepository roleRepository,
                       ClinicRepository clinicRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.roleRepository = roleRepository;
        this.clinicRepository = clinicRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto req) {
        String email = req.getCorreo() != null ? req.getCorreo().trim().toLowerCase() : "";
        User user = userRepository.findByCorreoIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas: no existe un usuario registrado con este correo"));

        // Verificación de clave con BCrypt (y compatibilidad/migración automática en caliente para semillas iniciales)
        boolean passwordMatches = false;
        String storedHash = user.getClaveHash();

        if (storedHash != null) {
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                passwordMatches = passwordEncoder.matches(req.getClave(), storedHash);
            } else {
                // Si la semilla previa era texto plano, verificamos y actualizamos de inmediato a hash BCrypt en PostgreSQL
                passwordMatches = storedHash.equals(req.getClave());
                if (passwordMatches) {
                    user.setClaveHash(passwordEncoder.encode(req.getClave()));
                    userRepository.save(user);
                }
            }
        }

        if (!passwordMatches) {
            throw new IllegalArgumentException("Credenciales incorrectas: la contraseña ingresada no coincide");
        }

        if (!"ACTIVO".equalsIgnoreCase(user.getEstado())) {
            throw new IllegalStateException("La cuenta de usuario se encuentra en estado: " + user.getEstado());
        }

        LoginResponseDto resp = new LoginResponseDto();
        resp.setId(user.getId());
        resp.setCorreo(user.getCorreo());
        resp.setNombres(user.getNombres());
        resp.setApellidos(user.getApellidos());
        resp.setNombreCompleto(user.getNombres() + " " + user.getApellidos());

        // Obtener rol y clínica asociada
        String userRole = "PACIENTE";
        UUID clinicaId = null;

        Optional<ClinicUser> clinicUserOpt = clinicUserRepository.findFirstByUsuarioId(user.getId());
        if (clinicUserOpt.isPresent()) {
            ClinicUser cu = clinicUserOpt.get();
            if (cu.getRol() != null) {
                userRole = cu.getRol().getCodigo();
                resp.setRol(userRole);
            }
            if (cu.getClinica() != null) {
                clinicaId = cu.getClinica().getId();
                resp.setClinicaId(clinicaId);
                resp.setClinicaNombre(cu.getClinica().getNombre());
            }
        } else {
            if (email.contains("superadmin")) {
                userRole = "SUPER_ADMIN";
            }
            resp.setRol(userRole);
        }

        // Generación y firma de Token JWT institucional (HS256)
        String jwtToken = jwtService.generateToken(user, userRole, clinicaId);
        resp.setToken(jwtToken);
        resp.setTipoToken("Bearer");

        return resp;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(u -> {
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setCorreo(u.getCorreo());
            dto.setNombres(u.getNombres());
            dto.setApellidos(u.getApellidos());
            dto.setEstado(u.getEstado());

            Optional<ClinicUser> cuOpt = clinicUserRepository.findFirstByUsuarioId(u.getId());
            if (cuOpt.isPresent() && cuOpt.get().getRol() != null) {
                dto.setRol(cuOpt.get().getRol().getCodigo());
                if (cuOpt.get().getClinica() != null) {
                    dto.setClinicaNombre(cuOpt.get().getClinica().getNombre());
                }
            } else if (u.getCorreo().contains("superadmin")) {
                dto.setRol("SUPER_ADMIN");
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
