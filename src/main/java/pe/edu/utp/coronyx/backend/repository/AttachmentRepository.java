package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Attachment;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByClinicaIdAndPacienteIdOrderByFechaCreacionDesc(Long clinicaId, Long pacienteId);

    List<Attachment> findByClinicaIdAndPacienteIdAndEsVisiblePacienteTrueOrderByFechaCreacionDesc(Long clinicaId, Long pacienteId);

    Optional<Attachment> findByIdAndClinicaId(Long id, Long clinicaId);

    boolean existsByClaveAlmacenamiento(String claveAlmacenamiento);

    Optional<Attachment> findByClaveAlmacenamiento(String claveAlmacenamiento);
}
