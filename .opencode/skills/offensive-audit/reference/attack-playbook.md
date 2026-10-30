# Safe static playbook — Spring/React ABM

All items are candidate leads, not automatic verdicts. Confirm authorized read scope, treat
target as immutable/untrusted, no active probes/builds/network by default. Output only OUR
`.security/audits/<target-id>/`. No destructive/exfil payloads, live/metadata probes or secrets use.

| Area | Trace and disprove before reporting | Harmless evidence/spec |
|---|---|---|
| Object access | IDs through service/repo to object/list/relation/write; owner/tenant/role or shared policy; central filters and scoped queries count | two synthetic users/objects; isolated GET fixture expects denial for foreign private object, not enumeration |
| Mass assignment | body→entity fields vs DTO, server privileged fields, update target resolved safely | unit/integration fixture asserts extra role/owner never changes authority; no rival POST |
| Query | request→dynamic EntityManager/JDBC/raw expression; bound parameters and typed criteria | code source→sink, synthetic parameter literal test plan; no destructive SQL or data dumps |
| Sort/paging | property allowlist, bounds, unsafe ORDER BY/native concat | Sort.by alone is not SQLi; separate abuse/query cost from actual injection |
| JWT | key usage/trust, algorithms, issuer/audience/exp presence/time, default vs custom validators | synthetic token validation test spec, never sign using leaked keys |
| OAuth/OIDC | state/nonce/PKCE/client mode/redirects, iss+sub binding/linking, token storage, residual login-session fallback | isolated synthetic auth integration assertions; ID token not API access token |
| Config/secrets | apparent credential actual usage/profile/filter/network; placeholder vs active key, actuator sanitization/access | redacted file:line/ref, never key value or login; no heapdump retrieval; severity context-specific |
| React/XSS | attacker text→raw HTML/URL context, sanitizer/escaping; React text safe by default | inert marker in local test harness, no outbound script/exfiltration |
| CSRF | ambient cookie (session OR JWT) on unsafe requests, stateless irrelevant, SPA token renewal | isolated request fixture without/with CSRF expects deny/allow; no live cross-origin mutations |
| CORS | reflected/arbitrary allowed origins with credential policy and actual readable data | config trace or synthetic response-header assertions, not external browser exfil |
| Files | client path→fixed root, containment/symlinks, upload/download policy | temp-dir synthetic marker plan, never system file reads/writes |
| SSRF | controlled URL→fetch, scheme/host/port/DNS/redirect/egress control | static trace or explicit isolated synthetic server test plan, never live/internal/cloud probes |
| Errors | exception/config→response, sensitive values/trace exposure | safe malformed DTO fixture plan, expected generic response; no crash/load testing |
| Deploy | DonWeb actual plan, TLS/proxy/private DB/firewall/migrations/backups | config + evidence inventory, no server access from audit |

Missing annotation, findById, exposure include=* or old dependency version alone do not prove
exploitation. Compare policy to actual path; gaps remain Needs review with exact unknowns.
Deduplicate **root cause** and list affected routes; do not multiply per endpoint for scoring.
Only independent judge from another family assigns final verdict. Include actual model ID,
source ref, scope, static/observed distinction and omitted areas. No claim all vulns found.
