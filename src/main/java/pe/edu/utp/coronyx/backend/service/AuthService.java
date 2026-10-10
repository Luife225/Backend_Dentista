package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.LoginRequestDto;
import pe.edu.utp.coronyx.backend.dto.LoginResponseDto;
import pe.edu.utp.coronyx.backend.dto.UserDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicRepository;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.RoleRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final RoleRepository roleRepository;
    private final ClinicRepository clinicRepository;
    private final PatientRepository patientRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       ClinicUserRepository clinicUserRepository,
                       RoleRepository roleRepository,
                       ClinicRepository clinicRepository,
                       PatientRepository patientRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.roleRepository = roleRepository;
        this.clinicRepository = clinicRepository;
        this.patientRepository = patientRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto req) {
        String email = req.getCorreo() != null ? req.getCorreo().trim().toLowerCase() : "";
        User user = userRepository.findByCorreoIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas: no existe un usuario registrado con este correo"));

        // Verificación estricta de clave con BCrypt. Cero compatibilidad con texto plano.
        if (user.getClaveHash() == null || !passwordEncoder.matches(req.getClave(), user.getClaveHash())) {
            throw new IllegalArgumentException("Credenciales incorrectas: la contraseña ingresada no coincide");
        }

        if (!"ACTIVO".equalsIgnoreCase(user.getEstado())) {
            throw new IllegalStateException("La cuenta de usuario se encuentra en estado: " + user.getEstado());
        }

        LoginResponseDto resp = new LoginResponseDto();
        String userIdStr = user.getId().toString();
        resp.setId(userIdStr);
        resp.setUsuarioId(userIdStr);
        resp.setCorreo(user.getCorreo());
        resp.setNombres(user.getNombres());
        resp.setApellidos(user.getApellidos());
        resp.setNombreCompleto(user.getNombres() + " " + user.getApellidos());

        // Resolución estricta de rol y clínica sin fallbacks inventados
        String userRole = null;
        Long clinicaId = null;
        Long usuarioClinicaId = null;
        Long pacienteId = null;

        Optional<ClinicUser> clinicUserOpt = clinicUserRepository.findFirstByUsuarioId(user.getId());
        if (clinicUserOpt.isPresent()) {
            ClinicUser cu = clinicUserOpt.get();
            if (!"ACTIVO".equalsIgnoreCase(cu.getEstado())) {
                throw new IllegalStateException("La membresía en la clínica se encuentra en estado inactivo");
            }
            if (cu.getClinica() == null || !"ABIERTO".equalsIgnoreCase(cu.getClinica().getEstado())) {
                throw new IllegalStateException("La clínica asignada no se encuentra disponible");
            }

            usuarioClinicaId = cu.getId();
            resp.setUsuarioClinicaId(usuarioClinicaId.toString());

            if (cu.getRol() != null) {
                userRole = cu.getRol().getCodigo();
                resp.setRol(userRole);
            }
            clinicaId = cu.getClinica().getId();
            resp.setClinicaId(clinicaId.toString());
            resp.setClinicaNombre(cu.getClinica().getNombre());

            // Si es un paciente con registro clínico asociado
            if ("PACIENTE".equalsIgnoreCase(userRole)) {
                Optional<Patient> pOpt = patientRepository.findFirstByUsuarioId(user.getId());
                if (pOpt.isPresent()) {
                    pacienteId = pOpt.get().getId();
                    resp.setPacienteId(pacienteId.toString());
                }
            }
        } else if (email.contains("superadmin")) {
            // SUPER_ADMIN operativo: no tiene clínica ni membresía operativa
            userRole = "SUPER_ADMIN";
            resp.setRol(userRole);
        } else {
            // Verificar si es un paciente directo registrado sin membresía staff
            Optional<Patient> pOpt = patientRepository.findFirstByUsuarioId(user.getId());
            if (pOpt.isPresent()) {
                Patient p = pOpt.get();
                userRole = "PACIENTE";
                resp.setRol(userRole);
                pacienteId = p.getId();
                resp.setPacienteId(pacienteId.toString());
                if (p.getClinica() != null) {
                    clinicaId = p.getClinica().getId();
                    resp.setClinicaId(clinicaId.toString());
                    resp.setClinicaNombre(p.getClinica().getNombre());
                }
            } else {
                // Sin membresía ni asignación de clínica: denegar sin asignar clínica por defecto
                throw new IllegalStateException("El usuario no tiene una membresía asignada a ninguna clínica activa");
            }
        }

        // Generación y firma de Token JWT institucional con claims verificados
        String jwtToken = jwtService.generateToken(user, userRole, clinicaId, usuarioClinicaId, pacienteId);
        resp.setToken(jwtToken);
        resp.setTipoToken("Bearer");

        return resp;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto getMe(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }

        LoginResponseDto resp = new LoginResponseDto();
        String userIdStr = principal.getUserId().toString();
        resp.setId(userIdStr);
        resp.setUsuarioId(userIdStr);
        resp.setCorreo(principal.getUsername());
        resp.setNombres(principal.getUser().getNombres());
        resp.setApellidos(principal.getUser().getApellidos());
        resp.setNombreCompleto(principal.getUser().getNombres() + " " + principal.getUser().getApellidos());
        resp.setRol(principal.getRol());

        if (principal.getUsuarioClinicaId() != null) {
            resp.setUsuarioClinicaId(principal.getUsuarioClinicaId().toString());
        }
        if (principal.getClinicaId() != null) {
            resp.setClinicaId(principal.getClinicaId().toString());
            if (principal.getClinic() != null) {
                resp.setClinicaNombre(principal.getClinic().getNombre());
            }
        }
        if (principal.getPacienteId() != null) {
            resp.setPacienteId(principal.getPacienteId().toString());
        }

        return resp;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(UserPrincipal principal) {
        if (principal == null || principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso restringido: requiere sesión con clínica asignada");
        }
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo ADMIN_CLINICA puede consultar el directorio de usuarios");
        }

        Long targetClinicId = principal.getClinicaId();
        List<ClinicUser> clinicUsers = clinicUserRepository.findByClinicaId(targetClinicId);

        return clinicUsers.stream().map(cu -> {
            User u = cu.getUsuario();
            UserDto dto = new UserDto();
            dto.setId(u.getId().toString());
            dto.setUsuarioClinicaId(cu.getId().toString());
            dto.setCorreo(u.getCorreo());
            dto.setNombres(u.getNombres());
            dto.setApellidos(u.getApellidos());
            dto.setEstado(cu.getEstado());
            dto.setRol(cu.getRol() != null ? cu.getRol().getCodigo() : "SIN_ROL");
            dto.setClinicaNombre(cu.getClinica() != null ? cu.getClinica().getNombre() : null);
            return dto;
        }).collect(Collectors.toList());
    }
}
