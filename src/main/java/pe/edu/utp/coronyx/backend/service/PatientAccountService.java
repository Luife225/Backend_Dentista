package pe.edu.utp.coronyx.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Compartida por alta/edición de pacientes, vinculación explícita y creación de cuentas. */
@Service
public class PatientAccountService {
    private final UserRepository users;
    private final ClinicUserRepository memberships;
    private final PatientRepository patients;
    @PersistenceContext
    private EntityManager entityManager;

    public PatientAccountService(UserRepository users, ClinicUserRepository memberships, PatientRepository patients) {
        this.users = users;
        this.memberships = memberships;
        this.patients = patients;
    }

    @Transactional
    public void link(Patient patient, Long userId, UserPrincipal principal) {
        if (principal == null || !("ADMIN_CLINICA".equals(principal.getRol())
                || "RECEPCIONISTA".equals(principal.getRol()))) {
            throw new AccessDeniedException("Solo personal administrativo puede cambiar el vínculo de una cuenta");
        }
        if (patient.getClinica() == null || !Objects.equals(patient.getClinica().getId(), principal.getClinicaId())) {
            throw new NoSuchElementException("Paciente no encontrado en la clínica");
        }
        User target = null;
        if (userId != null) {
            if (userId <= 0) throw new IllegalArgumentException("usuarioId debe ser un entero positivo");
            // Serializa vinculaciones de distintos expedientes a la misma cuenta.
            target = lockUser(userId);
            List<ClinicUser> assigned = memberships.findByUsuarioId(userId);
            if (!"ACTIVO".equalsIgnoreCase(target.getEstado()) || assigned.size() != 1) {
                throw new IllegalArgumentException("La cuenta debe estar activa y tener una única membresía PACIENTE de esta clínica");
            }
            ClinicUser membership = assigned.getFirst();
            if (!"ACTIVO".equalsIgnoreCase(membership.getEstado())
                    || !patient.getClinica().getId().equals(membership.getClinica().getId())
                    || membership.getRol() == null || !Boolean.TRUE.equals(membership.getRol().getActivo())
                    || !"PACIENTE".equals(membership.getRol().getCodigo())) {
                throw new IllegalArgumentException("Solo se puede vincular una cuenta PACIENTE activa de esta clínica");
            }
            if (patients.findByUsuarioId(userId).stream().anyMatch(p -> !Objects.equals(p.getId(), patient.getId()))) {
                throw new IllegalStateException("La cuenta ya está vinculada a otro expediente; desvincúlela primero");
            }
        }
        Long previousId = patient.getUsuario() == null ? null : patient.getUsuario().getId();
        if (Objects.equals(previousId, userId)) return;
        if (previousId != null) {
            User previous = lockUser(previousId);
            previous.revokeSessions();
            users.save(previous);
        }
        if (target != null) {
            target.revokeSessions();
            users.save(target);
        }
        patient.setUsuario(target);
    }

    public Long parseUserId(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
        if (!value.trim().matches("[1-9]\\d*")) throw new IllegalArgumentException("usuarioId debe ser un entero positivo");
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("usuarioId está fuera del rango permitido");
        }
    }

    public User lockUser(Long id) {
        User user = users.findLockedById(id).orElseThrow(() -> new NoSuchElementException("Cuenta no encontrada"));
        // Una asociación EAGER pudo cargar esta entidad antes de adquirir el bloqueo.
        entityManager.refresh(user);
        return user;
    }
}
