#!/usr/bin/env python3
"""Validate OpenTicker project-agent metadata. Does not test live skill invocation."""
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
ROLES = ("architect-planner", "developer-debugger", "independent-reviewer")
SKILLS = ("brainstorming", "code-review", "run-the-tests", "systematic-debugging", "writing-plans")


def verify():
    policy = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
    for role in ROLES:
        path = ROOT / ".codex" / "agents" / f"{role}.toml"
        with path.open("rb") as handle:
            config = tomllib.load(handle)
        assert config.get("name") == role, f"wrong name in {path}"
        assert config.get("description", "").strip(), f"missing description in {path}"
        assert len(config.get("developer_instructions", "").strip()) > 80, f"missing instructions in {path}"
        assert role in policy, f"role not documented in AGENTS.md: {role}"
    for skill in SKILLS:
        path = ROOT / ".agents" / "skills" / skill / "SKILL.md"
        content = path.read_text(encoding="utf-8")
        sections = content.split("---", 2)
        assert len(sections) == 3 and not sections[0].strip(), f"missing YAML frontmatter: {path}"
        header = sections[1].strip().splitlines()
        metadata = dict(line.split(": ", 1) for line in header if ": " in line)
        assert metadata.get("name") == skill, f"wrong skill name in {path}"
        assert len(metadata.get("description", "").strip()) >= 35, f"missing trigger description in {path}"
        assert len(sections[2].strip()) >= 120, f"skill content too short in {path}"
        assert skill in policy, f"skill not documented in AGENTS.md: {skill}"
    print(f"OK: {len(ROLES)} agent TOML files and {len(SKILLS)} SKILL.md files validated.")
    print("NOTE: runtime skill selection and execution traces must be tested in Codex.")


if __name__ == "__main__":
    try:
        verify()
    except (AssertionError, FileNotFoundError, ValueError, tomllib.TOMLDecodeError) as exc:
        print(f"Agent configuration invalid: {exc}", file=sys.stderr)
        sys.exit(1)
