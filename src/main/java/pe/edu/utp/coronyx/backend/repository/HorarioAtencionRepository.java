package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.HorarioAtencion;

import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

    List<HorarioAtencion> findByClinicaIdOrderByDiaSemanaAsc(Long clinicaId);

    List<HorarioAtencion> findByOdontologoIdOrderByDiaSemanaAsc(Long odontologoId);

    @Query("SELECT h FROM HorarioAtencion h WHERE h.clinica.id = :clinicaId AND h.odontologo IS NULL AND h.diaSemana = :diaSemana ORDER BY h.id ASC")
    List<HorarioAtencion> findClinicSchedulesForDay(@Param("clinicaId") Long clinicaId, @Param("diaSemana") Integer diaSemana);

    default Optional<HorarioAtencion> findClinicScheduleForDay(Long clinicaId, Integer diaSemana) {
        List<HorarioAtencion> list = findClinicSchedulesForDay(clinicaId, diaSemana);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("SELECT h FROM HorarioAtencion h WHERE h.odontologo.id = :odontologoId AND h.diaSemana = :diaSemana ORDER BY h.id ASC")
    List<HorarioAtencion> findDoctorSchedulesForDay(@Param("odontologoId") Long odontologoId, @Param("diaSemana") Integer diaSemana);

    default Optional<HorarioAtencion> findDoctorScheduleForDay(Long odontologoId, Integer diaSemana) {
        List<HorarioAtencion> list = findDoctorSchedulesForDay(odontologoId, diaSemana);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
