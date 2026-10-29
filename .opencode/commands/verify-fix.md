---
description: Confirms a finding's remediation is real and locked in. Verifies the fix closes the input→sink path, and that a regression test exists that fails on the old code and passes on the fix.
subagent: false
---

Verify that finding `$1` is genuinely fixed and locked in. This is a confirmation gate, not a fix.

1. **Load the finding** from `.security/findings.json` — its location, input→sink path, and the
   recorded fix. If it's not marked `fixed`, say so and stop.

2. **Confirm the fix closes the path.** Launch the `reviewer` subagent (independent, fresh
   context) on the changed file. It must confirm the specific input→sink path is now blocked —
   not merely that code changed. Ask for the `file:line` of the actual guard.

3. **Confirm the regression test exists and bites.** Launch `security-tester` to:
   - locate the guarding test in `src/test/` (or `.security/tests/`),
   - run it (`mvn test` / `./mvnw test` / `gradle test`),
   - verify it FAILS on the vulnerable code and PASSES on the fix (re-run against the pre-fix
     state if feasible via git stash, otherwise reason from the test's assertions).
   A test that passes on both the broken and fixed code is not a real guardrail — flag it.

4. **Re-hunt** the touched file once more with the matching hunter to confirm no variant remains.

5. **Update** `.security/findings.json`: set `status: verified`, record the guarding test path,
   the fix `file:line`, and the verifying agents.

Report: the fix location, the guard that blocks the path, the test result (before/after), the
re-hunt result, and the final status. If anything doesn't hold, set the finding back to `open`
and explain why.
