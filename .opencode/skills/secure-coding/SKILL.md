---
name: secure-coding
description: Secure-by-default Java/Spring MVC/JPA/REST and React, JWT/OAuth/OIDC and DonWeb deployment. Apply during implementation and review; explicit auth, DTO validation, service policy, contextual encoding and evidence-based checks.
---

# Secure coding (preventive)

Read existing code and `reference/spring-boot.md`, `auth-authz.md`, `input-validation.md`;
React/auth/deployment also `reference/react-oauth-deploy.md`. Rubric plan: `docs/COMPETENCIA.md`.

## Local security principles (not official OWASP C1–C10 numbering)

- Map entry points, identity, data classification and policy first.
- Deny undeclared routes; explicit decision for each route, public rationale included.
- DTO + `@Valid`, business invariants in services, DB constraints; no request binding to entities.
- Parameterized queries, allowlisted sort/direction and bounded paging. `Sort.by(input)` alone
  is not SQLi. No untrusted concatenation into commands/templates/paths.
- Object policy in service: owner/role/tenant, or explicitly justified shared/public access;
  cover relations, lists and writes. Absence of `@PreAuthorize` is not proof of missing control.
- React text nodes and contextual encoding; no raw HTML unless requirement + reviewed sanitizer.
- Secrets env/manager, not config/bundle/logs; generic external errors, redacted server traces.
- Verified JWT signature/alg/issuer/audience/time claims and presence policy; OAuth/OIDC flow
  correlation/account binding, CSRF for ambient cookie credentials even if stateless.
- Template Method must not allow hooks/proxy quirks to bypass ownership/transactions.

## Definition of done (all applicable)

Explicit justified auth; no untrusted concatenation; service policy; trusted validation;
no secrets; no leaked traces; tests actually executed with permission; independent-family
review of full diff. No open P0/P1 or unresolved potentially severe hypotheses. Missing app
or runner is not applicable/not run, never a fabricated pass. This is guidance, not a guarantee.
If a rule cannot be followed, reason inline + register at least P2 in `.security/findings.json`
(higher severity if warranted), without silently accepting it. Orchestrator persists read-only
agent results. Follow AGENTS for verdict/status, bounded retries and blocked review handling.

LLM section only when actual application usage is found (dependency is a lead, not proof).
Treat model output as untrusted; risk names only unless a specific OWASP edition is verified.
