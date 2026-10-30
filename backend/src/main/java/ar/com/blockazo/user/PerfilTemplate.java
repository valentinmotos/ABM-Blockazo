package ar.com.blockazo.user;

import ar.com.blockazo.auth.AuthDtos.Perfil;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Template Method fija autorización y validación antes de mutar el perfil. */
@Component
public final class PerfilTemplate {
  public final void ejecutar(Usuario target, UUID actorId, Perfil datos) {
    if (target.getId() == null || !target.getId().equals(actorId)) throw new org.springframework.security.access.AccessDeniedException("No autorizado");
    validar(datos);
    aplicar(target, datos);
  }
  private void validar(Perfil p) {
    if (p.fechaNacimiento().isAfter(LocalDate.now())) throw new IllegalArgumentException("Fecha de nacimiento inválida");
  }
  private void aplicar(Usuario u, Perfil p) {
    String email=p.email().trim().toLowerCase(java.util.Locale.ROOT);
    String dni=p.dni().replaceAll("[. -]", "");
    u.actualizar(p.nombre().trim(),p.apellido().trim(),dni,p.fechaNacimiento(),p.direccion().trim(),email);
  }
}
