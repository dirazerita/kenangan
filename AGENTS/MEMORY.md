# MEMORY.md — Kenang (Project Brain) · BYOK

> Persistent context for all agent sessions. READ FIRST. Append-only for decisions; edit tables only when a decision supersedes them.

## 1. Product snapshot

Desktop app (Windows first) that turns old photos (childhood, deceased loved ones, late pets) into short cinematic memory videos. Flow: **Input (photos, optional narration, music, 9:16/16:9, vibe) → AI analysis → editable storyboard (keyframe + motion prompt per scene) → user confirms → per-scene image-to-video → local FFmpeg assembly → shareable MP4.**

**Business model: sell the software, not credits.** Users bring their own API keys (BYOK) and pay providers directly, with no markup. Revenue = licenses (Trial free / Personal / Studio). Publisher backend is a mini service (license + config + store) and never receives user media or keys. Full spec: `PRD_Kenang_Video_Kenangan.md` v1.1.

## 2. Architecture decisions (AD)

| ID | Decision | Rationale |
|---|---|---|
| AD-01 | Desktop client = Kotlin + Compose Multiplatform (JVM, Windows MSI via jpackage) | Existing Kotlin skillset; shared future with Android |
| AD-02 | **BYOK: desktop calls AI providers DIRECTLY with the user's own keys.** Publisher backend = license activation/validation, remote config, store, update check, opt-out telemetry only — hosted on the owner's **dedicated license website** (already prepared), separate from the app repo | Zero publisher COGS; strongest privacy story; tiny backend |
| AD-03 | Providers: fal.ai (keyframe, I2V, TTS, VLM analysis in single-key mode); Google Gemini optional (higher-quality analysis); ElevenLabs optional (premium voice). **Goal: single-key onboarding = only a fal account required** (feasibility verified in Phase 00) | Minimize BYOK setup friction |
| AD-04 | Pipeline is keyframe-based: photo → (edited) keyframe → I2V | Storyboard previewable/editable before expensive spend; I2V model-agnostic |
| AD-05 | Final assembly is local FFmpeg on user machine | Fast, free, private |
| AD-06 | Backend mini is stateless HTTPS (no queue, no media storage, no uploads) — trivial on Hostinger shared hosting | Complexity removed by BYOK |
| AD-07 | Desktop polls the fal queue directly (submit → status → result) with backoff; resume-safe via SQLite | No webhooks; works behind NAT |
| AD-08 | Licensing (design unchanged): Ed25519-signed license blobs (public key embedded in app), device-fingerprint binding, weekly heartbeat, **30-day offline grace**, deactivate-to-move-seat. Trial = full features + watermark + max 3 exports | Simple, indie-provable, honest about limits — **implementation deferred to the final phase (AD-13)** |
| AD-09 | **User media never touches publisher servers.** Provider-side retention follows each provider's own policy under the user's account; disclosed in help docs. This is the product's headline privacy claim | Grief content demands it; also removes compliance burden |
| AD-10 | Config is the control lever: model slugs, tier routing, PriceBook rates, vibes, versions. **Development uses a bundled local `app-config.json` (identical schema); a remote `CONFIG_URL` on the license website takes over when Phase 01 ships.** | Provider churn is constant; switching local→remote must be a one-line change |
| AD-11 | User keys stored via Windows Credential Manager (JNA), AES-GCM file fallback; masked in UI; never logged/synced | Key safety |
| AD-12 | Content guardrails enforced client-side (motion-template validator + moderation pre-check via the user's key), provider safety filters as second layer. ProGuard obfuscation. No marketing claims of unbypassable filters | Honest, tamper-aware posture |
| AD-13 | **Licensing deferred until the desktop app is stable** (owner decision). Development builds run full-featured through a `LicenseGate` stub (`state() = DevFull`: no activation UI, no watermark, no export limits). Every future license touchpoint (activation screen, watermark, trial counter, update gating) must read ONLY from this interface | "Software sempurna dulu" — Phase 05 swaps the stub for the real implementation with zero refactor |
| AD-14 | **Multi-key fal failover.** API Key Manager stores an ORDERED LIST of fal keys `[{label, key}]` (Google/ElevenLabs stay single). FalQueueClient submits with the first non-exhausted key; on fal's balance-exhausted error (signature from Phase 00 T4: `User is locked. Reason: TOP_UP.` / `User is locked. Reason: Exhausted balance. Top up your balance at fal.ai/dashboard/billing.`) it marks that key exhausted (timestamp + 10-min cooldown) and retries the submit once with the next key. In-flight jobs keep polling with the key that submitted them. GenJob + `gen_cost` store `key_label` → costs reportable per key | Jasa/studio users run per-client fal accounts (per-client billing); graceful degradation instead of a hard stop when one balance runs dry — exactly the failure hit in Phase 00 T4 |

## 3. AI model routing (validated/updated by Phase 00)

| Layer | Default (single-key via fal) | Optional quality path | Notes |
|---|---|---|---|
| Analysis + story plan + prompts | `openrouter/router/vision` + gemini-2.5-flash — **LOCKED** (Phase 00: 3/3 valid JSON) | Gemini Flash (user's Google key) | No native JSON mode via router → app validates + retries |
| Keyframe / vibe / fusion | Nano Banana via fal — **LOCKED** | Nano Banana Pro (Premium tier; also cleanest for fusion) | Fusion prompts MUST include "exactly N people, no additional people" |
| Image-to-video | Kling 3.0 Standard `generate_audio:false` — **LOCKED, default tier** (Hemat: **DISABLED at launch** — Wan 2.6 flash spot-check scored verdict 3, D-009 · Premium: Kling 3.0 Pro · A/B: Seedance 2.0 Mini 480p only) | — | 5s/10s per scene; Kling audio default is ON → must set false (+50% cost otherwise) |
| TTS (id-ID) | `fal-ai/minimax/speech-02-hd` voice `Calm_Woman`, `language_boost:"Indonesian"` — **LOCKED** (owner blind-test winner) | ElevenLabs Multilingual v2 (user key, paid plan required for id voices) | ≤500 chars/project |
| Assembly | FFmpeg (bundled) | — | H.264+AAC, 720p/1080p |

**Verified slugs & rates (Phase 00, 2026-08-25):**
- fal VLM analysis: `openrouter/router/vision` + `model:"google/gemini-2.5-flash"` — token-billed via fal, measured ~$0.001–0.002/photo; valid PhotoAnalysis JSON 3/3 (no native JSON mode — app must validate+retry) · fal TTS id-ID: `fal-ai/minimax/speech-02-hd` $0.10/1k chars (voices tested: `Calm_Woman`, `Wise_Woman`, `language_boost:"Indonesian"`) (single-key verdict: **fal-only feasible — proven end-to-end 2026-08-25**; voice-quality acceptance pending owner blind-compare, see poc/REPORT.md)
- fal keyframe std/pro: `fal-ai/nano-banana/edit` $0.039/img / `fal-ai/nano-banana-pro/edit` $0.15/img ($0.30 4K)
- fal i2v hemat/standar/premium: `wan/v2.6/image-to-video` $0.10/s 720p ($0.05/s flash variant) / `fal-ai/kling-video/v3/standard/image-to-video` $0.084/s audio-off ($0.126/s audio-on) / `fal-ai/kling-video/v3/pro/image-to-video` $0.112/s audio-off; A/B: `bytedance/seedance-2.0/mini/reference-to-video` $0.1547/s 720p out ($0.0721/s 480p)
- ElevenLabs model id + 3 Indonesian voice ids: `eleven_multilingual_v2`; Meraki F `OKanSStS6li6xyU1WdXa`, Maya F `U3dExJoUNcmTY5H6GMuG`, Bram M `X8n8hOy3e8VLQnHTUcc5` — ⚠ all Indonesian voices are library voices: **paid EL plan required for API use** (402 on free tier; premade EN voices work free)

## 4. License SKUs & user-side API cost reference

| SKU | Price (proposal) | Contents |
|---|---|---|
| Trial | Free | All features, "Kenang Trial" watermark, max 3 exports |
| Personal | Rp249.000 one-time | 1 device, no watermark, non-commercial, 12-month updates |
| Studio | Rp699.000 one-time | 3 devices, commercial use (jasa), priority support |
| Update Pass | Rp99.000/yr optional | Continued updates after 12 months; app keeps working without it |

User-paid API cost per 15s video (indicative, no markup): Hemat ≈ $0.94 (Rp15,5rb) · Standar ≈ $1.47 (Rp24rb) · Premium ≈ $2.74 (Rp45rb). Estimator uses PriceBook from remote config; always labeled "estimasi".

## 5. Hard constraints

- Backend mini: stateless HTTPS only; no user media, no queue, no key transit. Hostinger-friendly.
- Desktop never freezes (all IO/AI on coroutines); resumable after force-kill (SQLite state).
- Max MVP: **15 photos in** (D-012), **12 scenes, 120s total video** (D-013), 4 subjects per fusion scene, 500-char narration.
- Installer < 300 MB incl. FFmpeg. No GPU requirement.
- All AI features require network + valid user key; license valid offline within 30-day grace.
- Every cost figure shown is an estimate; the app never promises exact billing.
- Development builds: NO license/watermark/trial enforcement — the `LicenseGate` stub is the single seam; never scatter license checks outside it (AD-13).

## 6. Data contracts (canonical)

```json
// PhotoAnalysis — per photo
{ "photo_id": "p1",
  "subjects": [{"id":"s1","desc":"elderly woman, ~70s, batik dress","face_quality":0.86}],
  "setting": "living room, 1990s aesthetic",
  "era_style": "faded color print",
  "mood": "warm, familial",
  "quality_score": 0.74,
  "issues": ["slight blur","color fading"] }

// Scene — storyboard unit
{ "scene_id":"sc2","source_photos":["p1","p3"],"type":"single|fusion","vibe":"taman",
  "keyframe_prompt_en":"...","keyframe_url":"...",
  "motion_prompt_en":"She turns slightly and smiles warmly; gentle slow push-in.",
  "motion_summary_id":"Beliau menoleh pelan lalu tersenyum hangat; kamera mendekat perlahan.",
  "duration_s":5,"regen_count":1,
  "status":"draft|keyframe_pending|keyframe_ready|confirmed|generating|done|failed" }

// GenJob — client-side job record (BYOK; no credits fields)
{ "job_id":"g_...","scene_id":"sc2","provider":"fal","model":"...","provider_job_id":"...",
  "status":"queued|running|done|failed_retryable|failed_permanent",
  "est_cost_usd":0.42,"error_code":null,"output_url":null }

// LicenseState — cached locally, Ed25519-verified
{ "plan":"trial|personal|studio","key_masked":"KNG-…-9F2A",
  "device_fingerprint":"sha256(...)","activated_at":"...","update_until":"...",
  "last_heartbeat":"...","offline_grace_until":"...","trial_exports_used":1 }

// PriceHint — remote config entry for PriceBook
{ "model_slug":"...","unit":"per_second|per_image|per_1k_chars","usd":0.084 }
// config also carries: fx_idr, vibes[], tier_routing, min_version, latest_version+url, flags
```

## 7. Guardrails (product requirements)

- Motion prompts ONLY from template categories: `smile, blink, slight_head_turn, wave, hug, hold_hands, walk_slowly, look_at_camera, laugh_softly, pet_animal` + camera (`slow push-in`, `gentle pan`, `static`). LLM fills templates; validator enforced **in app code**; never free-form actions.
- Client-side moderation pre-check before paid calls (NSFW/violence → polite rejection). No public figures. Rights/consent attestation before first generation.
- BYOK responsibility notice in onboarding: API usage is billed to the user's provider account and subject to each provider's ToS.
- "AI-generated" metadata on every output; visible watermark in Trial.
- Empathetic UX copy: "hidupkan kenangannya", never "hidupkan orangnya". Video framed as AI interpretation.
- Keys: never logged, never synced, masked in UI. No claims that filters or license checks are unbreakable.
- Voice cloning of the deceased: OUT OF SCOPE — do not build, do not stub.
- Multi-key fal support (AD-14) is only for keys the user legitimately owns (backup or per-client accounts). Onboarding copy must state plainly that creating multiple fal accounts to farm free signup credits violates fal's ToS and risks bans; the app must never suggest or encourage it.

## 8. Glossary

Vibe = target ambience preset. Keyframe = still image a scene's video is generated from. Fusion scene = keyframe combining subjects from ≥2 photos. Tier = Hemat/Standar/Premium model routing (affects the user's own provider bill). PriceBook = client-side rate table from remote config. Seat = one activated device on a license.

## 9. DECISIONS LOG (append-only)

- D-000 (2026-08-24): Pipeline docs created from PRD v1.0. Initial AD-01..AD-10 recorded.
- D-001 (2026-08-24): **PIVOT to BYOK** — monetization changed to selling desktop licenses; users bring their own API keys. AD-02/06/07/08/09 rewritten; AD-11/AD-12 added; single-key (fal-only) feasibility added to Phase 00 scope; credits/refund concepts removed product-wide. PRD bumped to v1.1.
- D-002 (2026-08-24): **Licensing DEFERRED** — owner decision: perfect the desktop software first, add licensing last. New order: 00 → (01 postponed) 02 ∥ 00 → 03 → 04 → Stabilization → 01 → 05. License backend & store will live on the owner's dedicated license website (already prepared). Desktop ships a bundled `app-config.json` until the remote config endpoint exists. AD-13 added; AD-02/08/10 amended.
- D-003 (2026-08-25): **Phase 00 executed — gate PARTIAL (provisional PASS)**, $6.80/$40 spent. Evidence: 0% policy rejection (0/41 generation calls incl. BW/deceased/child photos); single-key fal-only pipeline proven end-to-end (VLM+keyframe+fusion+I2V+id-TTS); §3 slugs/rates all verified. Routing adjustments proposed: Hemat → Wan 2.6 **flash** ($0.05/s; plain 2.6 blows the Rp15,5rb price point), fusion prompts MUST carry an "exactly N people" clause (std hallucinated an extra subject without it), Kling calls MUST set `generate_audio:false` (default on = +50% cost), Seedance 2.0 Mini dropped from Hemat (720p costs > Kling Std). Owner-side blockers before final PASS: fal balance top-up (6 T4 cells missing), new GOOGLE_API_KEY (current invalid), optional paid ElevenLabs plan (id voices are 402 on free tier), owner scores poc/REVIEW_SHEET.md (≥8/10 face-consistency target). See poc/REPORT.md.
- D-004 (2026-08-25): **Multi-fal-key failover approved** (owner). API Key Manager stores an ORDERED LIST of fal keys (label + key) instead of a single key; FalQueueClient fails over to the next key when a submit fails with the exact balance-exhausted signature captured in Phase 00 T4 results (`User is locked. Reason: TOP_UP.` / `…Exhausted balance…`); in-flight jobs keep polling with the key that submitted them; exhausted keys carry a 10-minute cooldown timestamp before retry; GenJob and `gen_cost` records store `key_label` for per-key cost reporting (per-client billing, jasa segment). AD-14 added; §7 anti-credit-farming guardrail added; MASTER_PROMPT_02 §API Key Manager/§Provider Layer and MASTER_PROMPT_04 §4.1 amended; PoC shared helper patched (optional `FAL_KEY_2`/`FAL_KEY_3` rotation, `key_label` column in results.csv).
- D-007 (2026-08-25): **Phase 02 marked DONE by owner** (owner confirmed the three pending verification items via session gate question) → Phase 03 started same day.
- D-008 (2026-08-25): **Phase 03 executed — DONE, scripted demo PASSED against real fal APIs** ($0.2036 spend; see kenang-desktop/docs/demo-03.md). Storyboard engine complete: 5-step wizard (autosaved, blur badges via variance-of-Laplacian, consent gate), analysis pipeline (moderation pre-check → PhotoAnalysis with PoC schema prompt → template-constrained story plan; validator + auto-repair in app code — "dancing wildly" repairs to walk_slowly), keyframe jobs (Nano Banana, state machine w/ crash recovery), live estimator (formula per MP03: I2V seconds + regen×per-image; verified == hand computation), confirm dialog → `StartGenerationRequest` seam for Phase 04. **fal API integration facts (encoded in FalStorage/FalQueueClient, learned the hard way):** ① upload = v3 CDN flow (`POST rest.fal.ai/storage/auth/token?storage_type=fal-cdn-v3` → `POST v3.fal.media/files/upload`); the old `storage/upload/initiate?storage_type=gcs` returns 400 "Invalid storage type". ② queue status/result URLs DROP slug sub-paths — `{owner}/{alias}/requests/{id}` (e.g. `openrouter/router/requests/…`, NOT `…/router/vision/requests/…` → 405); submit-response URLs are stored on the job. ③ photos are downscaled to ≤2048px JPEG q0.85 before upload (originals untouched locally) — full-res scans time out on slow uplinks and buy nothing for VLM/keyframe quality; 3 upload attempts for transient EOFs. Scene duration persisted via new `project.scene_duration_s` column (migration 1.sqm). Leftovers non-blocking: bundled music assets, drag-reorder, EL voice preview, ≤30s analysis re-measure. **Wan-flash spot-check ($0.75) still open → do at Phase 04 start (Hemat gate).**
- D-006 (2026-08-25): **Phase 02 executed — desktop shell code-complete** in `kenang-desktop/` (own git repo, 3 conventional commits; becomes standalone later). Stack pinned (verified on Maven Central 2026-08-25): Kotlin 2.4.10, Compose Multiplatform 1.12.0 + **material3 as direct artifact `org.jetbrains.compose.material3:material3:1.9.0`** (accessor deprecated in CMP 1.12) + material-icons-core 1.7.3, Ktor 3.5.2, SQLDelight 2.3.2, Koin 4.2.2, kotlinx.serialization 1.11.0, coroutines 1.11.0, JNA 5.19.1, Napier 2.7.1, Gradle 9.7.1 (wrapper committed), JDK 21 (Temurin 21.0.12.1). Implementation decisions: modules are plain Kotlin/JVM (not KMP targets) — desktop-only for now, restructure only when Android happens; FFmpeg pinned to BtbN `autobuild-2026-08-24-13-10` / `ffmpeg-n8.1.2-44-g7c533d0f86-win64-gpl-8.1.zip` sha256 `5efb8182…bdab` (gyan.dev blocks scripted downloads), Gradle task verifies hash and stages only ffmpeg.exe; PriceBook prices token-billed VLM analysis as `per_image` $0.002/photo (approximation of §3 measurement); hemat price hint = wan flash $0.05/s on the base slug; MSI upgradeUuid `7c1f4b3a-58d2-4e96-b1aa-c3958d20ef11` (never change); Credential Manager stores the whole fal list as one JSON blob (2560-byte CredWrite cap — fine for ~10 keys). Verified live: MSI 135 MB, app launches, JNA CredWrite/Read/Delete selfTest OK, SQLite survives force-kills, 26 offline unit tests green (incl. AD-14 failover against exact T4 balance signatures). Owner-pending for phase DONE: clean-machine MSI install, real-fal-key green test, offline-start check.
- D-014 (2026-08-26): **Wizard content expansion** (owner requests): ① Narration voices 2 → **15 MiniMax system voices**, config-driven (`tts.voices`, Indonesian labels + gender); only Calm_Woman/Wise_Woman are blind-tested (D-005), the rest offered as-is. ② Narration box **prefilled** with an editable warm template (new projects only; clearing it = no narration). ③ Vibes 5 → **50** (config-only; original 5 ids kept so old projects stay valid; wizard shows two scrollable chip-card rows). ④ Bundled music 1 → **27 tracks** (~103 MB at 128kbps, Kevin MacLeod CC BY 4.0, uniform credit; MSI measured **238 MB** < 300 MB cap). Owner asked for 50: NOT met — 50 full tracks at listenable bitrate ≈ +190 MB and breaks the §5 installer cap, and the CC-BY catalog needed hand-curation (29 of 56 candidate URLs 404'd). Path to 50+: download-on-demand track store via remote config (Phase 01+), logged as KI-018.
- D-013 (2026-08-26): **Scene cap raised 6 → 12; total-duration cap 60s → 120s** (owner request). Config-only (`limits.max_scenes`, `limits.max_total_s`); the story planner already targets `min(maxScenes, photos)` and the storyboard/estimator/orchestrator/assembly all scale off the scene list. `max_total_s` had to move too, otherwise 12 scenes × 10s (120s) would silently break the documented cap — note it is a DOCUMENTED constraint only, never enforced in code (no validation exists yet; candidate for Stabilization). **Cost consequence, must stay visible in UI:** a full 12-scene Standar project is ~$5.5 at 5s/scene (12×5s×$0.084 I2V + 12×$0.039 keyframes) and ~$10.5 at 10s/scene — 2× the old ceiling and far above the §4 "Standar ≈ $1.47/15s" reference figure, which describes a 3-scene video and is unchanged. Assembly of a 120s 1080p video runs ~4× the 30s benchmark (~2 min), still well inside the "never freezes" rule but outside the 60s assembly target's original scope. FfmpegGraphBuilderTest gained a 12-scene case (11 xfades, 53.4s at 5s/scene). MEMORY §5 + PRD F2.2 updated.
- D-012 (2026-08-26): **Photo input limit raised 10 → 15** (owner request during dogfooding). Changed in `limits.max_photos` only; wizard validation already read it from config, and the two UI strings now interpolate `%1` instead of hardcoding the number, so a future change is config-only. Scene cap stays 6 and total video stays ≤60s — extra photos give the story planner more material to choose/group from, they do not add scenes. Cost/latency impact: analysis is per-photo (~$0.002 and ~6s each), so a 15-photo project runs ~50% longer and costs ~$0.03 more at the analysis stage than a 10-photo one. MEMORY §5 and PRD (§66, F1.1) updated; MASTER_PROMPT_03 left as the historical record of what Phase 03 was asked to build.
- D-011 (2026-08-26): **Source pushed to GitHub as a monorepo** — `https://github.com/dirazerita/kenangan`. The `kenang-desktop/` git repo (18 commits) was moved up to the project root so AGENTS/, POC/, PRD and the app live in ONE repo with history intact (a later standalone split is still possible via `git subtree split`, amending D-006's "own repo" note). Root `.gitignore` permanently excludes: `.env` (real user API keys, AD-11), `.tools/` (~1 GB toolchain), `**/build/`, `app/ffmpeg-dist/` (138 MB pinned binary), `POC/.venv/`, `POC/out/` (200 MB generated clips) and **all personal family media** — `POC/ASSETS/`, `OUTPUT/`, plus the four `docs/img` screenshots showing identifiable relatives (AD-09: user media stays on the machine). Pushed tree = 173 files / 3.7 MB. Docs referencing the excluded screenshots have intentionally broken image links.
- D-010 (2026-08-25): **Phase 04 executed — video pipeline DONE, E2E PASSED both ratios vs real fal APIs** ($3.05 of $15 phase cap; see kenang-desktop/docs/demo-04.md). Orchestrator: submit-time key failover (AD-14), GenJob rows persisted BEFORE polling → force-kill resume proven live with 0 resubmissions; polling 5s→10s→15s pinned to `key_label`, Offline/Timeout treated as transient (network-kill survives in place). Audio: MiniMax full-narration TTS + narration-proportional .ass subtitles (no ASR) + F5.3 dialog (tempo 1.1 default, extend disabled post-gen). Assembly: FfmpegGraphBuilder (xfade 0.6, sidechain ducking, loudnorm, `-ar 48000` — loudnorm otherwise leaves 96kHz AAC that breaks some players), watermark strictly behind LicenseGate (flag-forced test). **Implementation decisions:** ① Result screen playback = fallback (thumbnail + "Putar" opens system player); VLCJ rejected for MVP (~80 MB LGPL natives + licensing friction). ② DB driver switched to `SQLiteDataSource.asJdbcDriver()` with WAL + busy_timeout 10s + `transaction_mode=IMMEDIATE` — stock JdbcSqliteDriver's deferred BEGIN made concurrent read→write transactions die with SQLITE_BUSY (busy_timeout never applies to lock upgrades); regression-locked by DbConcurrencyTest. ③ Wan variant routing: config keeps base slug (PriceBook key) + `i2v_params.variant` appended as slug sub-path at submit only. ④ Bundled music shipped: config-driven `bundled_music` list, "Heartwarming" (Kevin MacLeod, CC BY 4.0 — credit must stay in About/wizard), staged from app resources. Owner-pending for full DoD sign-off: WMP + WhatsApp playback check of the two outputs, in-app screen walkthrough.
- D-009 (2026-08-25): **Hemat tier locked DISABLED at launch.** Wan 2.6 flash spot-check (slug `wan/v2.6/image-to-video/flash` verified live, $0.25, REVIEW_SHEET row 50) scored by owner: face 4 / artifacts 4 / emotional 3 / **verdict 3** — below the ≥4 bar vs Kling Std (verdict 4, same keyframe). Per phase rule (≤3 → disable): `tier_routing.tiers.hemat.enabled=false, provisional=false` in bundled app-config; Standar stays default tier. Hemat can return post-launch via remote config if a better budget model appears. §3 amended; ConfigRepositoryTest updated.
- D-005 (2026-08-25): **Phase 00 gate = PASS** (owner scored REVIEW_SHEET.md 49/49 rows; face consistency 11/11 core cases, 41/44 outputs at 4–5; policy rejections 0%; spend $12.54/$40). Routing LOCKED in §3: Standar = Kling 3.0 Std audio-off (default tier, best I2V per scores), Premium = Kling 3.0 Pro + NB Pro, TTS = MiniMax Speech-02 HD `Calm_Woman` (single-key default; beats ElevenLabs premade in blind test), VLM = openrouter/router/vision. Hemat = Wan 2.6 flash **PROVISIONAL** pending one scored spot-check (~$0.75); until then app defaults to Standar. Seedance 2.0 Mini demoted to 480p A/B only. B-2 closed as optional (fal VLM proven), B-3 closed as not-needed (EL stays optional premium path), B-4 closed. Multi-key failover (AD-14) implemented in PoC but NOT yet exercised against a real balance error. **Phase 02 unblocked.**
