# React + OAuth/OIDC + JWT + DonWeb

Implementation checklist, not tested copy/paste API code. See `docs/COMPETENCIA.md` for full
rubric/stages. Confirm framework/provider versions and domain before implementation.

## Recommended same-origin BFF hybrid

React TS Vite → TLS reverse proxy → Spring MVC BFF/REST; DB/backend private. JWT is format,
OAuth delegates authorization, OIDC supplies identity. Requirements ask JWT **and** OAuth.

- Spring `oauth2Login` OIDC Authorization Code; PKCE as supported/configured for provider and
  client type, state and nonce validated, exact redirect URI allowlist. Confidential client
  secret backend only. Never assume PKCE automatically applied in all versions/client modes.
- OAuth uses a **transient authenticated login session**, rotated on authentication; callback
  normally `/login/oauth2/code/{registrationId}`. Provider tokens backend only. Bind verified
  `iss`+`sub`; no account linking/admin assignment from unverified email. Linking requires
  authenticated/reauthed local account and consent.
- Issue short-lived own API-audience JWT in HttpOnly Secure SameSite cookie, no JWT in URL,
  localStorage or bundle. Redirect local fixed path without token. ID token is not API access.
- Explicit API SecurityFilterChain stateless: **no OAuth-session SecurityContext fallback**;
  matchers/order and context repository policy must be tested. Close login session after flow;
  if provider tokens retained, separate backend store, not session that will be invalidated.
- Resource Server defaults to Authorization Bearer **header**, not cookies. Cookie design
  needs custom `BearerTokenResolver`, duplicate/header-cookie ambiguity policy and tests.
- Cookie credentials require CSRF header/token on unsafe requests, logout/refresh too, even
  stateless. JWT cookie stays HttpOnly; CSRF mechanism is separate and may expose its token
  to JS. Version-specific SPA handler for deferred token and BREACH, renew after login/logout.
  SameSite/CORS are not substitutes. No mutating GET.
- Trusted keys, pinned allowed algorithms, issuer, explicit API audience, exp presence/expiry,
  nbf validity (required if policy), bounded skew. Preserve default validators with custom
  NimbusJwtDecoder; default time validation may not require exp existence. No equality test
  against now as expiry validation. Reject issuer/JWKS from attacker token.
- Key rotation with controlled kid, revoke session/refresh on logout, clear cookies correctly;
  access JWT copied elsewhere remains valid until exp unless jti/session-version revocation.
  Document window or implement immediate revocation. Refresh only if needed, server-side
  rotation/reuse detection. No provider tokens/claims/secrets in client logs.

Alternative public SPA Code+PKCE: access token in memory and Bearer header, no client secret,
only if provider issues JWT for **our resource audience** and appropriate scopes. Not an ID
token or an access token for provider API. Define logout/refresh/XSS and exact CORS if needed.
Choose one architecture and document tradeoffs, not an accidental mixture.

## React and validation

DTO contract, abort/stale response handling, bounded pagination, safely rendered errors.
Text nodes, validated URL schemes, no raw HTML by default. Client validation mirrors backend
`@Valid`/business checks, never substitutes them. UI permissions are only UX. `VITE_*` is
public build data, not a secret store. No dependency installation or imagery generation by default.

## Template Method / Spring proxy traps

Final algorithm contains explicit policy guard before business hooks. CGLIB cannot advise
final methods; self-invocation bypasses `@Transactional`/`@PreAuthorize`. Use TransactionTemplate
or separate non-final proxied service facade, ownership inside transaction and no bypass path.
Test an overridden hook cannot skip authorization or save after denial.

## Conditional DonWeb checklist

Confirm actual cloud/VPS with root/JVM/Docker; shared hosting is not assumed compatible.
TLS same-origin proxy serves React, forwards `/api`, `/oauth2`, `/login/oauth2` (no SPA fallback
for these); trust forwarded headers only from proxy; exact external callbacks. Backend/DB
private, firewall IPv4/IPv6, restricted key-only SSH, non-root containers/processes, DB runtime
least privilege, migration user separate. Migrations + ddl-auto=validate, not update. Secrets
env/manager and private cert permissions, no secrets in source/image/Vite. Encrypted backups,
retention and tested isolated restore; versioned release/rollback, health without details,
restricted actuator, no public H2/devtools/trace output. No files/deploy created by this checklist.

## Required evidence (only claim execution if observed)

Spring integration: valid/invalid JWT signature/alg/issuer/aud/exp absent or expired/nbf future;
owner/foreign/shared/tenant/admin reads+writes+lists, DTO/overposting/paging/conflicts, CSRF
missing/wrong/renewed, OAuth state/nonce/PKCE/account linking, residual session without JWT
rejected, logout/refresh/rotation policy. React Vitest/Testing Library, Playwright if available,
keyboard/390/768/1440/reduced-motion; absent runner/browser = not run, not verified.

## Official sources (consulted 2026-10-30; verify selected version)

- https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- https://donweb.com/es-int/cloud-docker-hosting
- https://soporte.donweb.com/es/articles/16148070-cloud-iaas-instalar-docker-en-el-cloud-server

DonWeb Click&Go recreates server and **wipes existing data/config**: backup + restore proof +
explicit consent required before any future action. These pages do not establish your plan.
