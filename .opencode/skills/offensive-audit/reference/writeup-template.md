# Writeup template (private, evidence-based)

One report per root cause, with affected routes listed. No scoring inflation. Fill actual
facts, use unknown/not run when necessary. Store OUR `.security/audits/<target-id>/report.md`.

```markdown
# <ID> — <title>

- Target: canonical clone identity, source SHA/ref, date, authorized read scope
- Severity: P0/P1/P2/P3 + impact/prerequisites rationale (not pattern-based)
- Verdict: Confirmed / Needs review / Rejected; confidence 0–1
- Lifecycle: open / fixed / verified / rejected (rival audit does not remediate)
- Location/entry: exact file:line, method/path, caller capability
- Found by / independently validated by: actual role, model ID and different families
- CWE/OWASP: only mappings supported by chosen verified edition, otherwise omit

## Policy and description
Expected owner/role/tenant/public policy, observed control and mismatch.

## Source → sink / exposure
Every hop and existing guard; for secret leak, actual use + who can read it, never value.
All affected routes sharing this root cause, not duplicate findings for each route.

## Evidence
Static derivation vs observed execution explicitly separated, redacted/minimal excerpts.
Unknown prerequisites and coverage limits. No sensitive code sent to cloud without permission.

## Inert reproduction specification
OUR .security/audits/<target-id>/poc/<id>.http, synthetic fixture preconditions, isolated base
placeholder and expected denial/allow. No claims it was run unless authorized and observed.
Write issues use test assertions, not rival mutations. No leaked credential use/exfil/destruction.

## Impact and recommendation
Trust boundary crossed, realistic blast radius and concrete service/filter/validation fix.

## Regression / limits
Proposed test path and assertions; actual command/ref/exit/output only if run. No runner =
not executed. Fixed != verified without observed fail-before/pass-after. Areas not checked.
```

Confirmed ≥0.8 actionable, Needs review retained separately, potentially grave uncertainty
escalated rather than dropped. Configuration alone not proof of public exposure. Independent
review unavailable means BLOCKED, not pass. Private responsible disclosure to organizer.
