package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.AusenciaOdontologo;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AusenciaOdontologoRepository extends JpaRepository<AusenciaOdontologo, Long> {

    List<AusenciaOdontologo> findByOdontologoIdOrderByInicioEnAsc(Long odontologoId);

    List<AusenciaOdontologo> findByClinicaIdOrderByInicioEnAsc(Long clinicaId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AusenciaOdontologo a " +
           "WHERE a.odontologo.id = :odontologoId " +
           "AND a.inicioEn < :finEn AND a.finEn > :inicioEn")
    boolean existsOverlappingAusencia(@Param("odontologoId") Long odontologoId,
                                      @Param("inicioEn") OffsetDateTime inicioEn,
                                      @Param("finEn") OffsetDateTime finEn);
}
