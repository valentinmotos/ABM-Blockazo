# Spring Boot / Java — unsafe → safe patterns

Examples are focused fragments, not a complete app or tested security configuration. Use the
project's versions; React is mandatory for this rubric. Read auth/input/React-OAuth references.

## 1. Queries, sorting and paging

Real dynamic query risk (an annotation argument cannot reference runtime `nombre`):

```java
// UNSAFE: request nombre concatenated into executable SQL
em.createNativeQuery("SELECT * FROM producto WHERE nombre = '" + nombre + "'")
  .getResultList();
// SAFE: bound value
em.createNativeQuery("SELECT * FROM producto WHERE nombre = :nombre")
  .setParameter("nombre", nombre).getResultList();
```

Repository alternative:
```java
@Query("SELECT p FROM Producto p WHERE p.nombre = :nombre")
List<Producto> buscar(@Param("nombre") String nombre);
```
Derived queries/typed Criteria/Specifications are suitable too; inspect any custom raw
expression sink. Constant string concatenation in `@Query` is not runtime user injection.
`Sort.by(userProperty)` is **not inherently SQLi**: Spring Data commonly resolves property
paths/rejects invalid ones. Still allowlist exposed field names/direction, bound page/size
and query cost; inspect `JpaSort.unsafe`, custom ORDER BY concat, native paths separately.
Parameter binding handles values, not identifiers. Never interpolate a client identifier.

## 2. DTOs + validation, not mass assignment

```java
public record CrearDto(@NotBlank @Size(max = 100) String nombre) {}
// In a controller with an explicit protected route policy:
@PostMapping
public ResponseEntity<SalidaDto> crear(@Valid @RequestBody CrearDto dto) {
    return ResponseEntity.status(201).body(service.crear(dto));
}
```
Service assigns owner/role/privileged state from trusted identity and business policy;
request never binds entity. Validate invariants at trusted service entry and DB constraints.
Response DTO avoids secret fields and accidental lazy relationships. Reject/ignore unknown
properties by explicit contract, but prove overposting cannot grant privileges.

## 3. Object access and patterns

Read/update/delete, list/filter/export and related IDs all require **service policy**. Load
object, authorize caller/tenant/role or shared access explicitly, then return/change. UUIDs
do not enforce policy; filters/method security may already protect an endpoint. Comparing
object ID to user ID is not ownership. Never permit a payload to pick another owner.

Template Method final algorithm includes an explicit non-overridable guard before any
business hook/save. Final methods cannot be advised by CGLIB; self-invoked `@Transactional`
or `@PreAuthorize` hooks bypass proxies. Use `TransactionTemplate` with the explicit guard,
or a public non-final transactional facade calling a separate template object, with ownership
inside the transaction and no bypass path. Test overriding a hook cannot skip authorization.
See [Spring proxy semantics](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
Builder validates construction, never grants roles; Strategy only with real alternatives,
not swappable authorization guards. Do not build a generic CRUD framework for the rubric.

## 4. Output and React

`<div>{dto.descripcion}</div>` escapes text. Raw `dangerouslySetInnerHTML` requires a real
rich-content need and maintained allowlist sanitizer; no dependency added by default.
Validate URL protocols and destinations too. Legacy Thymeleaf uses `th:text`, not raw
`th:utext`; those constructs alone are leads, confirm attacker data and context.
No exfiltration payloads needed to reason about XSS. UI hiding controls is not authorization.

## 5. JWT policy, not bogus parser recipes

Prefer Spring Security Resource Server/NimbusJwtDecoder configured for the chosen version.
Configure trusted keys/issuer and allowed algorithms, **explicit audience validator**, required
`exp` presence + expiry, `nbf` validity when present (presence if policy), bounded skew.
Preserve default issuer/time validators when composing custom validators: a custom decoder
does not magically inherit all policy. Do not use equality validators like `requireIssuedAt(now)`
or `requireExpiration(now.plusHours(1))` as expiry checks. Old library usage alone does not prove
acceptance of `alg=none`; inspect actual parser/version/config.
Secret/key env/manager, rotate; severity of an apparent committed key depends on actual use,
access and impact, never automatically P0. Reject untrusted issuer/JWKS URLs and algorithm
confusion. Tokens are not encrypted by default; no sensitive PII in claims. See React/OAuth ref.

## 6. Security configuration (policy before code)

Explicit route/method decisions and terminal denyAll for undeclared routes; public login
initiation/callback/static assets only with documented rationale. Method security must be
enabled if used; central filters/service checks may be valid without `@PreAuthorize`.
Cookie auth (session **or JWT**) needs CSRF on unsafe methods even stateless. Header-only
Bearer API CSRF exception requires proof no ambient credentials/basic/session fallback.
Separate transient OAuth login session from API JWT chain; do not accidentally authenticate
API by residual session. Resource Server defaults to Bearer **header**, not cookie.
CORS exact origins/methods/headers if needed, never reflect arbitrary origins with credentials.
No H2-console public matcher: production console disabled, dev local only with separate
reviewed access policy; DB listener not exposed. Never globally disable frame/CSRF protections
to make a development console work.

## 7. Configuration, secrets, actuator

No credentials in source; `${ENV_NAME}` placeholders are not leaked secrets. `.env` excluded,
example config empty/safe, `VITE_*` public. Restrict actuator at route/profile/network layers;
health without details, no public sensitive management endpoints. Exposure setting alone does
not prove accessible secrets: verify SecurityFilterChain, profile, sanitization and network.
Disable production debug/devtools/H2, error traces; log server-side without secrets/PII.
Dependency age alone is not exploitability: verify advisory, version, config and reachable path.

## 8. Files

Server-generated storage IDs; extension/content allowlist, size limits, quarantine if needed.
Never client filename as path. Resolve under fixed canonical root, check containment and
symlink behavior; run least privilege. Protect download ownership. Content type is untrusted.

## 9. SSRF

Avoid server-fetching arbitrary URLs. If necessary fixed allowlisted scheme/hosts/ports,
resolve/check addresses, enforce egress controls and revalidate redirects/DNS at connection.
No live/cloud metadata probes: static trace or synthetic isolated server only with permission.

## 10. Miscellaneous

No untrusted Java deserialization, SpEL evaluation or dynamic templates. ProcessBuilder list
avoids shell splitting but **argument/option injection remains**: fixed executable, allowlisted
arguments, reject attacker flags, `--` where supported, resource limits; prefer Java APIs.
Use maintained password hashing (bcrypt/Argon2 per version), CSPRNG, authenticated encryption;
no MD5/SHA-1 for security, ECB or reused GCM nonce. Never log tokens/passwords/full PII.

## LLM / agentic — only actual application use

Dependency/package names are leads, confirm calls/tools/data flows before activating hunter.
Risk names: prompt injection, improper output handling, excessive agency, context/secret
exposure, tenant-isolation failure in retrieval. Delimiting data helps but is not a security
boundary; enforce tool permissions/authorization outside model, validate output to sinks,
keep secrets out, require human approval for consequential actions. No unverified OWASP IDs.
