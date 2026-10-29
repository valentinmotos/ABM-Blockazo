---
description: Iterative bug hunting with a fresh perspective per pass. Runs the hunter set, then re-hunts with an adversarial "what did I miss" angle. Use $1 for the number of passes (default 2).
subagent: false
---

Run an iterative bug-hunting loop over this repository, $1 passes (default 2 if not given).

The point of the loop: the first pass uses the standard hunters. Each subsequent pass uses a
**fresh perspective** — a new `general` subagent (different model family each time) told to
assume the first pass MISSED something, and to hunt specifically for what a tired reviewer
overlooks: business-logic flaws, TOCTOU/race conditions, authorization on *writes* (not just
reads), and combinations of two individually-safe features that become unsafe together.

## Per pass

1. **Pass 1 (standard):** dispatch `hunter-authz`, `hunter-injection`, `hunter-web`,
   `hunter-config` (in parallel), plus `hunter-llm` only if an LLM dependency exists.
2. **Pass n>1 (adversarial):** launch a fresh `general` subagent with a different model family
   than the previous pass. Prompt: "Assume the prior passes missed a real bug. Hunt only for
   what they overlooked — business logic, write-side authz, race conditions, feature
   combinations, and any route not covered. Read `.security/attack-surface.json` and the prior
   findings in `.security/findings.json` so you don't repeat them — find what's NOT there."
3. **De-duplicate** against `.security/findings.json` — don't re-log a finding that's already open.
4. **Validate** new candidates with `exploiter` (different family than the finder).

## After all passes

- Report what each pass found and what was newly discovered vs. already known.
- Log new `Confirmed` findings to `.security/findings.json`.
- Report the marginal yield per pass (if pass 3 found nothing new, that's a signal to stop).
- Ask whether to `/fix-loop` any open finding.

Never exceed the requested number of passes. This is a hunting loop, not a fixing loop — don't
edit code here.
