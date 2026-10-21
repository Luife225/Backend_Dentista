package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.HallazgoOdontograma;

import java.util.List;

@Repository
public interface HallazgoOdontogramaRepository extends JpaRepository<HallazgoOdontograma, Long> {

    List<HallazgoOdontograma> findByVersionOdontogramaId(Long versionOdontogramaId);

    void deleteByVersionOdontogramaId(Long versionOdontogramaId);
}
