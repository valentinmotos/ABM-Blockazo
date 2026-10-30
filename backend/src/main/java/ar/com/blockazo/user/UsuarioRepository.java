package ar.com.blockazo.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
  Optional<Usuario> findByEmail(String email);
  boolean existsByEmail(String email);
  boolean existsByDni(String dni);
  Optional<Usuario> findByGoogleIssuerAndGoogleSubject(String issuer, String subject);
  boolean existsByEmailAndIdNot(String email, UUID id);
  boolean existsByDniAndIdNot(String dni, UUID id);
  @Query("select u.tokenVersion from Usuario u where u.id = :id")
  Optional<Long> findTokenVersionById(@Param("id") UUID id);
}
