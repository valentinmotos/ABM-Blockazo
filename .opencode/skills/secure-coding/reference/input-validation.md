# Trusted validation and contextual output

Sources include HTTP body/path/query/headers/cookies, filenames, persisted user data and
provider/model responses. Trace source → transformation → reachable sink, checking framework
controls. A suspicious API alone is not proof; unknown reachability is Needs review.

## Backend validation

React required/min/max are UX, not security. DTO `@Valid` + Bean Validation on controller;
service validates business invariants and any other entry point. Combine `@NotNull` with
constraints that allow null; numeric bounds/size/precision, request-body limits. Roles/owner/
privileged active set server-side; no direct entity binding. Response DTO only needed fields.
Bound page/size, sort/direction allowlist. Sort property not inherently SQLi; raw identifier
concatenation/unsafe expression sinks need separate trace. Parameterize values in JPA/SQL.

## Context by sink

| Sink | Required control |
|---|---|
| SQL/JPQL | bound parameters, typed queries; identifiers server allowlist |
| HTML | React text nodes; legacy Thymeleaf th:text, no raw HTML without sanitization |
| URL | encode components + validate scheme/destination; encoding is not URL authorization |
| JavaScript | no user interpolation into scripts, use safe serialization and CSP |
| Process | avoid; fixed executable/argument allowlists; ProcessBuilder list still permits option injection |
| File | generated server ID, fixed canonical root, containment/symlink controls and per-object auth |
| Remote fetch | fixed host/scheme/port allowlist, redirects/DNS revalidation, egress control |
| Logs | structured parameters, control-character handling, no tokens/passwords/PII |

Do not apply a universal "sanitize". HTML escaping does not parameterize SQL. `th:attr` and
Thymeleaf inline expressions are not automatically XSS; examine actual context and escaping.
React text escaping does not validate `href` protocols. Raw HTML sanitizer only for justified
rich content, not default dependencies. Java deserialization/untrusted SpEL prohibited.

## Errors

Stable generic problem code/message, safe field errors without rejected sensitive values;
401/403/404/409 policy consistent. Never stack traces, SQL or arbitrary exception.getMessage
to clients. Redacted traces on server with correlation. Test malformed JSON, oversize input,
extra fields, invalid relation IDs, bounded paging, conflict and safe output.
