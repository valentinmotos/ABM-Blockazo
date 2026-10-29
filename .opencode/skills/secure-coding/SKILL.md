---
name: secure-coding
description: Secure-by-default coding for Java / Spring Boot (ABM, MVC, JPA, REST, JWT, Thymeleaf/React). Use when implementing, refactoring, or reviewing code that handles authentication, authorization, user input, database queries, file access, external calls, or LLM/AI features. Applies OWASP Proactive Controls and ASVS 5.0 L1/L2 with concrete unsafe→safe patterns.
---

# Secure Coding (Preventive)

Apply these rules **while writing code**, not after. Every rule maps to a real, common
vulnerability. If you must break one, write the reason inline and flag it as a `P2` in
`.security/findings.json` — never silently.

## The 10 Proactive Controls (OWASP C1–C10)

| # | Control | The one-line rule |
|---|---------|-------------------|
| C1 | Map untrusted data | Know every entry point before you write a handler for it. |
| C2 | Deny by default | Every route/role/method is closed unless explicitly opened. |
| C3 | Validate in a trusted layer | Validate on the server, at the service layer, not only client-side. |
| C4 | Encode by context | HTML, URL, SQL, OS — encode for the specific sink. |
| C5 | Parameterize everything | Queries, OS calls, templates — never concatenate user data. |
| C6 | AuthN + session | Verify identity centrally; new token on login, invalidate on logout. |
| C7 | Access control | Enforce at the service layer, per object, not just per route. |
| C8 | Secrets out of code | Env/key manager only. Never in git, logs, or the client bundle. |
| C9 | Secure logging | Log security events, never passwords/tokens/full PII. |
| C10 | Fail closed | Error paths must not leak stack traces or skip security checks. |

## Java / Spring Boot — the patterns that get you exploited

Read `reference/spring-boot.md` before writing any of: a JPA query, a controller that takes
an `id`, a DTO/entity binding, a JWT handler, a Thymeleaf/React view, or a Security config.
The three that sink most student ABMs:

1. **IDOR** — a `@PathVariable Long id` with no ownership/role check. See `reference/auth-authz.md`.
2. **SQLi** — string-built `nativeQuery`/`JPQL`, or a user-controlled `Sort`/`Pageable`. See `reference/spring-boot.md`.
3. **Mass assignment** — binding a request body straight onto a JPA entity (extra `role=admin` field accepted). Use DTOs + `@Valid`.

## Definition of Done (security gate)

Do not report a task "done" until all that apply are true:

- [ ] Every new/changed endpoint has an explicit auth decision (a `@PreAuthorize`, a filter, or an explicit `permitAll()` with a comment saying why).
- [ ] No user-supplied value is concatenated into a query, command, template, or file path — all parameterized.
- [ ] Every object-accessing handler checks ownership or role in the **service layer**, not only the controller.
- [ ] Client-side validation is mirrored on the server (`@Valid` + Bean Validation on the DTO).
- [ ] No secret, key, or password in source, config committed to git, or client bundle — env var or key manager.
- [ ] Error handling returns a generic message; stack traces never reach the client.
- [ ] New/changed code touched by the security gate has run through `/security-review`.

## LLM / AI (OPTIONAL — only if the project uses an LLM)

If `pom.xml`/`build.gradle` pulls `langchain4j`, `spring-ai`, or an OpenAI/Anthropic SDK,
read `reference/spring-boot.md` → "LLM & Agentic" and `security-review` skill's LLM section.
Treat **all model output as untrusted input** (OWASP LLM10). Otherwise skip this section.

## Anti-patterns to refuse on sight

- `// TODO: security` / `permitAll()` "temporarily" / `@PreAuthorize` commented out.
- `String sql = "...'" + input + "'..."`, `em.createNativeQuery(...)` with concatenation.
- `@ModelAttribute`/`@RequestBody` bound directly to a `@Entity` that has a role/owner field.
- `new ObjectInputStream(...)`, `Runtime.exec(...)` with a joined string, `MessageDigest.getInstance("MD5"/"SHA-1")` for security.
- `jwt.parse(input)` without a pinned algorithm and a fixed `signingKey` from env.
- Catch-all `catch (Exception e) { e.printStackTrace(); return "ok"; }`.
