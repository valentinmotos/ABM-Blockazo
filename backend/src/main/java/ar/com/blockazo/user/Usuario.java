package ar.com.blockazo.user;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
    @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"),
    @UniqueConstraint(name = "uk_usuario_dni", columnNames = "dni"),
    @UniqueConstraint(name = "uk_usuario_google", columnNames = {"google_issuer", "google_subject"})
})
public class Usuario {
  @Id @GeneratedValue private UUID id;
  @Column(nullable = false, length = 80) private String nombre;
  @Column(nullable = false, length = 80) private String apellido;
  @Column(nullable = false, length = 12) private String dni;
  @Column(name = "fecha_nacimiento", nullable = false) private LocalDate fechaNacimiento;
  @Column(nullable = false, length = 180) private String direccion;
  @Column(nullable = false, length = 254) private String email;
  @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
  @Column(name = "google_issuer", length = 255) private String googleIssuer;
  @Column(name = "google_subject", length = 255) private String googleSubject;
  @Column(name = "token_version", nullable = false) private long tokenVersion;

  protected Usuario() {}
  public Usuario(String nombre, String apellido, String dni, LocalDate fechaNacimiento, String direccion, String email, String passwordHash) {
    this.nombre=nombre; this.apellido=apellido; this.dni=dni; this.fechaNacimiento=fechaNacimiento;
    this.direccion=direccion; this.email=email; this.passwordHash=passwordHash;
  }
  public UUID getId(){return id;} public String getNombre(){return nombre;} public String getApellido(){return apellido;}
  public String getDni(){return dni;} public LocalDate getFechaNacimiento(){return fechaNacimiento;}
  public String getDireccion(){return direccion;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;}
  public String getGoogleIssuer(){return googleIssuer;} public String getGoogleSubject(){return googleSubject;}
  public long getTokenVersion(){return tokenVersion;}
  public void actualizar(String nombre, String apellido, String dni, LocalDate fecha, String direccion, String email) {
    this.nombre=nombre; this.apellido=apellido; this.dni=dni; this.fechaNacimiento=fecha; this.direccion=direccion; this.email=email;
  }
  public void cambiarPassword(String hash){this.passwordHash=hash;}
  public void revocarSesiones(){this.tokenVersion++;}
  public void vincularGoogle(String issuer, String subject){this.googleIssuer=issuer;this.googleSubject=subject;}
}
