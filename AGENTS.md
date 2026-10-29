# ABM-Blockazo — Security Constitution

This project is built to a security standard and audited by a multi-family agent pipeline.
This file is the operating procedure every agent (and subagent) follows. It is enforced by the
skills, agents, and commands in `.opencode/`.

## 0. What this project is

A Java / Spring Boot ABM (alta/baja/modificación) with MVC, JPA, REST, JWT, and a Thymeleaf or
React front. It is built defensively and audited offensively (a university security competition).

## 1. The security workflow (runs automatically)

For ANY code change, follow this loop — the commands encode it; you don't need to be told:

```
/implement <feature>     → implementer (OpenAI) writes → reviewer (Google) audits → gate blocks on P0/P1
/security-review         → review the current diff before commit/PR
/audit                   → full defensive audit before a release
/hunt-loop <n>           → iterative bug hunting with fresh perspective per pass
/fix-loop <id>           → closed-loop remediation (fix → verify → re-hunt → regression test)
/verify-fix <id>         → confirm a fix is real and locked in with a test
/audit-rival <path>      → white-box audit of a cloned rival repo (authorized/competition only)
```

The rule: **nothing is "done" with an open P0/P1.** Criticals are fixed before a task is
reported complete.

## 2. Multi-model anti-bias (why the roles are on different families)

Implementation and review are separated across **model families** so a blind spot in one is
caught by another. Finder and judge are also separated.

| Role | Agent | Model | Family |
|------|-------|-------|--------|
| Implement code | `implementer` | `openai/gpt-6.1-sol#high` | OpenAI |
| Review a diff / arbitrate | `reviewer` | `google/gemini-4-argon#high` | Google |
| Threat model (recon) | `threat-modeler` | `opencode/deepseek-v4.1-flash-free#high` | DeepSeek |
| Hunt IDOR / authz | `hunter-authz` | `openai/gpt-6.1-sol#high` | OpenAI |
| Hunt injection | `hunter-injection` | `opencode/deepseek-v4.1-flash-free#high` | DeepSeek |
| Hunt web (XSS/CSRF/CORS/SSRF) | `hunter-web` | `openai/gpt-6.1-sol#high` | OpenAI |
| Hunt config / secrets | `hunter-config` | `opencode/deepseek-v4.1-flash-free#high` | DeepSeek |
| Hunt LLM (optional) | `hunter-llm` | `openai/gpt-6.1-sol#high` | OpenAI |
| Validate a finding (judge) | `exploiter` | `google/gemini-4-argon#high` | Google |
| Craft a PoC | `exploit-craft` | `opencode/deepseek-v4.1-flash-free#high` | DeepSeek |
| Write regression tests | `security-tester` | `openai/gpt-6.1-sol#high` | OpenAI |

Anti-bias rules (do not skip):
1. **Implement ≠ review.** The implementer (OpenAI) is never shown as the reviewer of its own
   diff. The reviewer (Google) gets a **fresh context** with only the diff and entry points.
2. **Finder ≠ judge.** The `exploiter` (Google) re-derives a hunter's finding on a fresh context
   with **only the `file:line` and entry point** — never the hunter's analysis.
3. **Confidence gate.** Only `Confirmed` with confidence ≥ 0.8 is actionable. Everything else is
   recorded but clearly separated as `Needs review`/`Rejected`.
4. **Disagreement escalates.** If a P0/P1 verdict is contested, a third family checks before it
   is closed.

## 3. Security contract (every agent follows)

Load the `secure-coding` skill and follow it. Non-negotiables:

- **Deny by default.** Every endpoint has an explicit, justified auth decision.
- **Parameterize everything.** No string concatenation into queries/commands/templates. Allowlist
  any user-controlled `Sort`/`Pageable`/enum/host/filename.
- **DTOs + `@Valid`.** Never bind a request body to a JPA `@Entity`. Roles/owner/active are set
  server-side only.
- **Ownership checks in the service layer.** Every object accessed by `id` is verified to belong
  to the caller (or an ADMIN). No IDOR.
- **Encode output by context.** Thymeleaf `th:text`, React text nodes (not `th:utext` /
  `dangerouslySetInnerHTML`) unless sanitized.
- **Secrets from env.** Nothing sensitive in source, committed config, or the client bundle.
- **Fail closed.** Generic error to client; stack traces logged server-side only.

## 4. Findings register — `.security/findings.json`

Every actionable finding is logged here. Schema:

```json
{
  "updated": "2026-10-28T00:00:00Z",
  "findings": [
    {
      "id": "SEC-001",
      "title": "IDOR: GET /api/facturas/{id} sin ownership check",
      "severity": "P1",
      "confidence": 0.95,
      "status": "open",
      "cwe": "CWE-639",
      "owasp": "A01:2021",
      "location": "src/.../FacturaController.java:24",
      "entry": "GET /api/facturas/{id}",
      "path": "{id} → facturaRepo.findById(id) → sin check de ownership",
      "impact": "usuario autenticado enumera facturas ajenas",
      "fix": "ownership check en FacturaService (secure-coding/reference/auth-authz.md §IDOR)",
      "found_by": "hunter-authz",
      "validated_by": "exploiter",
      "poc": ".security/poc/SEC-001.http",
      "test": "src/test/java/.../FacturaIdorSecurityTest.java"
    }
  ]
}
```

Status flow: `open` → `fixed` → `verified`. Close a finding only via `/verify-fix` (fix + a
regression test that fails on the old code and passes on the new). Never close without a test
guarding it.

## 5. Scope & ethics (offensive audits of rivals)

`/audit-rival` is for an **authorized university competition** against **local clones** or
competition-provided instances only.

- Target scope: the local clone / authorized instance. **Never** probe university infrastructure,
  teammates' or competitors' machines, or anything outside the authorized scope.
- **No destructive actions.** Never `DELETE`/`DROP`/`TRUNCATE` or alter another user's data —
  prove access with a read, don't change anything.
- **Never use leaked credentials** to log in; report that they exist.
- **Responsible disclosure** to the competition organizer. Keep timestamped evidence.
- PoCs live in `.security/poc/` and are never executed against a live/production target without
  explicit confirmation.

## 6. Deterministic scanners (the ground truth layer)

LLM hunters are one signal; scanners are the repeatable one. Run before committing:

```bash
.opencode/scripts/scan.sh .                  # gitleaks + semgrep + spotbugs + dependency-check
.opencode/scripts/summarize-sarif.py .security/*.sarif   # rank the SARIF results
```

Feed scanner hits into the hunters via `/audit` — the hunters confirm reachability (a scanner
hit is a candidate, not a verdict). A missing tool skips its phase; install to enable.

## 7. Git hygiene

- Never commit secrets (gitleaks runs in the gate). Use `application-example.yml` in git, real
  values via env.
- Keep `.security/` and `.security/poc/` out of any public distribution of the repo (they
  document our own/rivals' vulnerabilities).
- Commit per coherent phase so the security work is reviewable.
