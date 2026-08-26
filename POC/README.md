# Kenang — Phase 00 PoC

Validates the AI routes in `agents/MASTER_PROMPT_00_POC.md` with real photos and real money.

## Setup

```powershell
python -m venv .venv
.\.venv\Scripts\pip install fal-client google-genai elevenlabs requests python-dotenv pillow
# copy .env.example -> project root ..\.env and fill keys
```

## Scripts (run in order, from `poc/`)

| Script | Task | What it does |
|---|---|---|
| `00_t1_verify.py` | T1 | ElevenLabs Indonesian voice shortlist (free metadata calls) |
| `01_vibe.py` | T2 | 5 photos × 3 vibes via `fal-ai/nano-banana/edit` → `out/vibe/` |
| `02_fusion.py` | T3 | 3 photo pairs merged (std + Pro on hardest pair) → `out/fusion/` |
| `03_i2v.py` | T4 | 6 keyframes × Kling 3.0 Std / Wan 2.6 / Seedance 2.0 Mini, 5 s → `out/i2v/` |
| `04_tts.py` | T5 | TTS: ElevenLabs vs MiniMax Speech-02 HD (id) + PhotoAnalysis: fal VLM vs Gemini → `out/tts/`, `out/analysis/` |

All scripts are idempotent (skip existing outputs), log every call to `results.csv`,
and hard-stop at 80% of the $40 budget (`kenang_poc.budget_guard`).

## Outputs

- `results.csv` — task, provider, model, input_ref, ratio, duration_s, latency_s, cost_usd, status, error_code, notes
- `errors.log` — verbatim provider errors
- `REVIEW_SHEET.md` — owner scores each output 1–5 (face consistency, artifacts, emotional quality)
- `REPORT.md` — gate verdict (PASS/PARTIAL/FAIL) + single-key feasibility

## Privacy

`ASSETS/` stays local and out of git. Uploads to fal storage are transient API inputs only.
