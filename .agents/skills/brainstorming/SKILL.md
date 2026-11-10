---
name: brainstorming
description: Use before designing a new OpenTicker feature or significant UI/architecture change when requirements or approaches need exploration; do not use for straightforward bug fixes or routine edits.
---

# Brainstorming: choose a practical direction

1. Restate user goals, constraints and what already works. Inspect relevant repository code and existing tests before suggesting a redesign.
2. Generate at least two *meaningfully different* approaches for substantive decisions; compare user experience, code complexity, cost, performance and maintenance.
3. Recommend one option with a reason. Call out trade-offs, unknowns and compatibility with the tablet landscape UI and current sports/weather/stock workflows.
4. Define acceptance criteria and what should explicitly remain unchanged.
5. Seek user choice only where a genuinely consequential trade-off is unresolved. If the user already approved the direction, do not ask for confirmation again.
6. Hand the choice to `writing-plans` when implementation spans multiple modules or carries regression risk.

Output: recommended approach, alternatives, assumptions and measurable acceptance criteria. This skill does not authorize deployment or merge.
