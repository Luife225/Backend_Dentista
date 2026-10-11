package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.ClinicUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicUserRepository extends JpaRepository<ClinicUser, Long> {
    List<ClinicUser> findByUsuarioId(Long usuarioId);
    Optional<ClinicUser> findFirstByUsuarioId(Long usuarioId);
    List<ClinicUser> findByClinicaId(Long clinicaId);
    List<ClinicUser> findByClinicaIdOrderByFechaAsignacionDesc(Long clinicaId);
    Optional<ClinicUser> findByUsuarioIdAndClinicaId(Long usuarioId, Long clinicaId);
}
