---
name: code-review
description: Use for an independent review of OpenTicker code, a pull request or pre-merge changes; also use when asked to assess correctness, safety or regression risk.
---

# Code review: findings first

1. Read the request/plan/acceptance criteria, then inspect the complete changed diff and enough surrounding code to assess behavior.
2. Look for observable bugs and test gaps; prioritize correctness, Kotlin lifecycle/state/persistence, sports schedule date/time behavior, stock data timestamps and delayed labels, Worker auth/rate limits/cache and secrets, UX legibility and both app variants.
3. Verify test evidence with `run-the-tests` if execution is available. Distinguish passing local fixtures from emulator, physical tablet and live-provider validation.
4. Output findings first: `[severity] path:line — problem; consequence; suggested correction`. Do not hide genuine findings behind praise or generic notes.
5. If no concrete defects found, say so and explicitly note untested areas and residual risks.
6. Return a verdict: BLOCKED, NEEDS FIXES, or READY FOR USER REVIEW. This is never permission to merge/deploy.

Remain independent of the implementation agent. Avoid directly editing the code during the review unless specifically asked to fix findings, then perform another review.
