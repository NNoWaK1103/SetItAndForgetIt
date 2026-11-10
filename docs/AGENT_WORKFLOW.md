# OpenTicker agent workflow and verification

These are **Codex project agents**, not background processes or app-runtime bots. The main Codex session acts as coordinator. `AGENTS.md` supplies repository policy; `.codex/agents/*.toml` describes specialist roles; `.agents/skills/*/SKILL.md` supplies reusable task procedures.

## Routing (avoid unnecessary delegation)

| Work | Agent | Skills |
| --- | --- | --- |
| Unclear new feature / substantial UI architecture | architect-planner | brainstorming, then writing-plans |
| Multi-file approved feature | architect-planner (brief plan), developer-debugger | writing-plans, run-the-tests |
| Bug, failing test or broken feed | developer-debugger | systematic-debugging, run-the-tests |
| PR or release readiness | independent-reviewer | code-review, run-the-tests |
| Typo or tiny isolated change | main agent | only relevant skills; avoid role overhead |

Subagents inherit access/permissions from the parent; a TOML file alone cannot grant tools or start autonomous work. The user must run Codex against this repository and request/delegate a task.

## Skill utilization verification

1. From repository root, run `python3 scripts/verify-agent-config.py`. This checks present files, YAML frontmatter metadata and parseable TOML, **not runtime selection**.
2. In Codex, inspect the skills list (`/skills` when available) and confirm the five skill names; restart/reload the session if files were added after launch.
3. **Explicit activation smoke test:** ask `Use $systematic-debugging to investigate a hypothetical stale HD quote. Do not edit any files; summarize the investigation steps.` Check the activity trace to verify Codex opened the corresponding SKILL.md and followed its procedure.
4. **Natural activation smoke test:** without naming the skill, ask `A quoted HD price is stale while the Worker returns 200. Investigate the likely failure path without modifying code.` Verify the debugger selects systematic-debugging on its own. Compare against the explicit test.
5. **Negative trigger test:** ask `Fix a spelling mistake in README.md only; do not modify anything else.` Verify it does **not** launch an elaborate brainstorming/planning process.
6. **Review handoff test:** on a draft diff, request an independent review. Verify `code-review` was actually read and that findings include file/line references and evidence.
7. **Test proof:** read terminal exit codes, uploaded test reports and exact GitHub Actions run URLs. Make sure the claimed command actually ran. A CI pass does not prove a skill was invoked.
8. Record the results in each substantial PR: which role worked, which skill files were observed in traces, which tests ran, and unresolved checks. If traces are inaccessible, mark skill invocation `UNVERIFIED`, not `PASS`.

### Evaluation rubric

- **Configured:** project TOML parses, five skills have names/descriptions, Codex discovers them.
- **Triggered:** Codex explicitly reads the appropriate SKILL.md (check trace, not self-report).
- **Applied:** output/actions follow the skill's steps and produce concrete evidence.
- **Effective:** required tests/review find or prevent regressions; CI passes. This requires task-specific evaluation.

For repeated tests, reuse the same representative positive/negative prompts and compare results after changing the skill descriptions. Avoid assuming that a few smoke tests guarantee future invocation.

## Branch and release policy

- Work on feature branches; open draft PRs. Main agent coordinates and integrates nonoverlapping contributions.
- Never change deployed Cloudflare secrets, deploy a Worker, merge PRs, or install an APK on the user's device without explicit approval.
- Full Android APK and emulator workflows are under `.github/workflows/`. The configuration workflow only validates agent files; it cannot run Codex agents.
- An agent cannot truthfully report `READY FOR USER REVIEW` until required CI results have been observed and known critical findings are resolved.
