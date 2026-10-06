package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Appointment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByClinicaIdAndInicioEnBetweenOrderByInicioEnAsc(
            UUID clinicaId, OffsetDateTime desde, OffsetDateTime hasta);

    List<Appointment> findByOdontologoIdAndInicioEnBetweenOrderByInicioEnAsc(
            UUID odontologoId, OffsetDateTime desde, OffsetDateTime hasta);

    List<Appointment> findByPacienteIdOrderByInicioEnDesc(UUID pacienteId);

    /**
     * Valida si el odontólogo ya tiene una cita activa que se solape con el horario deseado.
     * Regla: Una cita se solapa si (inicioEn < nuevoFin) Y (finEn > nuevoInicio)
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.odontologo.id = :odontologoId " +
           "AND a.estado != 'CANCELADA' " +
           "AND (:excludeId IS NULL OR a.id != :excludeId) " +
           "AND (a.inicioEn < :finEn AND a.finEn > :inicioEn)")
    boolean existsOverlapping(
            @Param("odontologoId") UUID odontologoId,
            @Param("inicioEn") OffsetDateTime inicioEn,
            @Param("finEn") OffsetDateTime finEn,
            @Param("excludeId") UUID excludeId);

    /**
     * Valida si el consultorio/sillón ya está ocupado en ese rango de horario por otra cita activa.
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.clinica.id = :clinicaId " +
           "AND LOWER(a.consultorio) = LOWER(:consultorio) " +
           "AND a.estado != 'CANCELADA' " +
           "AND (:excludeId IS NULL OR a.id != :excludeId) " +
           "AND (a.inicioEn < :finEn AND a.finEn > :inicioEn)")
    boolean existsConsultorioOverlapping(
            @Param("clinicaId") UUID clinicaId,
            @Param("consultorio") String consultorio,
            @Param("inicioEn") OffsetDateTime inicioEn,
            @Param("finEn") OffsetDateTime finEn,
            @Param("excludeId") UUID excludeId);
}
