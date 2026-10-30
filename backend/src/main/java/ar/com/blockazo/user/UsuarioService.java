package ar.com.blockazo.user;

import ar.com.blockazo.auth.AuthDtos.*;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
  private static final Set<String> COMMON_PASSWORDS=Set.of("password1234","password12345","password123456","123456789012","012345678901","qwerty123456","qwertyuiop12","letmein123456","admin12345678","welcome123456","iloveyou12345","abc123abc123","contraseña123","contrasena123","blockazo1234","changeme12345","111111111111","000000000000","123123123123","987654321098");
  private final UsuarioRepository repo; private final PasswordEncoder encoder; private final PerfilTemplate template;
  public UsuarioService(UsuarioRepository repo, PasswordEncoder encoder, PerfilTemplate template){this.repo=repo;this.encoder=encoder;this.template=template;}
  @Transactional
  public Usuario registrar(Registro r) {
    validarPassword(r.password());
    String email=r.email().trim().toLowerCase(java.util.Locale.ROOT), dni=normalizarDni(r.dni());
    if(repo.existsByEmail(email) || repo.existsByDni(dni)) throw new IllegalArgumentException("No se pudo crear la cuenta con esos datos.");
    return repo.save(new Usuario(r.nombre().trim(),r.apellido().trim(),dni,r.fechaNacimiento(),r.direccion().trim(),email,encoder.encode(r.password())));
  }
  @Transactional(readOnly=true)
  public Usuario autenticar(Login login) {
    Usuario u=repo.findByEmail(login.email().trim().toLowerCase(java.util.Locale.ROOT)).orElseThrow(()->new BadCredentialsException("Credenciales inválidas"));
    if(!encoder.matches(login.password(),u.getPasswordHash())) throw new BadCredentialsException("Credenciales inválidas"); return u;
  }
  @Transactional(readOnly=true)
  public Usuario buscar(UUID id){ return repo.findById(id).orElseThrow(()->new BadCredentialsException("No autorizado")); }
  @Transactional
  public Usuario actualizar(UUID id, Perfil datos) {
    Usuario u=buscar(id);
    String email=datos.email().trim().toLowerCase(java.util.Locale.ROOT), dni=normalizarDni(datos.dni());
    boolean emailChanged=!email.equals(u.getEmail());
    if((emailChanged&&repo.existsByEmail(email))||repo.existsByDniAndIdNot(dni,id)) throw new IllegalArgumentException("El correo o DNI ya están registrados.");
    template.ejecutar(u,id,datos);
    return u;
  }
  private String normalizarDni(String raw){String dni=raw.replaceAll("[. -]", "");if(!dni.matches("[0-9]{7,12}"))throw new IllegalArgumentException("El DNI debe contener entre 7 y 12 dígitos.");return dni;}
  private void validarPassword(String raw){String simplified=raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");if(COMMON_PASSWORDS.contains(simplified)||simplified.matches("(.)\\1{11,}"))throw new IllegalArgumentException("Elegí una clave menos predecible.");}
  @Transactional
  public void cambiarClave(UUID id, CambioClave cambio) {
    validarPassword(cambio.claveNueva());
    Usuario u=buscar(id);
    if(!encoder.matches(cambio.claveActual(),u.getPasswordHash())) throw new BadCredentialsException("No se pudo cambiar la clave.");
    if(encoder.matches(cambio.claveNueva(),u.getPasswordHash())) throw new IllegalArgumentException("Elegí una clave distinta de la actual.");
    u.cambiarPassword(encoder.encode(cambio.claveNueva()));u.revocarSesiones();
  }
  @Transactional public void cerrarSesiones(UUID id){buscar(id).revocarSesiones();}
}
