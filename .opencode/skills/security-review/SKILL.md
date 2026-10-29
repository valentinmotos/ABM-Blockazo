---
name: security-review
description: Structured security review of the current changes or the whole repo. Maps entry points, sweeps for candidate issues, triages them with a false-positive filter, and validates each finding independently (finder ≠ judge) before reporting. Use for reviewing a diff before a commit/PR, or for a full defensive audit of the codebase.
---

# Security Review (defensive)

A high-precision review workflow. The single most common failure of automated security review
is **reporting unreachable or already-mitigated code**, which buries the real findings. This
skill's rubric exists to prevent that.

## Workflow

```
1. Map entry points & trust boundaries
2. Load the references this code needs
3. Sweep for candidate issues (source → sink)
4. Triage every candidate (the rubric below)
5. Validate independently (finder ≠ judge)
6. Report
```

### Step 1 — Map entry points and trust boundaries

List where attacker-controlled data enters:
- `@RestController` / `@Controller` routes, every `@RequestParam` / `@PathVariable` / `@RequestBody` / `@ModelAttribute` / `MultipartFile`
- headers, cookies, webhooks, queue consumers, third-party API responses
- any LLM input/output (only if the project uses an LLM)

Note where authentication and authorization are enforced — usually centralized in
`SecurityFilterChain` / `@PreAuthorize` / a service-layer check, not per-route.

### Step 2 — Load the references this code needs

- Any JPA/query/controller/DTO/JWT/view → `secure-coding` skill `reference/spring-boot.md`
- Auth / access control / IDOR → `secure-coding` skill `reference/auth-authz.md`
- Any input handling → `secure-coding` skill `reference/input-validation.md`

### Step 3 — Sweep for candidate issues

Walk every source (Step 1) to every dangerous sink (SQL concat, `exec`, `th:utext` /
`dangerouslySetInnerHTML`, file path, `openStream`, `ObjectInputStream`, SpEL, logging). For
each candidate, record the **input → sink path**. If you can't name both ends, it's not a finding.

### Step 4 — Triage every candidate (the rubric)

Before reporting, answer all four. Drop the candidate if any answer is "no":

1. **Is the input actually attacker-controlled?** Trace it to a real entry point. A value that
   only comes from a constant, an enum, or trusted internal config is not an injection source.
2. **Is the sink reachable with that input?** Is there validation, an allowlist, an ORM/`@Query`
   param, or a framework control (auth middleware, `@PreAuthorize`, a base controller) between
   them? Look for centralized enforcement before flagging a route as "missing auth".
3. **What is the blast radius?** Who can trigger it, what do they get, does it cross a trust
   boundary? (SSRF to cloud metadata ≠ SSRF to localhost.)
4. **Can the attacker perform every step?** A header attack needs a client that can set that
   header; a race needs concurrent access. If a step needs a capability the attacker doesn't
   have, it's "Needs verification", not High.

**Severity by exploitability, not by pattern.** State the concrete path and say explicitly when
a finding is theoretical or defense-in-depth.

### Step 5 — Validate independently (finder ≠ judge)

For every candidate that survives Step 4, a **separate subagent** (the `exploiter` agent, on a
*different model family* than the finder) re-derives the finding from the code — it is **not**
shown the finder's conclusions, only the entry point and file:line. It assigns:

- `Confirmed` / `Needs Review` / `Rejected`
- `confidence` 0.0–1.0

**Only `Confirmed` with confidence ≥ 0.8 goes into the report's actionable list.** `Rejected`
and `Needs Review` are recorded but clearly separated. This finder→judge boundary is what keeps
the signal high.

### Step 6 — Report

Format, highest severity first:

```
## Security Review — <repo/branch> — <date>
Files reviewed: N, lines changed: M

### P0 — Critical
- SEC-001  [CWE-639 | A01:2021]  IDOR: cualquier usuario lee facturas ajenas
  Location:   src/.../FacturaController.java:24
  Path:       GET /api/facturas/{id} → facturaRepo.findById(id) → sin check de ownership
  Impact:     usuario autenticado enumerando ids lee facturas de otros (confidencialidad total)
  Fix:        ownership check en el service layer (ver auth-authz.md §IDOR)
  Confidence: Confirmed (0.95)   Found by: hunter-authz   Validated by: exploiter

### P1 — High
...
### P2 — Medium
...
### P3 — Low
...

### Rejected / Needs review (no accionables aún)
...

### Coverage & limits
- What was reviewed, what was NOT (e.g. "no revisé migraciones de BD ni la config de nginx")
- Residual risks / suggested follow-ups
```

Clean review (no findings): say explicitly **what was checked**, **what was not covered**, and
**residual risks**. Never pad the report with `Info` items to look thorough — a long list is
what makes real findings get ignored.

## Confidence & severity scale

| Severity | Meaning |
|---|---|
| P0 Critical | Unauthenticated RCE, auth bypass, mass data exposure, forged JWT via leaked secret |
| P1 High | Authenticated exploitation crossing a trust boundary (IDOR into other tenants, SQLi behind login) |
| P2 Medium | Needs unusual preconditions, or impact limited to the attacker's own data |
| P3 Low | Defense-in-depth gap with no demonstrated exploit path |
| Info | Hardening suggestion — say plainly it is not a vulnerability |

| Confidence | Meaning |
|---|---|
| Confirmed (≥0.8) | Clear source→sink with attacker control proven |
| Likely (0.5–0.8) | Real pattern but reachability uncertain — investigate |
| Needs verification | Conditions needed that the code doesn't show |
| Rejected | False positive (already mitigated / not attacker-controlled) |

## Multi-family anti-bias

Finders (hunters) and the judge (`exploiter`) must be on **different model families**, and the
judge gets a fresh context with only the file:line + entry point — never the finder's analysis.
Disagreements escalate to a third family (`reviewer` on Google Gemini) before closing. This is
configured in the agent frontmatter; see `AGENTS.md`.
