package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.ClinicUserDto;
import pe.edu.utp.coronyx.backend.dto.CreateClinicUserDto;
import pe.edu.utp.coronyx.backend.dto.UpdateClinicUserDto;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.Role;
import pe.edu.utp.coronyx.backend.model.User;
import pe.edu.utp.coronyx.backend.repository.ClinicUserRepository;
import pe.edu.utp.coronyx.backend.repository.PatientRepository;
import pe.edu.utp.coronyx.backend.repository.RoleRepository;
import pe.edu.utp.coronyx.backend.repository.UserRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de gestión y CRUD de cuentas/membresías en el ámbito de la clínica.
 * Exclusivo para ADMIN_CLINICA de su propia sede operativa.
 */
@Service
public class ClinicUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN_CLINICA", "ODONTOLOGO", "RECEPCIONISTA", "PACIENTE"
    );

    private final ClinicUserRepository clinicUserRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClinicUserService(ClinicUserRepository clinicUserRepository,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             PatientRepository patientRepository,
                             PasswordEncoder passwordEncoder) {
        this.clinicUserRepository = clinicUserRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private void validateAdminPrivileges(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("No se encontró una sesión activa");
        }
        if (principal.isSuperAdmin()) {
            throw new AccessDeniedException("Acceso denegado: SUPER_ADMIN no tiene acceso operativo para administrar cuentas de clínica");
        }
        if (!"ADMIN_CLINICA".equalsIgnoreCase(principal.getRol())) {
            throw new AccessDeniedException("Acceso denegado: solo ADMIN_CLINICA puede gestionar las cuentas y membresías de la clínica");
        }
        if (principal.getClinicaId() == null) {
            throw new AccessDeniedException("Acceso denegado: el administrador no tiene una clínica activa asignada");
        }
    }

    private void validateRoleAssignment(String rolCodigo) {
        if (rolCodigo == null || rolCodigo.isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        String cleanRole = rolCodigo.trim().toUpperCase();
        if ("SUPER_ADMIN".equals(cleanRole)) {
            throw new IllegalArgumentException("No se permite asignar el rol SUPER_ADMIN dentro del ámbito de la clínica.");
        }
        if (!ALLOWED_ROLES.contains(cleanRole)) {
            throw new IllegalArgumentException("El rol '" + rolCodigo + "' no es un rol autorizado dentro del incremento operativo.");
        }
    }

    @Transactional(readOnly = true)
    public List<ClinicUserDto> getClinicUsers(UserPrincipal principal) {
        validateAdminPrivileges(principal);
        Long clinicaId = principal.getClinicaId();

        return clinicUserRepository.findByClinicaIdOrderByFechaAsignacionDesc(clinicaId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClinicUserDto getClinicUserById(Long membershipId, UserPrincipal principal) {
        validateAdminPrivileges(principal);

        ClinicUser cu = clinicUserRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Membresía de usuario no encontrada con ID: " + membershipId));

        if (!cu.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Membresía de usuario no encontrada con ID: " + membershipId);
        }

        return toDto(cu);
    }

    @Transactional
    public ClinicUserDto createClinicUser(CreateClinicUserDto dto, UserPrincipal principal) {
        validateAdminPrivileges(principal);
        validateRoleAssignment(dto.getRol());

        String email = dto.getCorreo().trim().toLowerCase();
        Long clinicaId = principal.getClinicaId();
        Clinic clinic = principal.getClinic();

        // 1. Obtener o crear Usuario
        Optional<User> existingUserOpt = userRepository.findByCorreoIgnoreCase(email);
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            // Comprobar si ya tiene membresía en esta clínica
            Optional<ClinicUser> existingCu = clinicUserRepository.findByUsuarioIdAndClinicaId(user.getId(), clinicaId);
            if (existingCu.isPresent()) {
                throw new IllegalStateException("El usuario con correo '" + email + "' ya cuenta con una membresía activa en esta clínica.");
            }
        } else {
            user = new User();
            user.setCorreo(email);
            user.setClaveHash(passwordEncoder.encode(dto.getClave()));
            user.setNombres(dto.getNombres().trim());
            user.setApellidos(dto.getApellidos().trim());
            user.setEstado(dto.getEstado() != null ? dto.getEstado().toUpperCase().trim() : "ACTIVO");
            user = userRepository.save(user);
        }

        // 2. Resolver Rol
        String rolCodigo = dto.getRol().trim().toUpperCase();
        Role role = roleRepository.findByCodigo(rolCodigo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el rol con código: " + rolCodigo));

        // 3. Crear Membresía
        ClinicUser membership = new ClinicUser();
        membership.setClinica(clinic);
        membership.setUsuario(user);
        membership.setRol(role);
        membership.setRolAsignadoPor(principal.getUser());
        membership.setEstado(dto.getEstado() != null ? dto.getEstado().toUpperCase().trim() : "ACTIVO");

        ClinicUser saved = clinicUserRepository.save(membership);

        // 4. Si el rol es PACIENTE y se envió pacienteId, vincular el paciente de la clínica a este usuario
        if ("PACIENTE".equals(rolCodigo) && dto.getPacienteId() != null && !dto.getPacienteId().isBlank()) {
            try {
                Long pId = Long.parseLong(dto.getPacienteId().trim());
                Optional<Patient> pOpt = patientRepository.findById(pId);
                if (pOpt.isPresent() && pOpt.get().getClinica().getId().equals(clinicaId)) {
                    Patient p = pOpt.get();
                    p.setUsuario(user);
                    patientRepository.save(p);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return toDto(saved);
    }

    @Transactional
    public ClinicUserDto updateClinicUser(Long membershipId, UpdateClinicUserDto dto, UserPrincipal principal) {
        validateAdminPrivileges(principal);

        ClinicUser cu = clinicUserRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Membresía no encontrada con ID: " + membershipId));

        if (!cu.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Membresía no encontrada con ID: " + membershipId);
        }

        User user = cu.getUsuario();

        // 1. Actualización de datos de usuario
        if (dto.getNombres() != null && !dto.getNombres().isBlank()) {
            user.setNombres(dto.getNombres().trim());
        }
        if (dto.getApellidos() != null && !dto.getApellidos().isBlank()) {
            user.setApellidos(dto.getApellidos().trim());
        }
        if (dto.getClave() != null && !dto.getClave().isBlank()) {
            user.setClaveHash(passwordEncoder.encode(dto.getClave()));
        }

        // 2. Asignación de nuevo rol autorizado
        if (dto.getRol() != null && !dto.getRol().isBlank()) {
            validateRoleAssignment(dto.getRol());
            String rolCodigo = dto.getRol().trim().toUpperCase();

            // Protección de auto-degradación de rol
            if (user.getId().equals(principal.getUserId()) && !"ADMIN_CLINICA".equals(rolCodigo)) {
                throw new IllegalArgumentException("No puede revocar su propio rol de ADMIN_CLINICA en esta clínica.");
            }

            Role newRole = roleRepository.findByCodigo(rolCodigo)
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el rol con código: " + rolCodigo));
            cu.setRol(newRole);
        }

        // 3. Actualización de estado
        if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
            String nuevoEstado = dto.getEstado().trim().toUpperCase();
            if (!"ACTIVO".equals(nuevoEstado) && !"INACTIVO".equals(nuevoEstado) && !"DESHABILITADO".equals(nuevoEstado)) {
                throw new IllegalArgumentException("Estado no válido. Debe ser ACTIVO o INACTIVO.");
            }
            cu.setEstado("DESHABILITADO".equals(nuevoEstado) ? "INACTIVO" : nuevoEstado);
            user.setEstado("INACTIVO".equals(nuevoEstado) ? "DESHABILITADO" : nuevoEstado);
        }

        userRepository.save(user);
        ClinicUser updated = clinicUserRepository.save(cu);

        return toDto(updated);
    }

    @Transactional
    public ClinicUserDto updateClinicUserStatus(Long membershipId, String estado, UserPrincipal principal) {
        validateAdminPrivileges(principal);

        ClinicUser cu = clinicUserRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Membresía no encontrada con ID: " + membershipId));

        if (!cu.getClinica().getId().equals(principal.getClinicaId())) {
            throw new NoSuchElementException("Membresía no encontrada con ID: " + membershipId);
        }

        String nuevoEstado = estado != null ? estado.trim().toUpperCase() : "";
        if (!"ACTIVO".equals(nuevoEstado) && !"INACTIVO".equals(nuevoEstado)) {
            throw new IllegalArgumentException("Estado no válido. Debe ser ACTIVO o INACTIVO.");
        }

        // Protección contra auto-desactivación del propio administrador activo
        if (cu.getUsuario().getId().equals(principal.getUserId()) && "INACTIVO".equals(nuevoEstado)) {
            throw new IllegalArgumentException("No puede desactivar su propia cuenta o membresía activa.");
        }

        cu.setEstado(nuevoEstado);
        User user = cu.getUsuario();
        user.setEstado("INACTIVO".equals(nuevoEstado) ? "DESHABILITADO" : "ACTIVO");

        userRepository.save(user);
        ClinicUser updated = clinicUserRepository.save(cu);

        return toDto(updated);
    }

    public ClinicUserDto toDto(ClinicUser cu) {
        ClinicUserDto dto = new ClinicUserDto();
        dto.setId(cu.getId().toString());

        User u = cu.getUsuario();
        if (u != null) {
            dto.setUsuarioId(u.getId().toString());
            dto.setCorreo(u.getCorreo());
            dto.setNombres(u.getNombres());
            dto.setApellidos(u.getApellidos());
            dto.setNombreCompleto(u.getNombres() + " " + u.getApellidos());
        }

        if (cu.getRol() != null) {
            dto.setRol(cu.getRol().getCodigo());
            dto.setRolNombre(cu.getRol().getNombre());
        }

        dto.setEstado(cu.getEstado());

        if (cu.getClinica() != null) {
            dto.setClinicaId(cu.getClinica().getId().toString());
            dto.setClinicaNombre(cu.getClinica().getNombre());
        }

        if (u != null) {
            patientRepository.findFirstByUsuarioId(u.getId())
                    .ifPresent(p -> dto.setPacienteId(p.getId().toString()));
        }

        dto.setFechaAsignacion(cu.getFechaAsignacion());
        dto.setFechaActualizacion(cu.getFechaActualizacion());
        return dto;
    }
}
