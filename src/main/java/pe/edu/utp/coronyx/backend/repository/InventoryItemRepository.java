package pe.edu.utp.coronyx.backend.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.coronyx.backend.model.InventoryItem;
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByClinicaIdOrderByNombreAsc(Long clinicId);
    Optional<InventoryItem> findByClinicaIdAndCodigo(Long clinicId, String codigo);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id and i.clinica.id = :clinicId")
    Optional<InventoryItem> lockById(@Param("id") Long id, @Param("clinicId") Long clinicId);
}
