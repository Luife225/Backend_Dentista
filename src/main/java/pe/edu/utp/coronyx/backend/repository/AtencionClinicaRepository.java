package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.AtencionClinica;

import java.util.List;
import java.util.Optional;

@Repository
public interface AtencionClinicaRepository extends JpaRepository<AtencionClinica, Long> {

    @Query("SELECT DISTINCT a FROM AtencionClinica a " +
           "LEFT JOIN FETCH a.versiones v " +
           "WHERE a.paciente.id = :pacienteId AND a.clinica.id = :clinicaId " +
           "ORDER BY a.fechaCreacion DESC")
    List<AtencionClinica> findByPacienteIdAndClinicaIdWithVersions(
            @Param("pacienteId") Long pacienteId,
            @Param("clinicaId") Long clinicaId
    );

    @Query("SELECT a FROM AtencionClinica a " +
           "LEFT JOIN FETCH a.versiones v " +
           "WHERE a.id = :id AND a.clinica.id = :clinicaId")
    Optional<AtencionClinica> findByIdAndClinicaIdWithVersions(
            @Param("id") Long id,
            @Param("clinicaId") Long clinicaId
    );

    Optional<AtencionClinica> findByIdAndClinicaId(Long id, Long clinicaId);

    Optional<AtencionClinica> findByCitaId(Long citaId);

    boolean existsByCitaId(Long citaId);
}
