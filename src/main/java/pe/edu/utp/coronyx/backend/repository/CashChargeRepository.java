package pe.edu.utp.coronyx.backend.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.coronyx.backend.model.CashCharge;
public interface CashChargeRepository extends JpaRepository<CashCharge, Long> {
    List<CashCharge> findByClinicaIdOrderByFechaCreacionDesc(Long clinicId);
    Optional<CashCharge> findByClinicaIdAndSolicitudId(Long clinicId, String requestId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashCharge c where c.id = :id and c.clinica.id = :clinicId")
    Optional<CashCharge> lockById(@Param("id") Long id, @Param("clinicId") Long clinicId);
}
