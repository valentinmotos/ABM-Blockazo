package ar.com.blockazo.auth;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException ex){
    var fields=ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(e->e.getField(),e->"Revisá este campo.",(a,b)->a));return ResponseEntity.badRequest().body(Map.of("error","validation","fields",fields));
  }
  @ExceptionHandler(BadCredentialsException.class) ResponseEntity<?> credentials(){return ResponseEntity.status(401).body(Map.of("error","unauthorized","message","Credenciales inválidas o sesión vencida."));}
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("error","forbidden","message","No tenés permiso para realizar esta acción."));}
  @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("error","invalid_request","message",ex.getMessage()));}
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("error","conflict","message","El correo o DNI ya están registrados."));}
  @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(){return ResponseEntity.status(500).body(Map.of("error","internal_error","message","No pudimos procesar la solicitud."));}
}
