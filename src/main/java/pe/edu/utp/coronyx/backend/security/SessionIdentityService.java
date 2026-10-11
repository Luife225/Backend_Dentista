package pe.edu.utp.coronyx.backend.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;

import java.util.List;
import java.util.Set;

/** Una sola política de identidad para login y cada petición autenticada. */
@Service
@Transactional(readOnly = true)
public class SessionIdentityService {
    public static final Set<String> OPERATIVE_ROLES = Set.of(
            "ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE");

    private final ClinicUserRepository memberships;
    private final PatientRepository patients;
    private final RoleRepository roles;

    public SessionIdentityService(ClinicUserRepository memberships, PatientRepository patients, RoleRepository roles) {
        this.memberships = memberships;
        this.patients = patients;
        this.roles = roles;
    }

    public UserPrincipal forLogin(User user) {
        List<ClinicUser> assigned = memberships.findByUsuarioId(user.getId());
        if (assigned.size() > 1) {
            throw invalid("La cuenta tiene varias membresías; requiere revisión de su asignación de clínica");
        }
        return resolve(user, assigned.isEmpty() ? null : assigned.getFirst().getId());
    }

    public UserPrincipal resolve(User user, Long membershipId) {
        if (!"ACTIVO".equalsIgnoreCase(user.getEstado())) {
            throw invalid("La cuenta de usuario no está activa");
        }
        ClinicUser membership = null;
        Clinic clinic;
        Patient patient = null;
        String role;
        if (membershipId != null) {
            membership = memberships.findById(membershipId).orElseThrow(() -> invalid("Membresía no vigente"));
            if (!user.getId().equals(membership.getUsuario().getId())
                    || !"ACTIVO".equalsIgnoreCase(membership.getEstado())) {
                throw invalid("Membresía no vigente para el usuario");
            }
            Role assignedRole = membership.getRol();
            if (assignedRole == null || !Boolean.TRUE.equals(assignedRole.getActivo())
                    || !OPERATIVE_ROLES.contains(assignedRole.getCodigo())) {
                throw invalid("El rol asignado no está habilitado en esta entrega");
            }
            role = assignedRole.getCodigo();
            clinic = membership.getClinica();
            if ("PACIENTE".equals(role)) {
                patient = uniquePatient(user);
                if (clinic == null || !clinic.getId().equals(patient.getClinica().getId())) {
                    throw invalid("El expediente no pertenece a la clínica de la cuenta");
                }
            }
        } else {
            // No permite que un token antiguo sin membresía eluda una asignación nueva.
            if (!memberships.findByUsuarioId(user.getId()).isEmpty()) {
                throw invalid("La asignación de la cuenta cambió; vuelva a iniciar sesión");
            }
            Role patientRole = roles.findByCodigo("PACIENTE").orElseThrow(() -> invalid("Rol PACIENTE no disponible"));
            if (!Boolean.TRUE.equals(patientRole.getActivo())) {
                throw invalid("Rol PACIENTE desactivado");
            }
            patient = uniquePatient(user);
            clinic = patient.getClinica();
            role = "PACIENTE";
        }
        if (clinic == null || !"ABIERTO".equalsIgnoreCase(clinic.getEstado())) {
            throw invalid("La clínica asignada no está abierta");
        }
        return new UserPrincipal(user, membership, clinic, patient, role);
    }

    private Patient uniquePatient(User user) {
        List<Patient> linked = patients.findByUsuarioId(user.getId());
        if (linked.size() != 1 || !"ACTIVO".equalsIgnoreCase(linked.getFirst().getEstado())
                || linked.getFirst().getClinica() == null) {
            throw invalid("La cuenta necesita un único expediente activo vinculado para acceder al portal");
        }
        return linked.getFirst();
    }

    private BadCredentialsException invalid(String message) {
        return new BadCredentialsException(message);
    }
}
