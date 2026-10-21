package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Appointment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(
            Long clinicaId, OffsetDateTime desde, OffsetDateTime hasta);

    List<Appointment> findByOdontologoIdAndInicioEnBetweenOrderByInicioEnAsc(
            Long odontologoId, OffsetDateTime desde, OffsetDateTime hasta);

    List<Appointment> findByPacienteIdOrderByInicioEnDesc(Long pacienteId);

    Optional<Appointment> findByIdAndClinicaId(Long id, Long clinicaId);

    List<Appointment> findByPacienteIdAndClinicaIdOrderByInicioEnDesc(Long pacienteId, Long clinicaId);

    List<Appointment> findByOdontologoIdAndClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(
            Long odontologoId, Long clinicaId, OffsetDateTime desde, OffsetDateTime hasta);

    /**
     * Valida si el odontólogo ya tiene una cita activa que se solape con el horario deseado.
     * Regla: Una cita se solapa si (inicioEn < nuevoFin) Y (finEn > nuevoInicio).
     * Excluye citas CANCELADAS o REPROGRAMADAS.
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.odontologo.id = :odontologoId " +
           "AND a.estado NOT IN ('CANCELADA', 'REPROGRAMADA') " +
           "AND (:excludeId IS NULL OR a.id != :excludeId) " +
           "AND (a.inicioEn < :finEn AND a.finEn > :inicioEn)")
    boolean existsOverlapping(
            @Param("odontologoId") Long odontologoId,
            @Param("inicioEn") OffsetDateTime inicioEn,
            @Param("finEn") OffsetDateTime finEn,
            @Param("excludeId") Long excludeId);

    /**
     * Valida si el consultorio/sillón ya está ocupado en ese rango de horario por otra cita activa.
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.clinica.id = :clinicaId " +
           "AND LOWER(a.consultorio) = LOWER(:consultorio) " +
           "AND a.estado NOT IN ('CANCELADA', 'REPROGRAMADA') " +
           "AND (:excludeId IS NULL OR a.id != :excludeId) " +
           "AND (a.inicioEn < :finEn AND a.finEn > :inicioEn)")
    boolean existsConsultorioOverlapping(
            @Param("clinicaId") Long clinicaId,
            @Param("consultorio") String consultorio,
            @Param("inicioEn") OffsetDateTime inicioEn,
            @Param("finEn") OffsetDateTime finEn,
            @Param("excludeId") Long excludeId);

    /**
     * Valida si el paciente ya tiene otra cita en el mismo rango de horario.
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.paciente.id = :pacienteId " +
           "AND a.estado NOT IN ('CANCELADA', 'REPROGRAMADA') " +
           "AND (:excludeId IS NULL OR a.id != :excludeId) " +
           "AND (a.inicioEn < :finEn AND a.finEn > :inicioEn)")
    boolean existsPacienteOverlapping(
            @Param("pacienteId") Long pacienteId,
            @Param("inicioEn") OffsetDateTime inicioEn,
            @Param("finEn") OffsetDateTime finEn,
            @Param("excludeId") Long excludeId);
}
