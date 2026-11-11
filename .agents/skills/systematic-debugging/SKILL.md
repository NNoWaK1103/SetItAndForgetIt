---
name: systematic-debugging
description: Use whenever OpenTicker has a crash, bad quote, missing sports/weather data, failed build, failed test or regression; investigate reproducible root cause before patching.
---

# Systematic debugging: evidence before fixes

1. Write the precise symptom, expected behavior, environment, version and evidence; distinguish live data, fixtures, emulator and physical tablet.
2. Reproduce with the smallest reliable case, a failing test or an existing captured log. If reproduction is not possible, state why.
3. Trace the entire relevant path (provider -> proxy/cache -> parser -> state -> UI) and inspect recent changes; rank competing hypotheses.
4. Change one variable or add one diagnostic at a time. Do not simultaneously scatter speculative fixes.
5. Add or update a regression test that fails for the original defect when feasible, then implement the smallest root-cause correction.
6. Use `run-the-tests` for targeted and broader verification. If symptoms persist, revisit the hypothesis instead of accumulating patches.
7. Record root cause, evidence, exact commands/results, limitations and regressions checked.

Protect API tokens and credentials in all diagnostic output. Do not bypass or weaken authentication, data-freshness labels or rate limiting to make a test pass.
