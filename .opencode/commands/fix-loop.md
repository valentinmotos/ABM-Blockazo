---
description: Closed-loop remediation for a single finding. Implementer fixes it, an independent reviewer on a different family verifies, the file is re-hunted, and security-tester locks it in with a regression test. Max 3 iterations then escalate.
subagent: false
---

Fix finding `$1` (a `SEC-0xx` id from `.security/findings.json`) using a closed loop. Max 3
iterations; if it's still open after 3, stop and escalate to me instead of looping forever.

## Loop (per iteration)

1. **Load the finding** from `.security/findings.json` — its location, input→sink path, impact,
   and any `poc` path. If the id doesn't exist, list the open findings and ask which to fix.

2. **Fix** — launch the `implementer` subagent (OpenAI). Give it the finding and the reference
   pattern (`secure-coding/reference/...`). It must follow the security contract and the
   Definition of Done; it edits production code.

3. **Verify independently** — launch the `reviewer` subagent (a **different model family** than
   the implementer) with a fresh context on the changed file. It re-derives whether the fix
   actually closes the input→sink path — not just whether the code "looks" changed.

4. **Re-hunt the touched area** — relaunch the matching hunter (`hunter-authz` / `hunter-injection`
   / `hunter-web` / `hunter-config`, whichever produced the finding) scoped to the changed file,
   to catch a variant the fix introduced.

5. **Lock it in** — launch `security-tester` to write a JUnit regression test that FAILS on the
   old code and PASSES on the fix. Run it with the project's runner to confirm.

6. **Update state.** If reviewer says fixed AND the test passes: set `status: fixed` (then
   `verified` after a clean re-hunt) in `.security/findings.json`, record the fix commit/file,
   and record the guarding test path. Otherwise, loop again with the reviewer's specific concern
   passed to the implementer.

## After the loop

Report per iteration: what changed, the reviewer's verdict, the re-hunt result, and the test
result. When closed, state the fix file:line, the test that guards it, and the final status.
If you hit 3 iterations, stop and give me a clear summary of what's still open and why.
