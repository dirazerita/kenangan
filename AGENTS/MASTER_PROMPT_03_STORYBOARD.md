# MASTER_PROMPT 03 — Storyboard Engine (Input Wizard → Editable Storyboard)

## PRECONDITIONS
Phase 00 = PASS (MEMORY §3 has no TBD) and Phase 02 DONE. (Phase 01 is deferred — D-002 — and is NOT a dependency: config comes from the bundled `app-config.json`.) If not, stop and record blocker.

## ROLE
Senior Kotlin/Compose engineer. Implement PRD modules **F1 (Input)**, **F2 (Analysis, client side)**, **F3 (Storyboard)** inside `kenang-desktop`, wired to the Phase-01 backend. This phase ends at the moment the user presses **"Buat Video"** and the confirm dialog hands a validated payload to Phase 04.

## SESSION PROTOCOL
Read `MEMORY.md` + `PROGRESS.md`; plan; small commits; update docs. UI copy Indonesian, warm and empathetic per MEMORY §7 (never "hidupkan orangnya").

## WIZARD (screen flow, single window, back-safe, autosaved to DB each step)
**Step 1 — Foto.** Drag-drop / picker, 1–10 photos. Local validation: mime, ≤20MB, min side ≥512px, quick blur heuristic (variance of Laplacian via a small pure-Kotlin/JVM impl) → per-photo quality badge (Bagus/Cukup/Kurang) with plain-language tips. Reorder thumbnails.
**Step 2 — Cerita (opsional).** Narration textarea (counter /500), voice selector (from config voices) with 1-click preview (via `TtsProvider` — fal-hosted default, ElevenLabs bila key tersedia; tampilkan hint "biaya ±$0.001"; cache lokal).
**Step 3 — Musik.** Bundled royalty-free list (stream local preview, tag by mood: haru/hangat/ceria/khidmat) OR file upload (MP3/WAV, copyright warning modal, checkbox acknowledge).
**Step 4 — Format & Vibe.** Ratio toggle 9:16/16:9 (visual frames), vibe grid from config (thumbnail+desc, "Original" first), scene duration 5s/10s, "Restorasi foto lama" toggle (P1: can ship as visible-but-disabled if backend flag off).
**Step 5 — Consent gate (first project only).** Rights/consent attestation dialog per MEMORY §7; store acceptance timestamp in settings.

## ANALYSIS FLOW
On wizard finish (BYOK — nothing is uploaded to the publisher backend): run a client-side moderation pre-check per photo (cheap VLM safety-category call via `core/providers`) → PhotoAnalysis JSON via `AnalysisProvider` (fal VLM default / Gemini when key present) → story plan from the same LLM with the **motion-template validator enforced in app code** (port MEMORY §7 rules; reject & auto-repair non-template verbs) → persist `PhotoAnalysis[]` + Scene[] (status=draft). Full-screen progress with stage labels ("Membaca foto…", "Menyusun cerita…"); `content_blocked` → empathetic explanation + which photo + edit options. Target ≤ 30s for 5 photos; show elapsed time.

## STORYBOARD SCREEN (core UX of the product)
- Header: project name, ratio badge, vibe badge, **live cost estimator** (see below), tombol "Buat Video".
- Grid of Scene cards (LazyVerticalGrid, drag-to-reorder). Card contents: keyframe image (placeholder shimmer until keyframe job done — auto-trigger Nano Banana keyframe jobs via `FalQueueClient` for all scenes on first entry), scene number, duration chip, `motion_summary_id` text, expandable editor.
- Card actions: **Edit prompt** (dialog: editable `motion_prompt_en` constrained by template picker — category dropdown + camera dropdown + free adjective field ≤ 8 words; regenerate `motion_summary_id` locally via simple mapping), **Regenerate keyframe** (unlimited; button shows a per-regen cost chip "±$0.04" from PriceBook; calls FalQueueClient), **Hapus** (min 1 scene must remain), fusion badge for `type=fusion`.
- Empty/failed keyframe states with retry.

## COST ESTIMATOR (provider cost — informational)
`estimate_usd = Σ scenes (duration_s × price_per_second[tier_model]) + regens × price_per_image` — ALL rates from PriceBook (config price_hints — bundled `app-config.json` during development; fx_idr for the Rp display), never hardcoded; recompute on every mutation. Always labeled: "Estimasi — tagihan riil ada di akun provider Anda". Provider balance cannot be known client-side; if a `402` occurs during work, the error translator explains "Saldo fal habis" with a button opening the fal billing page.

## CONFIRM DIALOG ("Buat Video")
Shows: scene count, total duration, tier selector (Hemat/Standar/Premium with one-line tradeoff copy), final estimated cost (USD + Rp), a first-time note "biaya ditagih provider langsung ke akun Anda", and the disclaimer line ("Video adalah interpretasi AI dari fotomu"). On confirm: set scenes → `confirmed`, persist, emit `StartGenerationRequest(projectId, tier)` event → Phase 04 owns everything after. Feature-flag stub allowed until 04 lands.

## STATE MACHINE (persisted per scene)
`draft → keyframe_pending → keyframe_ready → confirmed` (+ `keyframe_failed` w/ retry). Any app restart restores exact screen state.

## DEFINITION OF DONE
Scripted demo (`docs/demo-03.md`): 4 real photos incl. 1 fusion-worthy pair → wizard → storyboard renders all keyframes → edit 1 prompt, regen 1 keyframe, reorder, delete 1 scene → estimator matches hand-computed PriceBook values → confirm dialog fires an event whose payload validates against the MEMORY §6 Scene contract → the template validator is proven by attempting one forbidden free-form verb and seeing it rejected/repaired. All Phase-03 PROGRESS boxes checked.
