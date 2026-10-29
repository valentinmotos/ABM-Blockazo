# Input validation & output encoding (by context)

Validate at a trusted layer, encode for the specific sink. The core idea: **never let a value
that a user or an external system controls reach a "dangerous sink" without validation or
contextual encoding.**

---

## Sources (untrusted) vs Sinks (dangerous)

**Sources** — anything an attacker can influence:
HTTP params (query, path, form, JSON body), headers, cookies, uploaded files, path segments,
database values that were originally user-supplied, third-party API responses, queue messages,
LLM/model output, filenames.

**Sinks** — where a value becomes a vulnerability:

| Sink | Vulnerability if unvalidated |
|------|------------------------------|
| SQL / JPQL / `nativeQuery` | SQL injection |
| Shell / `Runtime.exec` / `ProcessBuilder` | command injection |
| HTML output (`th:utext`, `dangerouslySetInnerHTML`) | XSS |
| File path / `new File(...)` | path traversal |
| `URL(userInput).openStream()` | SSRF |
| `ObjectInputStream` | deserialization RCE |
| `SpelExpressionParser().parseExpression` | SpEL injection |
| LDAP / XML / template engine | injection / XXE |
| Log message (`log.info("..."+input)`) | log injection / CRLF |

A "finding" is a **path from a source to a sink with no validation/encoding in between.** If
you cannot name both ends and the path between them, it is not a confirmed vulnerability.

---

## Validate on the server (client-side is advisory)

React `<input required>`, JS form checks, and Thymeleaf `required` are UX only — an attacker
bypasses them with `curl`. Mirror every client rule on the server with Bean Validation:

```java
public record ProductoDto(
    @NotBlank @Size(max = 100) String nombre,
    @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal precio,
    @Pattern(regexp = "[A-Z]{2}") String pais
) {}

@PostMapping("/productos")
public String crear(@Valid @ModelAttribute ProductoDto dto, BindingResult br) { ... }
```

`@Valid` triggers the checks; `BindingResult` collects errors. Prefer a **positive allowlist**
("only these values") over a denylist ("block these bad strings") — denylists always miss a case.

---

## Encode for the specific sink (contextual output encoding)

One input, many sinks — each needs the right encoding:

| Sink | Encode with |
|------|-------------|
| HTML body | HTML-entity encode (`<` → `&lt;`); in Thymeleaf use `th:text`, in React use text nodes |
| HTML attribute | quote + entity encode |
| URL query param | `URLEncoder.encode(x, UTF_8)` |
| SQL | **parameterize** (there is no safe "SQL escape" — use params) |
| JS context | JSON encode, never string-interpolate into `<script>` |
| OS command | **do not build commands from input**; if unavoidable, use a list-based `ProcessBuilder` and validate each arg |

The dangerous anti-pattern is one global "sanitize" function applied everywhere. Encoding is
context-specific: HTML-encoding does nothing to stop SQLi, and parameterizing does nothing to
stop XSS.

---

## Thymeleaf & React specifics

- Thymeleaf `th:text` escapes by default → safe. `th:utext` does NOT → XSS unless sanitized.
- React text children escape by default → safe. `dangerouslySetInnerHTML` does NOT.
- `|${value}|` inline and `th:attr` need care when the value becomes markup.

See `spring-boot.md` §4 for concrete before/after.

---

## Parameterize, never concatenate (SQL, OS, templates)

```java
// ❌ SQL injection
em.createNativeQuery("SELECT * FROM u WHERE email='" + email + "'");

// ✅ parameterized
em.createNativeQuery("SELECT * FROM u WHERE email = :email")
  .setParameter("email", email);
```

There is no "escaping" your way out of SQL — only parameters.

---

## Allowlist file names, URLs, enums, sort fields

Anything that names a resource, a column, a host, or a mode must be allowlisted, not trusted:

- **Filenames:** generate server-side (`UUID`), never use the client name as a path.
- **URLs (SSRF):** allowlist scheme + host; block private/link-local ranges (`169.254.169.254`, `10.*`, `192.168.*`, `localhost`).
- **Enums / roles / sort fields:** validate against the allowed set (see `Sort` in `spring-boot.md` §1).

---

## The review sweep (defense and offense use the same list)

For each handler, confirm no unvalidated path source→sink:

```
rg -n "getParameter|@RequestParam|@PathVariable|@RequestBody|@ModelAttribute|getHeader|getCookies|MultipartFile" 
rg -n "createNativeQuery|createStoredProcedureQuery|executeQuery|\+ *(nombre|email|input|q|query)" 
rg -n "Runtime\.getRuntime|ProcessBuilder|ObjectInputStream|SpelExpressionParser|openStream|new File\(" 
rg -n "th:utext|dangerouslySetInnerHTML|innerHTML|print\(" 
```

If a source reaches one of those sinks with no validation/encoding/parameterization in between,
raise it with the file:line, the input-to-sink path, and the fix.
