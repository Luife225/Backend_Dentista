package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.VersionOdontograma;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionOdontogramaRepository extends JpaRepository<VersionOdontograma, Long> {

    List<VersionOdontograma> findByPacienteIdOrderByNumeroVersionDesc(Long pacienteId);

    @Query("SELECT v FROM VersionOdontograma v WHERE v.paciente.id = :pacienteId AND v.estado = 'BORRADOR'")
    Optional<VersionOdontograma> findBorradorByPacienteId(@Param("pacienteId") Long pacienteId);

    @Query("SELECT v FROM VersionOdontograma v WHERE v.paciente.id = :pacienteId AND v.estado = 'CONFIRMADO' ORDER BY v.numeroVersion DESC LIMIT 1")
    Optional<VersionOdontograma> findLatestConfirmadoByPacienteId(@Param("pacienteId") Long pacienteId);

    @Query("SELECT COALESCE(MAX(v.numeroVersion), 0) FROM VersionOdontograma v WHERE v.paciente.id = :pacienteId")
    int findMaxNumeroVersionByPacienteId(@Param("pacienteId") Long pacienteId);
}
