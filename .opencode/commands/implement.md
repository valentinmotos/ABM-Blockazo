---
description: Secure implementation loop for a feature. Delegates to the implementer subagent (OpenAI), then audits the diff with an independent reviewer on a different family, then runs a security-review gate. Use $1 to describe the feature.
subagent: false
---

Implement the following feature/fix: `$1`

Run it through the secure implementation loop (see `AGENTS.md`):

1. **Implement** — launch the `implementer` subagent (OpenAI, effort high). It loads the
   `secure-coding` skill and follows the security contract: deny-by-default auth, parameterized
   queries, DTOs + `@Valid`, service-layer ownership checks, contextual output encoding, secrets
   from env, fail-closed errors. It reports what it changed and the auth decisions it made.

2. **Review independently** — launch the `reviewer` subagent (Google Gemini, a **different
   model family**) with a fresh context on the diff. It is NOT shown the implementer's reasoning.
   It applies the false-positive filter and reports findings in severity order with `file:line`.

3. **Security gate** — if the reviewer raises any P0/P1, run `/fix-loop` on each before calling
   this done. P2/P3 go to `.security/findings.json` for later. Ask me whether to fix P2/P3 now.

4. **Report** — the changed files, the auth decisions, the review verdict, and the final status
   (clean / findings logged). Only report "done" when the Definition of Done in `secure-coding`
   is satisfied and there are no open P0/P1.

This is the default path for ANY code change: implementer writes, a different-family reviewer
audits, the gate blocks on criticals. That's how we keep our own project from being exploitable.
