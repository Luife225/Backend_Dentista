package pe.edu.utp.coronyx.backend.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.coronyx.backend.model.CashPayment;
public interface CashPaymentRepository extends JpaRepository<CashPayment, Long> {
    @Query("select p.cargo.id from CashPayment p where p.id = :id and p.clinica.id = :clinicId")
    Optional<Long> findChargeId(@Param("id") Long id, @Param("clinicId") Long clinicId);
    List<CashPayment> findByCargoIdOrderByFechaCreacionAsc(Long chargeId);
    Optional<CashPayment> findByClinicaIdAndSolicitudId(Long clinicId, String requestId);
    Optional<CashPayment> findByIdAndClinicaId(Long id, Long clinicId);
}
