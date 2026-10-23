package pe.edu.utp.coronyx.backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.coronyx.backend.model.CatalogImportBatch;

public interface CatalogImportBatchRepository extends JpaRepository<CatalogImportBatch, Long> {
    Optional<CatalogImportBatch> findByClinicaIdAndLoteId(Long clinicId, String loteId);
    boolean existsByClinicaIdAndLoteId(Long clinicId, String loteId);
}
