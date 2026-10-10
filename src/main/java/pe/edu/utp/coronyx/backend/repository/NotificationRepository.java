package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.Notification;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinatarioIdOrderByFechaCreacionDesc(Long destinatarioId);

    long countByDestinatarioIdAndEstado(Long destinatarioId, String estado);
}
