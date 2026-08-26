# Kenang — Agent Rules
This repo is driven by the agent pipeline in /agents.

At the start of EVERY session:
1. Read agents/MEMORY.md and agents/PROGRESS.md in full.
2. Work ONLY on the phase the owner names; follow its MASTER_PROMPT exactly.
3. Never start a phase whose dependencies are not DONE in PROGRESS.md.

Before ending a session: update PROGRESS.md checkboxes, append new decisions
to MEMORY.md §9 (DECISIONS LOG), and list next actions.

Small verifiable steps; conventional commits (feat/fix/docs...).
Code, comments, and docs in English; user-facing UI strings in Indonesian.
Never hardcode prices/model slugs (use config); never log or transmit user API keys.