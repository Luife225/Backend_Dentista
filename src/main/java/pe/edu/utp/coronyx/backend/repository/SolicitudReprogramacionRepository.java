package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.SolicitudReprogramacion;

import java.util.List;

@Repository
public interface SolicitudReprogramacionRepository extends JpaRepository<SolicitudReprogramacion, Long> {

    List<SolicitudReprogramacion> findByCitaIdOrderByFechaSolicitudDesc(Long citaId);

    List<SolicitudReprogramacion> findBySolicitanteUsuarioIdOrderByFechaSolicitudDesc(Long solicitanteUsuarioId);

    @Query("SELECT s FROM SolicitudReprogramacion s " +
           "JOIN s.cita c " +
           "WHERE c.clinica.id = :clinicaId " +
           "AND (:estado IS NULL OR s.estado = :estado) " +
           "ORDER BY s.fechaSolicitud DESC")
    List<SolicitudReprogramacion> findByClinicaIdAndEstado(
            @Param("clinicaId") Long clinicaId,
            @Param("estado") String estado);

    @Query("SELECT s FROM SolicitudReprogramacion s " +
           "JOIN s.cita c " +
           "WHERE c.paciente.id = :pacienteId " +
           "ORDER BY s.fechaSolicitud DESC")
    List<SolicitudReprogramacion> findByPacienteIdOrderByFechaSolicitudDesc(@Param("pacienteId") Long pacienteId);
}
