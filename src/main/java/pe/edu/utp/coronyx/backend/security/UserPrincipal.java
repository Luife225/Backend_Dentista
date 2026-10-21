package pe.edu.utp.coronyx.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pe.edu.utp.coronyx.backend.model.Clinic;
import pe.edu.utp.coronyx.backend.model.ClinicUser;
import pe.edu.utp.coronyx.backend.model.Patient;
import pe.edu.utp.coronyx.backend.model.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Principal de Seguridad que encapsula Usuario, Membresía de Clínica, Clínica activa
 * y Expediente de Paciente (si aplica), resolviendo permisos y pertenencia en cada petición.
 */
public class UserPrincipal implements UserDetails {

    private final User user;
    private final ClinicUser membership;
    private final Clinic clinic;
    private final Patient patient;
    private final String rol;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(User user, ClinicUser membership, Clinic clinic, Patient patient, String rol) {
        this.user = user;
        this.membership = membership;
        this.clinic = clinic;
        this.patient = patient;
        this.rol = rol;
        this.authorities = (rol != null && !rol.isBlank())
                ? List.of(new SimpleGrantedAuthority("ROLE_" + rol.trim().toUpperCase()))
                : Collections.emptyList();
    }

    public User getUser() {
        return user;
    }

    public ClinicUser getMembership() {
        return membership;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getRol() {
        return rol;
    }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public Long getUsuarioId() {
        return getUserId();
    }

    public Long getUsuarioClinicaId() {
        return membership != null ? membership.getId() : null;
    }

    public Long getClinicaId() {
        return clinic != null ? clinic.getId() : null;
    }

    public Long getPacienteId() {
        return patient != null ? patient.getId() : null;
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equalsIgnoreCase(rol);
    }

    public boolean hasOperativeClinic() {
        return getClinicaId() != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user != null ? user.getClaveHash() : null;
    }

    @Override
    public String getUsername() {
        return user != null ? user.getCorreo() : null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user != null && !"BLOQUEADO".equalsIgnoreCase(user.getEstado());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user != null && "ACTIVO".equalsIgnoreCase(user.getEstado());
    }
}
