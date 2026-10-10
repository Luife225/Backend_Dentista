package pe.edu.utp.coronyx.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.utp.coronyx.backend.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByCorreoIgnoreCase(String correo);
    boolean existsByCorreoIgnoreCase(String correo);
}
