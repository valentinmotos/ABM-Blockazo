# Authentication, session & authorization (Spring Security)

The single largest source of points in a student ABM competition is **broken access control**
(IDOR + missing `@PreAuthorize` + mass assignment). This reference makes those findings
systematic — both to avoid them (defense) and to spot them (offense).

---

## The authorization model you must be able to state

Before writing (or reviewing) any protected handler, answer:

1. **Who** is calling? (anonymous / authenticated user / role X)
2. **What object** is being touched, identified how? (path `id`, query param, JWT claim)
3. **May this caller touch THIS object?** (ownership / role / tenant)

A route being `authenticated()` answers only question 1. **IDOR is a failure of question 3** —
the user is logged in, so `authenticated()` passes, but they reach someone else's object.

---

## Spring Security setup checklist

| Check | ❌ Vulnerable | ✅ Secure |
|-------|--------------|-----------|
| Default route policy | `anyRequest().permitAll()` | `anyRequest().authenticated()` (deny by default) |
| Method security | not enabled | `@EnableMethodSecurity` on a `@Configuration` |
| Role checks | only in UI, or nowhere | `@PreAuthorize("hasRole('ADMIN')")` on admin handlers |
| Ownership | controller trusts `id` | service layer compares object owner to caller |
| Passwords | `{noop}` or raw | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt) |
| Session | fixed/predictable, not invalidated | regenerate on login, invalidate on logout, `HttpOnly`+`Secure` cookies |
| CSRF | `.csrf().disable()` globally | keep CSRF for cookie auth; disable only for token APIs |
| CORS | `allowedOriginPatterns("*")` + `allowCredentials(true)` | pin to your front-end origin |
| Error responses | stack traces returned | generic message + log server-side |

---

## Enable method security (do this once)

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig { ... }
```

Then annotate handlers. Method security is enforced in the **service layer**, so it can't be
bypassed by calling the service directly from another controller or a scheduled job.

---

## IDOR: the three shapes you must recognize

### A) Read IDOR — `GET /api/facturas/{id}`
```java
return repo.findById(id).orElseThrow();     // ❌ no ownership check
```

### B) Write IDOR — `PUT /api/facturas/{id}` or `POST` with an `id` in the body
```java
repo.save(facturaFromRequestBody);          // ❌ the body picks the target row
```
An attacker edits another user's record by changing the `id`. Always re-resolve the target
server-side from the path id (or from the JWT), **never** from a client-supplied field.

### C) Enumerated / predictable IDs
Sequential `Long` ids are trivially enumerable. That's not a bug by itself, but combined with a
missing ownership check it's full data exposure. (UUIDs reduce enumeration, but are NOT a
substitute for an ownership check — they just raise the bar.)

### The fix pattern (service layer, not controller)

```java
@Transactional(readOnly = true)
public Factura obtener(Long id) {
    Long current = auth.getUserId();                       // caller identity from SecurityContext
    Factura f = repo.findById(id)
        .orElseThrow(() -> new NotFoundException("factura"));
    if (!f.getClienteId().equals(current) && !auth.hasRole("ADMIN")) {
        throw new AccessDeniedException("no autorizado"); // or 404 to hide existence
    }
    return f;
}
```

Decide your policy for "exists but not yours": **404** hides existence (safer), **403** is
explicit. Be consistent. 404 is the safer default when objects are sensitive.

---

## Mass assignment via role/owner on create & register

A registration or create endpoint that binds to the `@Entity` accepts extra fields. Force the
role/owner/active fields server-side:

```java
public Usuario registrar(UsuarioCrearDto dto) {
    Usuario u = new Usuario();
    u.setNombre(dto.nombre());
    u.setEmail(dto.email());
    u.setPassword(encoder.encode(dto.password()));
    u.setRol(Rol.USER);          // ← server-chosen, never from the DTO
    u.setActivo(true);           // ← server-chosen
    return repo.save(u);
}
```

The DTO must simply not have a `rol` field. If a field shouldn't be client-settable, it must
not exist on the DTO.

---

## Session & token handling

- **New session/token on login and on privilege change.** Fixation: don't reuse the pre-login session id.
- **Invalidate on logout**, and invalidate server-side (remove the session / blacklist the token), not just clear the cookie client-side.
- Cookies: `HttpOnly` (no JS access → no theft via XSS), `Secure` (HTTPS only), `SameSite=Lax|Strict`.
- **JWT:** see `spring-boot.md` §5. Pin the algorithm, check `exp`/`iss`/`aud`, secret from env.
- Never store the password or token in logs or in `localStorage` if a cookie is viable
  (`localStorage` is readable by any XSS on the page).

---

## Password storage

```java
@Bean
public PasswordEncoder encoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt by default
}
```

Never `{noop}`, never MD5/SHA-1, never a hardcoded password ("admin/admin" default accounts are
an ASVS L1 fail). Block the top common passwords and enforce a minimum length (8+, prefer 12+).

---

## How to audit auth quickly (defense checklist / offense checklist)

Grep these and confirm each:

```
rg -n "permitAll|anonymous\(\)|csrf\(\)\.disable|allowedOrigin" 
rg -n "@PreAuthorize|@Secured|@RolesAllowed"
rg -n "@PathVariable.*Long id|@RequestParam.*id|@PathVariable.*UUID" 
rg -n "@ModelAttribute|@RequestBody.*Entity"    # entity binding = mass-assignment smell
rg -n "findById\(|getReferenceById\("           # each needs an ownership check downstream
```

For every handler that takes an `id` and calls `findById`, there must be an ownership/role
check on the returned object. No check → IDOR. That is the finding you are hunting for and
the bug you are defending against.
