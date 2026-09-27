package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.ClinicUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicUserRepository extends JpaRepository<ClinicUser, UUID> {
    List<ClinicUser> findByUsuarioId(UUID usuarioId);
    Optional<ClinicUser> findFirstByUsuarioId(UUID usuarioId);
}
