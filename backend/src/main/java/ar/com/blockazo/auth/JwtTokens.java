package ar.com.blockazo.auth;

import ar.com.blockazo.user.Usuario;
import ar.com.blockazo.user.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Configuration
class JwtConfiguration {
  @Bean SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String secret) {
    byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
  @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key.getEncoded())); }
  @Bean JwtDecoder jwtDecoder(SecretKey key, UsuarioRepository users, @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
    OAuth2TokenValidator<Jwt> required = jwt -> {
      Object version=jwt.getClaims().get("session_version");
      if(jwt.getExpiresAt()==null || !jwt.getAudience().contains(audience) || !(version instanceof Number)) return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
      try {
        long current=users.findTokenVersionById(UUID.fromString(jwt.getSubject())).orElse(-1L);
        return current>=0 && current==((Number)version).longValue()?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
      } catch(RuntimeException ex) { return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token")); }
    };
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, required));
    return decoder;
  }
}

@Component
public class JwtTokens {
  private final JwtEncoder encoder;
  private final String issuer, audience;
  public JwtTokens(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience) {
    this.encoder=encoder;this.issuer=issuer;this.audience=audience;
  }
  public Issued issue(Usuario user) {
    String csrf = AuthSecurity.newCsrfToken();
    Instant now=Instant.now();
    JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(user.getId().toString())
        .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("csrf",csrf).claim("session_version",user.getTokenVersion()).build();
    return new Issued(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),csrf);
  }
  public record Issued(String token,String csrf) {}
}
