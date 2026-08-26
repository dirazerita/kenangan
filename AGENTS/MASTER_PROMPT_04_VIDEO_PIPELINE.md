# MASTER_PROMPT 04 — Video Pipeline (Generate → Assemble → Export)

## PRECONDITIONS
Phase 03 DONE (emits `StartGenerationRequest`). FFmpeg present via Phase-02 `FfmpegLocator`.

## ROLE
Senior Kotlin engineer with strong FFmpeg experience. Implement PRD modules **F4 (Generate)**, **F5 (Audio)**, **F6 (Assembly/Export)**. Everything after "Buat Video" until the user holds a finished MP4.

## SESSION PROTOCOL
Read `MEMORY.md` + `PROGRESS.md`; plan; small commits; update docs. Test with the clips produced in Phase 00 whenever possible (cheap iteration).

## 4.1 GENERATION ORCHESTRATOR (client)
- On `StartGenerationRequest`: submit each confirmed scene directly via `FalQueueClient` (model slug per tier from config). **Key failover happens per-scene at submit time** (AD-14): FalQueueClient picks the first non-exhausted fal key, fails over on balance-exhausted, and the chosen `key_label` is stored on the GenJob row → store provider_job_ids per scene as GenJob rows incl. `key_label` (MEMORY §6).
- Poll fal job status per provider_job_id with backoff 5s→10s→15s cap, **always using the key recorded on the job** (`key_label`) — never the current submit key; resume polling after app restart from DB.
- Per-scene UI states: Antre → Diproses (spinner + elapsed) → Selesai (thumbnail) → Gagal (reason + action). Error-code mapping: `content_blocked` → show scene, suggest editing the motion prompt (link back to storyboard for that scene; no auto-retry); `invalid_key` → deep-link to API Keys settings; `provider_balance` (only raised after failover has exhausted ALL fal keys) → **reuse the existing partial-generation pause UX** with the updated CTA "Top up / kelola key"; `provider_failed/timeout` → auto-retry 1× then offer manual retry. Copy is honest that some failed attempts may still be billed by the provider and that **each fal account bills its own usage** (multi-key, AD-14).
- On done: append a `CostTracker` entry (duration × PriceBook rate for the model, tagged with the job's `key_label`) and download the MP4 from `output_url` to `projects/<id>/clips/` (resume-capable download, sha check if provided). Partial success is allowed: user may assemble with completed scenes (confirm dialog) or wait/retry.

## 4.2 AUDIO
- Narration: `POST /tts` full text once → `narration.mp3`. Generate `subtitles.ass` from the narration text: split into ≤42-char lines, distribute timings proportionally to sentence lengths across narration duration (no ASR needed), style per ratio (bottom-center 9:16 higher safe-area; classy white with soft shadow, no boxes).
- Duration rule (F5.3): if narration_len > total_video_len → dialog with three choices: extend scene durations (regenerates? NO — only if scenes not yet generated; else disable), trim text (reopen editor), or tempo +10% (ElevenLabs speed param if supported, else ffmpeg `atempo=1.1`). Default suggestion picked automatically.
- Music: trim/loop to video length.

## 4.3 FFMPEG ASSEMBLY (local)
Build one command via a typed `FfmpegGraphBuilder` (unit-tested string output):
- Video: scale+crop each clip to exact target (1080x1920 or 1920x1080), `settb/fps=30` normalize, chain `xfade=transition=fade:duration=0.6` between scenes; final `fade=in:0:15` and fade-out.
- Audio: `[music]aloop/atrim → volume` + `[narration]` → sidechain ducking (`sidechaincompress=threshold=0.05:ratio=8:attack=5:release=300`) → `amix` → `loudnorm=I=-16:TP=-1.5:LRA=11` (single pass ok for MVP).
- Subtitles: `subtitles=subtitles.ass` (escape Windows paths!). Toggleable.
- Watermark: implemented but **gated by `LicenseGate`** — with the DevFull stub it is always OFF during development (D-002). Keep the overlay path (`overlay=W-w-24:H-h-24`) implemented and covered by a flag-forced unit test so Phase 05 can switch it on without touching FFmpeg code. Same for the trial 3-export counter hook: TODO(D-002) marker only.
- Output: `output/<project>_<ratio>.mp4`, H.264 `-crf 20 -preset medium -pix_fmt yuv420p`, AAC 192k, `-movflags +faststart`, metadata `comment=AI-generated (Kenang)`.
- Progress: parse `-progress pipe:1` → percent bar. Run in temp dir; atomic move on success. Assembly of 30s/1080p must finish < 60s on a mid laptop.

## 4.4 RESULT SCREEN
Embedded preview: attempt VLCJ-based player; if bundling/licensing friction, fallback = large thumbnail + "Putar" (opens system player) — record the choice in MEMORY §9. Buttons: Simpan Sebagai…, Buka Folder, Salin Lokasi. Shows: durasi, ukuran file, tier, dan **estimasi biaya proyek (USD + Rp)** dari CostTracker. "Buat versi rasio lain" = P2 stub (disabled tooltip).

## TESTS
Unit: FfmpegGraphBuilder (2/3/6 scenes, both ratios, watermark on/off, subs on/off), ASS timing splitter, downloader resume. Integration: assemble from 3 local sample clips + sample mp3s committed under `testdata/` (tiny files).

## DEFINITION OF DONE
E2E on a real project: confirmed storyboard (3 scenes) → generated via backend (Standar tier) → narration+music+subtitle assembled → both a 9:16 and a 16:9 project exported, playable in Windows Media Player & WhatsApp upload. Failure path demonstrated once (kill network mid-generate → resume works). All Phase-04 PROGRESS boxes checked; `docs/demo-04.md` with output samples.
