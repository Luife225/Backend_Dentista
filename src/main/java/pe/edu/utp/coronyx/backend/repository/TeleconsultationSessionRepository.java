package pe.edu.utp.coronyx.backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.coronyx.backend.model.TeleconsultationSession;

public interface TeleconsultationSessionRepository extends JpaRepository<TeleconsultationSession, Long> {
    Optional<TeleconsultationSession> findByClinicaIdAndCitaId(Long clinicId, Long citaId);
    Optional<TeleconsultationSession> findBySalaId(String salaId);
    boolean existsByClinicaIdAndCitaId(Long clinicId, Long citaId);
}
