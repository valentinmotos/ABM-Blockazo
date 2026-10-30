package ar.com.blockazo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {
  private final AuthRateLimiter limiter;
  public AuthRateLimitFilter(AuthRateLimiter limiter){this.limiter=limiter;}
  @Override protected boolean shouldNotFilter(HttpServletRequest request){
    return !"POST".equals(request.getMethod())||!("/api/auth/login".equals(request.getServletPath())||"/api/auth/register".equals(request.getServletPath()));
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    boolean login=request.getServletPath().endsWith("/login");
    int limit=login?10:5; Duration window=login?Duration.ofMinutes(1):Duration.ofHours(1);
    if(!limiter.allow(login?"login":"register",request.getRemoteAddr(),limit,window)){
      response.setStatus(429);response.setHeader("Retry-After",Long.toString(window.toSeconds()));response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Demasiados intentos. Esperá un momento y volvé a intentar.\"}");return;
    }
    chain.doFilter(request,response);
  }
}
