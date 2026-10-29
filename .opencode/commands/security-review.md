---
description: Security review of the current changes (diff). Maps entry points, dispatches an independent reviewer on a different model family, triages with a false-positive filter, and logs findings to .security/findings.json.
subagent: false
---

Security review of my current uncommitted changes. Follow the `security-review` skill.

!`git status -sb && echo "---DIFF---" && git diff && echo "---STAGED---" && git diff --cached`

Steps:

1. **Map entry points** in the diff above: every controller, `@RequestParam`/`@PathVariable`/
   `@RequestBody`/`@ModelAttribute`, and any JPA query, JWT handler, view, or config touched.
2. **Dispatch the `reviewer` subagent** (a different model family than whoever wrote this code)
   with a fresh context. Give it ONLY the changed files and entry points — not my reasoning.
   Ask it to apply the false-positive filter and report in severity order with `file:line`.
3. **Triage its output** using the four-question rubric. Drop false positives. Keep only
   `Confirmed` ≥0.8 in the actionable list.
4. **Report** in `security-review` skill format: P0→P3, each with location, input→sink path,
   impact, fix, confidence, and which agent found/validated it.
5. **Log** every actionable finding into `.security/findings.json` (create the file if needed)
   with a new `SEC-0xx` id, severity, confidence, `found_by`, `validated_by`, and `status: open`.

If the diff is empty, say so and ask whether to review the staged changes or a specific commit
range. Do NOT implement fixes until I choose an option at the end.
