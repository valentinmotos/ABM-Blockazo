---
description: Full defensive security audit of this repository. Runs threat modeling, dispatches all hunters in parallel on different model families, validates each candidate independently, and produces a report + findings.json.
subagent: false
---

Run a full defensive security audit of this repository (my own project). This is the pre-release
deep pass. Follow the `security-review` skill.

## Pipeline — dispatch these in order, subagents in parallel where noted

1. **Threat model (recon).** Launch the `threat-modeler` subagent. It writes
   `.security/attack-surface.json` and reports the top-5 riskiest areas. Wait for it — the
   hunters read its output.

2. **Hunters — launch in PARALLEL.** Read the attack surface, then start these subagents at the
   same time (they're independent):
   - `hunter-authz` (IDOR / broken access control) — highest scoring, be exhaustive
   - `hunter-injection` (SQLi, Sort/Pageable, SpEL, deserialization)
   - `hunter-web` (XSS, CSRF, CORS, session, SSRF)
   - `hunter-config` (secrets, actuator, weak crypto, devtools)
   - `hunter-llm` — ONLY if `.security/attack-surface.json` has `llmUsed: true`; otherwise skip it.

3. **Validate (finder ≠ judge).** For every candidate the hunters report, launch the `exploiter`
   subagent on a **different model family than the hunter that found it**. Give it only the
   `file:line` and entry point — NOT the hunter's analysis. Collect its
   Confirmed/Needs-Review/Rejected + confidence.

4. **Report.** Compile in `security-review` skill format (P0→P3), each finding with location,
   input→sink path, impact, fix, confidence, and which agents found/validated it. Separate the
   `Confirmed` (≥0.8) actionable list from `Needs review`/`Rejected`. Include a coverage &
   limits note (what was NOT reviewed).

5. **Log** all actionable findings to `.security/findings.json` (new `SEC-0xx` ids, severity,
   confidence, `found_by`, `validated_by`, `status: open`, and a `poc` path if one exists).

6. **Optional test coverage.** For each `Confirmed` P0/P1, offer to launch `security-tester` to
   write a JUnit regression test — ask me before running, since it writes to `src/test/`.

Do NOT fix anything yet — this is an audit. At the end, list the findings and ask whether to
run `/fix-loop` on any of them.
