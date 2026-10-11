package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Patient;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> findLockedById(@Param("id") Long id);
    List<Patient> findAllByOrderByFechaCreacionDesc();
    List<Patient> findByClinicaIdOrderByFechaCreacionDesc(Long clinicaId);
    Optional<Patient> findByIdAndClinicaId(Long id, Long clinicaId);
    Optional<Patient> findFirstByUsuarioId(Long usuarioId);
    List<Patient> findByUsuarioId(Long usuarioId);
    long countByClinicaId(Long clinicaId);
}
