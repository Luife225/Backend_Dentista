package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Patient;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findAllByOrderByFechaCreacionDesc();
    List<Patient> findByClinicaIdOrderByFechaCreacionDesc(Long clinicaId);
    Optional<Patient> findByIdAndClinicaId(Long id, Long clinicaId);
    Optional<Patient> findFirstByUsuarioId(Long usuarioId);
    List<Patient> findByUsuarioId(Long usuarioId);
    long countByClinicaId(Long clinicaId);
}
