# Kenang — Agent Pipeline (MASTER_PROMPTS) · BYOK Edition

Orchestration package for building **Kenang** (desktop memory-video generator, **sell-the-software + Bring-Your-Own-Key** model) with AI coding agents (Claude in VS Code / Claude Code). Source of truth: `PRD_Kenang_Video_Kenangan.md` **v1.1**.

## Business model & build strategy (read this first)
Users bring their own provider API keys (fal required; Gemini/ElevenLabs optional). The desktop app calls providers **directly** — the publisher's backend never touches photos, prompts, or keys. Revenue = software licenses.

**Owner decision (D-002): licensing comes LAST.** The desktop app is built to full, stable, feature-complete quality first — no activation UI, no watermark, no limits during development. A `LicenseGate` stub (DevFull) marks the single seam where Phase 05 later plugs in real licensing. The license backend + store will live on the owner's **dedicated license website** (already prepared).

## Files

| File | Purpose |
|---|---|
| `MEMORY.md` | Persistent project brain: architecture decisions (incl. AD-13 deferral), model routing, contracts, license SKUs. Agents READ first, APPEND decisions. |
| `PROGRESS.md` | Live status: per-phase checklists incl. the **Stabilization** gate, blockers, open questions. Agents UPDATE after every task. |
| `MASTER_PROMPT_00_POC.md` | Phase 0 — Model, policy & **single-key** validation (GATE). |
| `MASTER_PROMPT_02_DESKTOP_SHELL.md` | Desktop skeleton + BYOK foundation: KeyVault, API Key Manager, `core/providers`, ConfigRepository, **LicenseGate stub**. |
| `MASTER_PROMPT_03_STORYBOARD.md` | Input wizard + client-side analysis/moderation + storyboard editor + cost estimator. |
| `MASTER_PROMPT_04_VIDEO_PIPELINE.md` | Direct-to-fal generation, TTS, FFmpeg assembly, cost tracker, export. |
| `MASTER_PROMPT_01_BACKEND.md` | **(Deferred)** License & Config mini-backend on the dedicated license website. Starts only after Stabilization. |
| `MASTER_PROMPT_05_LICENSING_LAUNCH.md` | **(Last)** Turn licensing on (swap the stub), store wiring, ProGuard, packaging, beta. |

## Execution order & gates

```
00 POC (GATE) ──────────────┐
                            ├──> 03 STORYBOARD ──> 04 VIDEO PIPELINE ──> STABILIZATION ("app sempurna")
02 DESKTOP SHELL ───────────┘                                                   │
                                                                                v
                                              01 LICENSE BACKEND (situs lisensi) ──> 05 LICENSING & LAUNCH
```

- `00` is a **hard gate for 03 and 04**. `02` may run in parallel with `00`.
- `01` and `05` are **frozen until the Stabilization checklist in PROGRESS.md is fully checked** — do not start them earlier, even if idle.
- During development the app runs with **no license enforcement**; config comes from a bundled `app-config.json` (same schema as the future remote config).

## How to run one agent session
1. Fresh agent session with: the relevant `MASTER_PROMPT_XX` + `MEMORY.md` + `PROGRESS.md`.
2. Agent reads MEMORY + PROGRESS first and states a short plan.
3. Small verifiable steps; conventional commits (`feat(scope): ...`).
4. Before ending: update PROGRESS checkboxes, append decisions to MEMORY §9, list next actions.

## Repo layout (recommended)
- Desktop: new repo `kenang-desktop` (Kotlin + Compose Multiplatform) — the center of gravity until Stabilization.
- License backend: lives with the owner's dedicated license website (stack per that site; MP01 keeps the contract fixed). Reuses the owner's Midtrans account + mailer.
- This folder lives at `/agents` in each repo.

## Global rules for all agents
1. English for code/comments/commits/docs; Indonesian only for user-facing UI strings.
2. **User media and user API keys must NEVER be sent to any publisher backend, logged, or synced.**
3. **No license checks outside the `LicenseGate` interface** — during development the stub grants full access; leave TODO(D-002) markers at every future touchpoint.
4. All price/routing numbers come from ConfigRepository/PriceBook — never hardcoded.
5. Verify third-party endpoint slugs/prices against live docs before coding; record verified values in `MEMORY.md`.
6. Cost/spend figures shown to users are always labeled as estimates; real billing lives in the user's provider account.
7. Respect the content & ethics guardrails in `MEMORY.md §7` — product requirements, not suggestions.
8. No scope creep: out-of-phase ideas go to `PROGRESS.md → OPEN QUESTIONS`.
