package ar.com.blockazo.auth;

import ar.com.blockazo.user.Usuario;
import ar.com.blockazo.user.UsuarioRepository;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OidcSuccess implements AuthenticationSuccessHandler {
  static final String LINK="GOOGLE_LINK_ACCOUNT";
  private final UsuarioRepository users; private final JwtDecoder decoder; private final JwtTokens tokens;
  private final boolean secure; private final String frontendUrl;
  public OidcSuccess(UsuarioRepository users,JwtDecoder decoder,JwtTokens tokens,
      @org.springframework.beans.factory.annotation.Value("${app.jwt.secure-cookie:true}") boolean secure,
      @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") String frontendUrl){this.users=users;this.decoder=decoder;this.tokens=tokens;this.secure=secure;this.frontendUrl=frontendUrl;}
  @Override @Transactional public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws IOException {
    HttpSession session=request.getSession(false); Object intent=session==null?null:session.getAttribute(LINK);
    OidcUser oidc=(OidcUser)((OAuth2AuthenticationToken)authentication).getPrincipal();
    String issuer=oidc.getIdToken().getIssuer().toString(), subject=oidc.getSubject();
    Usuario user;
    if(intent instanceof String accountId){
      try {
        UUID id=UUID.fromString(accountId); String raw=AuthSecurity.cookie(request,AuthSecurity.ACCESS); Jwt caller=raw==null?null:decoder.decode(raw);
        if(caller==null||!accountId.equals(caller.getSubject())) throw new IllegalStateException();
        user=users.findById(id).orElseThrow();
        users.findByGoogleIssuerAndGoogleSubject(issuer,subject).ifPresent(existing->{if(!existing.getId().equals(id))throw new IllegalStateException();});
        user.vincularGoogle(issuer,subject);
      } catch(Exception ex){if(session!=null)session.invalidate();response.sendRedirect(frontendUrl+"/perfil?google_error=1");return;}
      response.sendRedirect(frontendUrl+"/perfil?google=linked");
    } else {
      user=users.findByGoogleIssuerAndGoogleSubject(issuer,subject).orElse(null);
      if(user==null){if(session!=null)session.invalidate();response.sendRedirect(frontendUrl+"/login?google_error=unlinked");return;}
      response.sendRedirect(frontendUrl+"/perfil");
    }
    JwtTokens.Issued issued=tokens.issue(user);
    response.addCookie(cookie(AuthSecurity.ACCESS,issued.token(),true)); response.addCookie(cookie(AuthSecurity.CSRF,issued.csrf(),false));
    if(session!=null)session.invalidate();
  }
  private Cookie cookie(String name,String value,boolean httpOnly){Cookie c=new Cookie(name,value);c.setPath("/");c.setHttpOnly(httpOnly);c.setSecure(secure);c.setAttribute("SameSite","Lax");c.setMaxAge(name.equals(AuthSecurity.ACCESS)?900:900);return c;}
}
