package ar.com.blockazo.auth;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class AuthDtos {
  private AuthDtos() {}
  public record Registro(@NotBlank @Size(max=80) String nombre, @NotBlank @Size(max=80) String apellido,
      @NotBlank @Pattern(regexp="[0-9. -]{7,12}") String dni, @NotNull @Past LocalDate fechaNacimiento,
      @NotBlank @Size(max=180) String direccion, @NotBlank @Email @Size(max=254) String email,
      @NotBlank @Size(min=12,max=72) String password) {}
  public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=128) String password) {}
  public record Perfil(@NotBlank @Size(max=80) String nombre, @NotBlank @Size(max=80) String apellido,
      @NotBlank @Pattern(regexp="[0-9. -]{7,12}") String dni, @NotNull @Past LocalDate fechaNacimiento,
      @NotBlank @Size(max=180) String direccion, @NotBlank @Email @Size(max=254) String email) {}
  public record CambioClave(@NotBlank String claveActual, @NotBlank @Size(min=12,max=72) String claveNueva) {}
  public record DatosPerfil(String nombre, String apellido, String dni, LocalDate fechaNacimiento, String direccion, String email, boolean googleVinculado) {}
  public record Mensaje(String mensaje) {}
}
