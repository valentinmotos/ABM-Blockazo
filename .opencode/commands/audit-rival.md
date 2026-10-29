---
description: "White-box security audit of a CLONED third-party repository (authorized/competition use). Runs the offensive pipeline: recon, secrets, config, JWT, authz/IDOR, injection, mass assignment, front-end; then validates, crafts PoCs and writeups. Scope = the local clone ONLY."
subagent: false
---

Run an offensive (white-box) security audit of the repository at path `$1`. This is a **local
clone** for an authorized university competition. Follow the `offensive-audit` skill.

## Scope — hard rules (no exceptions)
- Target is the local clone at `$1` (or a competition-provided instance) ONLY. Do NOT probe
  university infrastructure, teammates' or competitors' machines, or anything outside scope.
- **No destructive actions.** Never `DELETE`/`DROP`/`TRUNCATE`. To prove an IDOR, `GET` the
  other object — don't modify it. Never use leaked credentials to log in; just report they exist.
- Read-only against the clone unless building a PoC into `.security/poc/` (confirm with me first).

## Pipeline
1. **Recon.** Launch `threat-modeler` on `$1` → writes `$1/.security/attack-surface.json`
   (routes, auth posture, entity model, top-5 risks). It also runs
   `.opencode/scripts/scan.sh $1` (gitleaks on git history + config scan) — a committed
   `jwt.secret` or DB password is an immediate P0.
2. **Hunt in parallel** (read the surface first): `hunter-authz` (IDOR — score the most here),
   `hunter-injection`, `hunter-web`, `hunter-config`; `hunter-llm` only if an LLM dependency
   exists. Point them at the clone's `src/`, not mine.
3. **Validate (finder ≠ judge).** Each candidate → `exploiter` on a different family than the
   finder, fresh context, file:line only.
4. **Craft PoCs.** Each `Confirmed` (≥0.8) → `exploit-craft` writes a read-only PoC to
   `.security/poc/<id>.http`.
5. **Writeups.** Each PoC → a writeup using `offensive-audit/reference/writeup-template.md`,
   compiled into `.security/report-rival.md`. One writeup per finding (a bulk "IDOR everywhere"
   is worth N concrete file:line writeups). Order by scoring value: secrets/actuator → IDOR →
   mass assignment → SQLi → JWT.

Report the findings summary, the PoC/writeup paths, and the coverage note (what you did NOT
check). Ask before executing any PoC against a live instance.
