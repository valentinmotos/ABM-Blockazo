---
name: offensive-audit
description: White-box security audit of a CLONED third-party repository (caja blanca). Maps the attack surface, hunts for IDOR, SQLi, XSS, CSRF, JWT flaws, actuator/config leakage, mass assignment and secrets, then crafts reproducible PoCs and professional writeups. Use ONLY on a local clone in an authorized context (e.g. a university competition). Read reference/attack-playbook.md and reference/writeup-template.md.
---

# Offensive Audit (caja blanca)

White-box hunting against a **local clone** of a rival Spring Boot project. Same hunters as
the defensive flow, inverted orientation: instead of fixing, we produce an **evidence-backed
finding** a judge can reproduce.

## Scope & ethics (hard rules, no exceptions)

- **Local clones / competition-provided instances ONLY.** Never probe infrastructure of the
  university, teammates' or competitors' machines, or anything outside the authorized scope.
- **No destructive actions.** Do not `DELETE`, `DROP`, or `TRUNCATE` anything. To prove an
  IDOR, `GET` the other object — do not modify or delete it. Proving access is the point.
- **Never use leaked credentials** to "explore further". Report that they exist; don't log in
  with them.
- **Responsible disclosure** to the competition organizer. Keep evidence with a timestamp.
- Everything runs read-only against the clone unless a PoC is explicitly built into
  `.security/poc/` and confirmed with you before anything is executed.

## Pipeline

```
1. Recon & surface map
2. Secrets in git history (gitleaks)
3. Config & actuator exposure
4. Spring Security config (CSRF/CORS/permitAll)
5. JWT implementation
6. AuthZ / IDOR (the #1 scoring area)
7. Injection (SQLi, sort, SpEL, XSS)
8. Mass assignment / over-posting
9. Front-end (Thymeleaf th:utext / React dangerouslySetInnerHTML)
10. Validate (finder ≠ judge) → craft PoC → writeup
```

Run each hunt with the matching subagent (`hunter-authz`, `hunter-injection`, `hunter-web`,
`hunter-config`, `hunter-llm` if the project uses an LLM). Validate every candidate with
`exploiter` (different model family than the finder). Turn each `Confirmed` finding into a
reproducible PoC with `exploit-craft`, then into a writeup with `reference/writeup-template.md`.

## The steps in detail

**1. Recon & surface map.** Read `pom.xml`/`build.gradle` (Spring Boot? JPA? JWT lib? React or
Thymeleaf?), the `@Controller`/`@RestController` classes, `SecurityConfig`, `application.yml`.
Produce `.security/attack-surface.json`: every route, its auth posture, the entity model, and
the top-5 riskiest areas. This drives the hunters.

**2. Secrets in git history.** Run `.opencode/scripts/scan.sh` (gitleaks). A hardcoded
`jwt.secret`, DB password, or API key in `application.yml` or anywhere in history is an
**immediate P0** — it forges tokens and unlocks everything downstream.

**3. Config & actuator.** Check `management.endpoints.web.exposure.include` (`*`/`env`/`heapdump`
= P0/P1), devtools, h2-console, verbose error pages, `spring.datasource` credentials committed.

**4. Spring Security.** Find `permitAll()` on real endpoints, `.csrf().disable()` with cookie
auth, `allowedOriginPatterns("*")` + credentials. Each is a finding with its own exploit.

**5. JWT.** Algorithm pinning, `exp`/`iss`/`aud` validation, secret source. `alg=none` accepted
or a committed signing key = forged-admin-token P0.

**6. AuthZ / IDOR (score the most here).** For every handler that takes an `id` and calls
`findById`, check for an ownership/role check downstream. Enumerate the missing ones. See
`reference/attack-playbook.md` §IDOR for the exact test and PoC.

**7. Injection.** Native/JPQL concatenation, user-controlled `Sort`/`Pageable`, SpEL, and
Thymeleaf/React output encoding.

**8. Mass assignment.** `@RequestBody`/`@ModelAttribute` bound to a `@Entity` with role/owner/
active fields → privilege escalation via an extra field in the POST body.

**9. Front-end.** `th:utext`, `dangerouslySetInnerHTML`, reflected params in views.

**10. Validate → PoC → writeup.** Each `Confirmed` (≥0.8) finding:
- `exploiter` re-derives it independently (fresh context, different family).
- `exploit-craft` writes a concrete PoC to `.security/poc/<id>.http` (exact request/params).
- `reference/writeup-template.md` produces the report a judge can reproduce.

## Output

- `.security/attack-surface.json` — routes, auth posture, entity model, riskiest areas.
- `.security/poc/<id>.http` — reproducible PoC per confirmed finding.
- `.security/report-rival.md` — writeups, one per finding, using `reference/writeup-template.md`.

This skill is for authorized, educational/competitive use only. See `AGENTS.md` for scope rules.
