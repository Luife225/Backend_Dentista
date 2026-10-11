package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.VersionAtencionClinica;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionAtencionClinicaRepository extends JpaRepository<VersionAtencionClinica, Long> {

    List<VersionAtencionClinica> findByAtencionClinicaIdOrderByNumeroVersionAsc(Long atencionClinicaId);

    Optional<VersionAtencionClinica> findByAtencionClinicaIdAndNumeroVersion(Long atencionClinicaId, Integer numeroVersion);

    Optional<VersionAtencionClinica> findTopByAtencionClinicaIdOrderByNumeroVersionDesc(Long atencionClinicaId);

    Optional<VersionAtencionClinica> findByIdAndClinicaId(Long id, Long clinicaId);
}
