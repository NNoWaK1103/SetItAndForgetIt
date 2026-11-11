# OpenTicker Codex instructions

This repository is a native Kotlin/Jetpack Compose Android tablet dashboard with sports/weather feeds and an optional authenticated stock-data proxy (Node.js and Cloudflare Worker). Read README.md and the relevant module before editing.

## Roles and skill routing

The main Codex session coordinates work. Use project agents when delegation materially helps; for small changes, do not spawn agents merely to satisfy a checklist:
- `architect-planner` for complex or ambiguous work: [brainstorming](.agents/skills/brainstorming/SKILL.md), then [writing-plans](.agents/skills/writing-plans/SKILL.md).
- `developer-debugger` for implementation and failures: [systematic-debugging](.agents/skills/systematic-debugging/SKILL.md) when diagnosing defects; [run-the-tests](.agents/skills/run-the-tests/SKILL.md) after code changes.
- `independent-reviewer` for a distinct review pass: [code-review](.agents/skills/code-review/SKILL.md) and [run-the-tests](.agents/skills/run-the-tests/SKILL.md) as appropriate.

Select skills by actual task need, or explicitly via `$skill-name`; inspect their SKILL.md before following them. Do not claim a skill was loaded without an execution trace showing it was read. Never treat writing or listing a skill as proof it ran. For anything beyond trivial documentation changes, include a brief record of selected skills, tests and outcomes in the PR.

## Non-negotiable project invariants

- Preserve working stock, sports, weather, slideshow, data persistence, and both Android app build variants. Keep the standard `com.openticker.app` package and isolated preview package distinct.
- Use real feed values only; keep delayed/stale/unknown states clearly labeled. Do not fabricate prices, scores, game states, or live availability.
- Keep Alpaca/provider credentials and Worker access tokens out of source, logs, screenshots, tests, APKs and PR descriptions. Never bypass Worker authentication, request limits, caching or data-licensing safeguards.
- Never deploy to Cloudflare, modify live credentials, change production settings, or merge to `main` without the user's explicit approval. Use a feature branch and draft PR for changes.
- Avoid touching unrelated files or changing the app just to accommodate agent configuration.
- Record device-only verification separately: local fixtures, emulator preview and GitHub CI are not evidence of live provider behavior or physical-tablet testing.
- For conflicting subagents, assign separate paths or integrate sequentially; the main agent reviews combined diffs.

## Required delivery gates

1. Define expected behavior and regression risks before implementation; use `writing-plans` for multi-file/high-risk changes.
2. Fix root causes, not symptoms; use `systematic-debugging` for failures.
3. Run targeted tests for edited code, then the relevant broader suite. Follow `run-the-tests`; report the exact commands, results and tests not run. Never call a task tested based solely on code inspection.
4. Request a genuinely separate code review (or state clearly when unavailable). Address critical/high findings before declaring ready; follow `code-review`.
5. Do not call a PR ready until CI results are actually observed. Do not merge without approval.

See [agent workflow and skill evaluation](docs/AGENT_WORKFLOW.md) for test prompts, verification and handoff.
