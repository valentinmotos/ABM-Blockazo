package ar.com.blockazo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class AuthSecurity {
  static final String ACCESS="BLOCKAZO_ACCESS", CSRF="XSRF-TOKEN";
  private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
  static String newCsrfToken(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}

  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
  @Bean BearerTokenResolver cookieTokenResolver(){
    DefaultBearerTokenResolver header=new DefaultBearerTokenResolver();
    return request->{
      String path=request.getRequestURI();
      if(path.startsWith(request.getContextPath()+"/oauth2/")||path.startsWith(request.getContextPath()+"/login/oauth2/")) return null;
      if(("GET".equals(request.getMethod())&&path.endsWith("/api/auth/csrf"))||("POST".equals(request.getMethod())&&(path.endsWith("/api/auth/login")||path.endsWith("/api/auth/register"))))return null;
      String fromHeader=header.resolve(request), fromCookie=cookie(request,ACCESS);
      if(fromHeader!=null && fromCookie!=null) throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
      return fromHeader!=null?fromHeader:fromCookie;
    };
  }
  @Bean ApiCsrfFilter apiCsrfFilter(JwtDecoder decoder){return new ApiCsrfFilter(decoder);}
  @Bean AuthRateLimitFilter authRateLimitFilter(AuthRateLimiter limiter){return new AuthRateLimitFilter(limiter);}
  @Bean FilterRegistrationBean<ApiCsrfFilter> disableCsrfServletRegistration(ApiCsrfFilter filter){
    FilterRegistrationBean<ApiCsrfFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;
  }
  @Bean FilterRegistrationBean<AuthRateLimitFilter> disableRateLimitServletRegistration(AuthRateLimitFilter filter){
    FilterRegistrationBean<AuthRateLimitFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;
  }
  /* API CSRF and rate limiting run only inside Spring Security's explicitly matched API chain. */
  static final class ApiCsrfFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder; ApiCsrfFilter(JwtDecoder decoder){this.decoder=decoder;}
    @Override protected boolean shouldNotFilter(HttpServletRequest req){return !req.getRequestURI().startsWith(req.getContextPath()+"/api/") || Arrays.asList("GET","HEAD","OPTIONS","TRACE").contains(req.getMethod());}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
      String cookie=cookie(req,CSRF),header=req.getHeader("X-CSRF-TOKEN");
      boolean valid=cookie!=null && header!=null && java.security.MessageDigest.isEqual(cookie.getBytes(java.nio.charset.StandardCharsets.UTF_8),header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String access=cookie(req,ACCESS);
      String path=req.getRequestURI();boolean publicCredentialEndpoint=path.endsWith("/api/auth/login")||path.endsWith("/api/auth/register");
      if(valid && access!=null && !publicCredentialEndpoint) try { Jwt jwt=decoder.decode(access); Object claim=jwt.getClaims().get("csrf"); valid=claim instanceof String && java.security.MessageDigest.isEqual(cookie.getBytes(java.nio.charset.StandardCharsets.UTF_8),((String)claim).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
      catch(Exception ignored){valid=false;}
      if(!valid){res.sendError(403,"Solicitud no válida");return;} chain.doFilter(req,res);
    }
  }

  @Bean @Order(1) SecurityFilterChain appSecurity(HttpSecurity http, BearerTokenResolver resolver, ApiCsrfFilter apiCsrfFilter, AuthRateLimitFilter rateLimitFilter,
      @Value("${app.oauth.google.enabled:false}") boolean google,
      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
      OidcSuccess oidcSuccess) throws Exception {
    http.securityMatcher("/api/**","/oauth2/**","/login/oauth2/**")
      .csrf(csrf->csrf.ignoringRequestMatchers("/api/**"))
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/auth/csrf").permitAll()
          .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
          .requestMatchers("/oauth2/**","/login/oauth2/**").permitAll()
          .anyRequest().authenticated())
      .oauth2ResourceServer(o->o.bearerTokenResolver(resolver).jwt(Customizer.withDefaults()))
      .addFilterBefore(rateLimitFilter,BearerTokenAuthenticationFilter.class)
      .addFilterAfter(apiCsrfFilter,BearerTokenAuthenticationFilter.class)
      .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401,"Autenticación requerida")));
    if(google) http.oauth2Login(o->o.successHandler(oidcSuccess).failureHandler((req,res,ex)->res.sendRedirect(frontendUrl+"/login?oauth_error=1")));
    return http.build();
  }
  @Bean @Order(2) SecurityFilterChain denyOtherRoutes(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a->a.anyRequest().denyAll()).csrf(Customizer.withDefaults()); return http.build();
  }

  @Bean @ConditionalOnProperty(prefix="app.oauth.google",name="enabled",havingValue="true") ClientRegistrationRepository googleRegistration(
      @Value("${GOOGLE_CLIENT_ID}") String clientId,@Value("${GOOGLE_CLIENT_SECRET_B64}") String clientSecretB64){
    String clientSecret=new String(java.util.Base64.getDecoder().decode(clientSecretB64),java.nio.charset.StandardCharsets.UTF_8);
    ClientRegistration registration=CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(clientId).clientSecret(clientSecret)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid","profile","email")
        .clientSettings(org.springframework.security.oauth2.client.registration.ClientRegistration.ClientSettings.builder().requireProofKey(true).build()).build();
    return new InMemoryClientRegistrationRepository(registration);
  }
  static String cookie(HttpServletRequest req,String name){if(req.getCookies()==null)return null;return Arrays.stream(req.getCookies()).filter(c->name.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);}
}
