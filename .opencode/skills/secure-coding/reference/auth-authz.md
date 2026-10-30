# Identity, session and authorization

Before implementation: who calls (verified identity), which object/action/tenant, what policy
allows this caller? Classify data and document roles. Authenticated alone is not private-object
authorization. Public/shared catalogues may allow all roles **by policy**; do not infer IDOR
from `findById` or absent `@PreAuthorize` alone. Inspect filters, proxies, scoped repository
queries and service guards. Object ID is not owner ID; UUID is not authorization.

## Route and service decisions

Explicit matchers/method auth, documented public exceptions, terminal denyAll for undeclared
routes. Enable method security if used. Service loads object and enforces owner/tenant/role or
explicit shared policy **before return/write/hook**, including relation IDs and list/export
queries. DTO request excludes owner/role/active; response excludes credentials. Adopt consistent
403 vs 404 policy to avoid sensitive existence leaks. Admin bypass only if business policy allows.

Method annotations work via Spring proxies, not every call: self-invocation bypasses advice,
final CGLIB methods cannot be advised. Do not trust a final Template Method annotation as the
guard; explicit policy in algorithm plus programmatic tx or proxied non-final facade. Test
denied callers cannot cause hooks/save and that every exposed service entry enforces policy.

## OAuth/OIDC and JWT

Read `react-oauth-deploy.md`. OAuth delegation ≠ OIDC identity ≠ JWT token format. Bind identity
by validated issuer+subject, not unverified email; account linking needs reauthentication and
consent, no profile-driven admin role. Code flow correlation state/nonce, PKCE as supported and
configured; redirect allowlist. Provider tokens backend only in BFF design.
Validate JWT signature, allowed algorithm, issuer, audience and required exp/time policy;
never use ID token to authenticate our API. Resource Server default is header Bearer, not cookie.

## Sessions, cookies and CSRF

Rotate session ID on login/privilege change, expire/invalidate server-side on logout. If REST
JWT hybrid chosen, login session is transient, API JWT chain cannot fall back to OAuth session.
Cookies HttpOnly/Secure/appropriate SameSite; XSS can still act as user even with HttpOnly.
Cookie JWT **also** needs CSRF for unsafe requests, including logout/refresh, despite stateless.
Only truly header-only credentials with no ambient fallback may justify disabling API CSRF.
SPA token materialization/BREACH handler and CSRF renewal after auth need version-specific tests.
JWT logout must define revocation or residual short-token lifetime; clearing cookie alone
does not invalidate a copied token. Refresh rotation/reuse detection if refresh needed.
No tokens in localStorage/redirect URL/logs, no secrets in React bundle.

## Passwords (if local login required)

Use maintained PasswordEncoder (bcrypt/Argon2 configured per version), rate limits, common
password checks and suitable length policy. Never plaintext/{noop}/MD5/SHA-1 or default
admin account. OIDC does not require inventing local password auth if rubric does not ask it.

## Evidence

Tests must exercise actual chain and service: anonymous, owner, foreign owner, admin,
shared-policy access, tenant mismatch, related IDs, list filtering, write denial, missing/invalid
JWT and CSRF, OAuth state/nonce/account binding, logout and residual session rejection.
Central enforcement counts; annotation absence is a lead, not proof. Severity follows real
impact/reachability, not a scoring shortcut.
