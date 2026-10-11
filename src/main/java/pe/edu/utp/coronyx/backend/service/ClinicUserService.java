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
    private final PatientAccountService patientAccounts;

    public ClinicUserService(ClinicUserRepository clinicUserRepository,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             PatientRepository patientRepository,
                             PasswordEncoder passwordEncoder, PatientAccountService patientAccounts) {
        this.clinicUserRepository = clinicUserRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientAccounts = patientAccounts;
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

        // Dar de alta una cuenta no autoriza apropiarse de una identidad existente.
        if (userRepository.findByCorreoIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("El correo ya está registrado; edite su cuenta autorizada en lugar de reutilizarlo");
        }
        String rolCodigo = dto.getRol().trim().toUpperCase();
        Role role = activeRole(rolCodigo);
        String state = normalizeState(dto.getEstado() == null ? "ACTIVO" : dto.getEstado());
        Patient linkedPatient = null;
        if (dto.getPacienteId() != null && !dto.getPacienteId().isBlank()) {
            if (!"PACIENTE".equals(rolCodigo)) throw new IllegalArgumentException("Solo una cuenta PACIENTE puede vincularse a un expediente");
            Long patientId = patientAccounts.parseUserId(dto.getPacienteId());
            if (patientId == null) throw new IllegalArgumentException("pacienteId debe ser un entero positivo");
            linkedPatient = patientRepository.findLockedById(patientId)
                    .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado"));
            if (!clinicaId.equals(linkedPatient.getClinica().getId())) throw new NoSuchElementException("Paciente no encontrado");
            if (linkedPatient.getUsuario() != null) throw new IllegalStateException("El expediente ya tiene una cuenta vinculada");
            if (!"ACTIVO".equalsIgnoreCase(linkedPatient.getEstado())) throw new IllegalArgumentException("El expediente debe estar activo");
        }

        User user = new User(email, passwordEncoder.encode(dto.getClave()), dto.getNombres().trim(), dto.getApellidos().trim());
        user.setEstado("ACTIVO".equals(state) ? "ACTIVO" : "DESHABILITADO");
        user = userRepository.save(user);

        // 3. Crear Membresía
        ClinicUser membership = new ClinicUser();
        membership.setClinica(clinic);
        membership.setUsuario(user);
        membership.setRol(role);
        membership.setRolAsignadoPor(principal.getUser());
        membership.setEstado(state);

        ClinicUser saved = clinicUserRepository.save(membership);

        if (linkedPatient != null) {
            // La cuenta recién creada puede estar inactiva; el vínculo se valida antes de insertar.
            linkedPatient.setUsuario(user);
            patientRepository.save(linkedPatient);
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

        User user = patientAccounts.lockUser(cu.getUsuario().getId());
        ensureExclusiveAccount(user, principal.getClinicaId());
        cu.setUsuario(user);
        boolean revoke = false;

        // 1. Actualización de datos de usuario
        if (dto.getNombres() != null && !dto.getNombres().isBlank()) {
            user.setNombres(dto.getNombres().trim());
        }
        if (dto.getApellidos() != null && !dto.getApellidos().isBlank()) {
            user.setApellidos(dto.getApellidos().trim());
        }
        if (dto.getClave() != null && !dto.getClave().isBlank()) {
            user.setClaveHash(passwordEncoder.encode(dto.getClave()));
            revoke = true;
        }

        // 2. Asignación de nuevo rol autorizado
        if (dto.getRol() != null && !dto.getRol().isBlank()) {
            validateRoleAssignment(dto.getRol());
            String rolCodigo = dto.getRol().trim().toUpperCase();

            // Protección de auto-degradación de rol
            if (user.getId().equals(principal.getUserId()) && !"ADMIN_CLINICA".equals(rolCodigo)) {
                throw new IllegalArgumentException("No puede revocar su propio rol de ADMIN_CLINICA en esta clínica.");
            }

            Role newRole = activeRole(rolCodigo);
            revoke |= cu.getRol() == null || !rolCodigo.equals(cu.getRol().getCodigo());
            cu.setRol(newRole);
        }

        // 3. Actualización de estado
        if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
            String nuevoEstado = normalizeState(dto.getEstado());
            if (user.getId().equals(principal.getUserId()) && "INACTIVO".equals(nuevoEstado)) {
                throw new IllegalArgumentException("No puede desactivar su propia cuenta o membresía activa.");
            }
            String userState = "INACTIVO".equals(nuevoEstado) ? "DESHABILITADO" : "ACTIVO";
            revoke |= !nuevoEstado.equals(cu.getEstado()) || !userState.equals(user.getEstado());
            cu.setEstado(nuevoEstado);
            user.setEstado(userState);
        }

        if (revoke) user.revokeSessions();

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
        User user = patientAccounts.lockUser(cu.getUsuario().getId());
        ensureExclusiveAccount(user, principal.getClinicaId());
        cu.setUsuario(user);
        // Revoca también después de reactivar: un token previo no vuelve a ser válido.
        user.revokeSessions();
        user.setEstado("INACTIVO".equals(nuevoEstado) ? "DESHABILITADO" : "ACTIVO");

        userRepository.save(user);
        ClinicUser updated = clinicUserRepository.save(cu);

        return toDto(updated);
    }

    private Role activeRole(String code) {
        Role role = roleRepository.findByCodigo(code)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + code));
        if (!Boolean.TRUE.equals(role.getActivo())) throw new IllegalArgumentException("El rol está desactivado");
        return role;
    }

    private String normalizeState(String state) {
        String normalized = state.trim().toUpperCase();
        if ("DESHABILITADO".equals(normalized)) normalized = "INACTIVO";
        if (!Set.of("ACTIVO", "INACTIVO").contains(normalized)) {
            throw new IllegalArgumentException("Estado no válido. Debe ser ACTIVO o INACTIVO.");
        }
        return normalized;
    }

    private void ensureExclusiveAccount(User user, Long clinicId) {
        boolean otherMembership = clinicUserRepository.findByUsuarioId(user.getId()).stream()
                .anyMatch(m -> !clinicId.equals(m.getClinica().getId()));
        boolean otherPatient = patientRepository.findByUsuarioId(user.getId()).stream()
                .anyMatch(p -> !clinicId.equals(p.getClinica().getId()));
        if (otherMembership || otherPatient) {
            throw new IllegalStateException("No se puede modificar una identidad compartida con otra clínica desde esta sede");
        }
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
            List<Patient> linked = patientRepository.findByUsuarioId(u.getId());
            if ("PACIENTE".equals(dto.getRol()) && linked.size() == 1
                    && cu.getClinica().getId().equals(linked.getFirst().getClinica().getId())) {
                dto.setPacienteId(linked.getFirst().getId().toString());
            }
        }

        dto.setFechaAsignacion(cu.getFechaAsignacion());
        dto.setFechaActualizacion(cu.getFechaActualizacion());
        return dto;
    }
}
