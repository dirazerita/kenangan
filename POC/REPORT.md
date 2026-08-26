# REPORT — Phase 00 PoC & Policy Gate — FINAL (2026-08-25)

## GATE VERDICT: **PASS**

All four gate questions answered with positive evidence; owner review complete
(REVIEW_SHEET.md, 49 scored rows). Total spend **$12.54 of $40.00 (31%)** — fal $12.53,
ElevenLabs ≈$0.02 (est.), Google $0. 72 rows in `results.csv` with per-key labels.

Scoring interpretation note: the owner filled numeric scores in every column; verdict
column read as 4–5 = pass, 3 = borderline, ≤2 = fail. The sheet's summary block was left
blank; the figures below are computed from the row scores.

## Q1 — Face consistency: PASS (target ≥8/10 core cases)

- **41 of 44 visual outputs scored 4–5** on face_consistency (93%).
- **By core test case: 11/11 pass** (foto01 BW, foto03 faded, foto05 group, foto07 elderly,
  foto10 child, fusion A/B/C, kakek1, kakek2, keluarga ~20-person group) — every case's
  outputs are predominantly ≥4. The only 3s: Seedance Mini on the group photo (2 clips)
  and Wan 2.6 on the group beach clip (also the sheet's only verdict-2).
- Owner's own-family set scored 4–5 face consistency across all 6 outputs — including the
  ~20-person group keyframe + clip, well beyond the 4-subject product limit.
- Caveats already folded into prompts: fusion needs the "exactly N people, no additional
  people" clause (std hallucinated an extra subject without it — that output scored verdict 3).

## Q2 — Policy rejection rate: 0% (target <10%)

0 content-policy refusals in 47 successful generation calls across Nano Banana std/Pro,
Kling 3.0 Std, Wan 2.6, Seedance 2.0 Mini, MiniMax TTS, fal VLM — photo set included
deceased-era BW portraits, elderly, children, a pet, and real family photos.
All 19 logged errors were account-side (EL free tier 402, invalid Google key, fal balance).

## Q3 — Costs & latency (all slugs verified; MEMORY §3 locked)

| Layer | Slug | Price | Avg latency |
|---|---|---|---|
| Keyframe std | `fal-ai/nano-banana/edit` | $0.039/img | 15 s |
| Keyframe pro | `fal-ai/nano-banana-pro/edit` | $0.15/img | 27 s |
| I2V Standar | `fal-ai/kling-video/v3/standard/image-to-video` (audio off) | $0.084/s | 108 s |
| I2V Hemat (prov.) | `wan/v2.6/image-to-video` 720p (flash variant $0.05/s untested) | $0.10/s | 56 s |
| I2V A/B | `bytedance/seedance-2.0/mini/reference-to-video` | $0.155/s @720p | 158 s |
| TTS id | `fal-ai/minimax/speech-02-hd` voice `Calm_Woman` | $0.10/1k chars | 5 s |
| VLM | `openrouter/router/vision` + `google/gemini-2.5-flash` | ~$0.001–0.002/photo | 5.5 s |

Per 15s video (3×5s + 3 keyframes + analysis + TTS): **Standar ≈ $1.42** ✓ (assumption $1.47),
**Premium ≈ $2.20** ✓ ($2.74), **Hemat on Wan flash ≈ $0.92** ✓ ($0.94) *if* flash quality
verifies — plain Wan 2.6 720p ≈ $1.67 misses the target.

## Q4 — Single-key (fal-only): YES — locked

Full pipeline (VLM analysis → keyframe/fusion → I2V → Indonesian TTS) proven on one FAL_KEY.
Owner blind-test: MiniMax `Calm_Woman` (verdict 4 on both narrations) beats MiniMax
`Wise_Woman` (3) and ElevenLabs premade (3) → **MiniMax Speech-02 HD / Calm_Woman is the
single-key default voice**. ElevenLabs remains the optional user-added premium path.

## Locked routing (MEMORY §3 updated)

| Tier | Route |
|---|---|
| **Standar (default tier)** | Kling 3.0 Standard, `generate_audio:false` |
| **Premium** | Kling 3.0 Pro + Nano Banana Pro keyframes |
| **Hemat** | **PROVISIONAL** — Wan 2.6 *flash*; pending a scored spot-check (~$0.75). Until then the app defaults to Standar |
| **A/B** | Seedance 2.0 Mini at **480p only** (owner scores + 720p price rule it out as a tier) |

Best I2V overall per scores: **Kling 3.0 Standard** (8/9 outputs pass, incl. all own-family clips).

## Blocker closures

- B-1 CLOSED (fal topped up; matrix 18/18 complete).
- B-2 CLOSED — **optional**: fal VLM route proven; Gemini-direct diff can wait for a valid key.
- B-3 CLOSED — **not needed**: MiniMax locked as default; ElevenLabs stays optional premium.
- B-4 CLOSED: owner scored 49/49 rows.

## Known-untested (carried into Phase 02+ as notes, none gate-blocking)

1. **Multi-key failover (AD-14) is implemented but has never fired against a real
   balance-exhausted error** — every post-top-up call was served by key1. First real
   exercise will happen naturally or via a deliberately drained test key.
2. Wan 2.6 flash quality (Hemat) — one scored clip decides it.
3. Seedance Mini at 480p — only if the A/B slot is ever used.
4. 10s/15s durations, `end_image_url`, and Kling `elements` — Phase 03/04 concerns.

## GATE: **PASS** → Phase 02 (Desktop Shell) is unblocked. MEMORY §3 has zero TBD.
