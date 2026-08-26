# MASTER_PROMPT 00 — PoC & Policy Gate

## ROLE
You are a senior AI-integration engineer. Your job in this phase is to **validate, with real photos and real money, that the chosen models can do this product** — before any app code exists.

## SESSION PROTOCOL
Read `MEMORY.md` and `PROGRESS.md` first. State a short plan. After each task: update PROGRESS, log costs, append decisions to MEMORY §9. Never exceed the budget cap.

## OBJECTIVE
Answer four questions with evidence:
1. **Face consistency** — after vibe transform / fusion / I2V, is the person still recognizably the same? (target ≥ 8/10 test cases pass human review)
2. **Policy rejection rate** — how often do providers refuse real-people photos (incl. deceased, old/BW photos)? (target < 10% on the chosen route)
3. **Real unit costs & latency** per layer vs MEMORY §4 assumptions.
4. **Single-key feasibility (BYOK)** — can the whole pipeline run on ONE fal account? i.e., is there a fal-hosted vision LLM that reliably produces the PhotoAnalysis JSON, and a fal-hosted TTS with acceptable natural Indonesian (e.g., MiniMax Speech family)?

## BUDGET CAP
USD 40 total. Track every call in `poc/results.csv`. Stop and report if 80% consumed.

## SETUP
- `poc/` folder, Python 3.11, `uv` or venv; deps: `fal-client, google-genai, elevenlabs, requests, python-dotenv, pillow`.
- `.env`: `FAL_KEY, GOOGLE_API_KEY, ELEVENLABS_API_KEY` (never commit).
- Test assets (provided by owner, NOT committed to git): 12–15 photos in `poc/assets/` covering: BW/very old, faded color 80s–90s, group family photo (3–4 people), solo elderly portrait, child photo, pet photo, low-res/blurry sample. `manifest.csv`: filename, category, notes.
- Privacy: assets stay local; remote copies are transient API inputs only; delete any provider-hosted assets you can after runs.

## TASKS
**T1 — Verify routes.** Confirm current fal endpoint slugs + per-second/per-image prices for: Nano Banana (std & Pro edit), Kling 3.0 Standard & Pro i2v, Wan 2.6/2.7 i2v, Seedance 2.0 Mini i2v. Identify fal-hosted candidates for (a) a vision LLM for PhotoAnalysis JSON and (b) Indonesian TTS. Confirm ElevenLabs model id + shortlist 3 Indonesian voices (warm-female, warm-male) as the premium comparison path. Record everything in MEMORY §3 table.

**T2 — Vibe transform batch.** Script `01_vibe.py`: for 5 photos × 3 vibes (taman, pantai, ruang tamu) call Nano Banana edit with the standard instruction pattern: *"Place the exact same people in a [vibe] setting. Preserve faces, age, body, and clothing exactly. Photorealistic, warm natural light, [ratio]."* Save to `poc/out/vibe/`.

**T3 — Fusion batch.** Script `02_fusion.py`: 3 pairs (e.g., elderly portrait + adult child photo) → one keyframe of both subjects together in ruang tamu / taman. Test std vs Pro on the hardest pair.

**T4 — I2V matrix.** Script `03_i2v.py`: pick 6 best keyframes (mix of T2/T3 + 2 originals). For each, run **Kling 3.0 Standard, Wan 2.6, Seedance Mini**, 5s, motion prompts only from MEMORY §7 templates (use `smile`, `hug`, `slight_head_turn` + `slow push-in`). At least 2 runs in 9:16 and 2 in 16:9 per model. Save MP4s to `poc/out/i2v/<model>/`. Log: latency, billed cost, HTTP/policy errors verbatim.

**T5 — TTS.** Script `04_tts.py`: 2 sample narrations (≈250 chars, warm memorial tone, bahasa Indonesia) × (3 ElevenLabs voices + best fal-hosted candidate). Blind-compare and note whether fal-only quality is acceptable as the default voice. Also run one PhotoAnalysis JSON extraction through the fal VLM candidate on 3 photos and diff against Gemini output.

**T6 — Results log.** `results.csv` columns: `task, provider, model, input_ref, ratio, duration_s, latency_s, cost_usd, status, error_code, notes`.

**T7 — Human review + report.** Generate `REVIEW_SHEET.md` (one row per output: file, face_consistency 1–5, artifacts 1–5, emotional_quality 1–5, verdict). Owner scores it. Then write `REPORT.md`: scores summary, rejection rates per provider, real cost per 15s video per tier, latency, and the **GATE decision**:
- **PASS** → lock routing table in MEMORY §3 (fill TBDs) **including the single-key verdict** (fal-only OK / Gemini and-or ElevenLabs keys recommended).
- **PARTIAL** → propose adjusted routing (e.g., swap Hemat model) with evidence.
- **FAIL** → list concrete blockers + options (different models, restoration pre-step, product pivot).

## DELIVERABLES
```
poc/ ├─ 01_vibe.py 02_fusion.py 03_i2v.py 04_tts.py
     ├─ assets/ (local only)  out/  results.csv
     ├─ REVIEW_SHEET.md  REPORT.md  .env.example  README.md
```

## DEFINITION OF DONE
All T1–T7 checked in PROGRESS; MEMORY §3 has zero `TBD`; REPORT.md contains an explicit gate verdict; total spend ≤ $40 and reconciled against provider dashboards.

## GUARDRAILS
No motion prompts outside the allowed templates. No public-figure photos. If a provider rejects a photo, record it — do not attempt jailbreak-style prompt evasion; workaround attempts are limited to legitimate rephrasing within templates.
