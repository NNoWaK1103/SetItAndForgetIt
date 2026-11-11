---
name: writing-plans
description: Use for multi-file OpenTicker features, migrations, major redesigns or high-risk fixes that need an ordered implementation and testing plan; skip for trivial isolated edits.
---

# Writing plans: executable, regression-aware tasks

1. Inspect affected app/server directories, existing tests, and docs. Confirm the current behavior and boundaries.
2. Break the approved design into small ordered tasks with concrete paths, dependencies and expected results. Assign tasks to agents only when edits can be isolated.
3. For every task, list acceptance criteria, targeted tests, edge cases and a fallback or rollback strategy.
4. Consider Kotlin/Compose lifecycle and settings persistence, both APK variants, feed provenance and missing-data states, Worker auth/rate limits/caching and CI.
5. Keep a minimal task-status checklist; record assumptions and unresolved questions rather than inventing facts.
6. Build a review gate: developer verification, independent code review, green relevant CI, then a *draft* PR; no automatic merge.

Output: implementation order, files, acceptance checks, risks, rollback and a short verification matrix. Never equate an unexecuted test plan with passing tests.
