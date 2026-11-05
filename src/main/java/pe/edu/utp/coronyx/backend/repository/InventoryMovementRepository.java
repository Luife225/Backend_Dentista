package pe.edu.utp.coronyx.backend.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.coronyx.backend.model.InventoryMovement;
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    boolean existsByInsumoId(Long insumoId);
    List<InventoryMovement> findByInsumoIdOrderByFechaCreacionDesc(Long itemId);
    Optional<InventoryMovement> findByClinicaIdAndSolicitudId(Long clinicId, String requestId);
}
