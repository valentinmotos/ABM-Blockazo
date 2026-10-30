package ar.com.blockazo.auth;

import ar.com.blockazo.auth.AuthDtos.*;
import ar.com.blockazo.user.Usuario;
import ar.com.blockazo.user.UsuarioService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UsuarioService service; private final JwtTokens tokens; private final boolean secure;
  public AuthController(UsuarioService service, JwtTokens tokens,@Value("${app.jwt.secure-cookie:true}") boolean secure){this.service=service;this.tokens=tokens;this.secure=secure;}
  @GetMapping("/csrf") public Mensaje csrf(HttpServletRequest request,HttpServletResponse response){
    String value=AuthSecurity.cookie(request,AuthSecurity.CSRF);
    if(value==null){value=AuthSecurity.newCsrfToken();response.addCookie(cookie(AuthSecurity.CSRF,value,false));}
    return new Mensaje(value);
  }
  @PostMapping("/register") public DatosPerfil register(@Valid @RequestBody Registro request,HttpServletResponse response){
    passwordBytes(request.password());Usuario user=service.registrar(request);issue(response,user);return dto(user);
  }
  @PostMapping("/login") public DatosPerfil login(@Valid @RequestBody Login request,HttpServletResponse response){passwordBytes(request.password());Usuario user=service.autenticar(request);issue(response,user);return dto(user);}
  @GetMapping("/me") public DatosPerfil me(@AuthenticationPrincipal Jwt jwt){return dto(service.buscar(UUID.fromString(jwt.getSubject())));}
  @PutMapping("/profile") public DatosPerfil update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Perfil request){
    return dto(service.actualizar(UUID.fromString(jwt.getSubject()),request));
  }
  @PutMapping("/password") public Mensaje password(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CambioClave request,HttpServletResponse response){passwordBytes(request.claveActual());passwordBytes(request.claveNueva());service.cambiarClave(UUID.fromString(jwt.getSubject()),request);clearSession(response);return new Mensaje("La clave se actualizó. Volvé a iniciar sesión.");}
  @PostMapping("/logout") public Mensaje logout(@AuthenticationPrincipal Jwt jwt,HttpServletResponse response){service.cerrarSesiones(UUID.fromString(jwt.getSubject()));clearSession(response);return new Mensaje("Sesión cerrada.");}
  @PostMapping("/google/link") public java.util.Map<String,String> beginGoogleLink(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){request.getSession(true).setAttribute(OidcSuccess.LINK,jwt.getSubject());return java.util.Map.of("url","/oauth2/authorization/google");}
  private DatosPerfil dto(Usuario u){return new DatosPerfil(u.getNombre(),u.getApellido(),u.getDni(),u.getFechaNacimiento(),u.getDireccion(),u.getEmail(),u.getGoogleSubject()!=null);}
  private void issue(HttpServletResponse response,Usuario user){JwtTokens.Issued issued=tokens.issue(user);response.addCookie(cookie(AuthSecurity.ACCESS,issued.token(),true));response.addCookie(cookie(AuthSecurity.CSRF,issued.csrf(),false));}
  private Cookie cookie(String name,String value,boolean httpOnly){Cookie c=new Cookie(name,value);c.setPath("/");c.setHttpOnly(httpOnly);c.setSecure(secure);c.setAttribute("SameSite","Lax");c.setMaxAge(900);return c;}
  private void clear(HttpServletResponse response,String name,boolean httpOnly){Cookie c=cookie(name,"",httpOnly);c.setMaxAge(0);response.addCookie(c);}
  private void clearSession(HttpServletResponse response){clear(response,AuthSecurity.ACCESS,true);clear(response,AuthSecurity.CSRF,false);}
  private void passwordBytes(String password){if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("La clave excede el máximo permitido.");}
}
