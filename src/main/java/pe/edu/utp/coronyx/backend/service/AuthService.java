package pe.edu.utp.coronyx.backend.service;

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
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ClinicUserRepository clinicUserRepository;
    private final RoleRepository roleRepository;
    private final ClinicRepository clinicRepository;

    public AuthService(UserRepository userRepository,
                       ClinicUserRepository clinicUserRepository,
                       RoleRepository roleRepository,
                       ClinicRepository clinicRepository) {
        this.userRepository = userRepository;
        this.clinicUserRepository = clinicUserRepository;
        this.roleRepository = roleRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto req) {
        String email = req.getCorreo() != null ? req.getCorreo().trim().toLowerCase() : "";
        User user = userRepository.findByCorreoIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas: no existe un usuario registrado con este correo"));

        // Verificación de clave (coincidencia directa con clave_hash)
        if (!user.getClaveHash().equals(req.getClave())) {
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
        Optional<ClinicUser> clinicUserOpt = clinicUserRepository.findFirstByUsuarioId(user.getId());
        if (clinicUserOpt.isPresent()) {
            ClinicUser cu = clinicUserOpt.get();
            if (cu.getRol() != null) {
                resp.setRol(cu.getRol().getCodigo());
            }
            if (cu.getClinica() != null) {
                resp.setClinicaId(cu.getClinica().getId());
                resp.setClinicaNombre(cu.getClinica().getNombre());
            }
        } else {
            // Si el correo o nombre coincide con superadmin
            if (email.contains("superadmin")) {
                resp.setRol("SUPER_ADMIN");
            } else {
                resp.setRol("PACIENTE");
            }
        }

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
