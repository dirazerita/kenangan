# MASTER_PROMPT 02 — Desktop Shell (Compose Multiplatform)

## ROLE
Senior Kotlin engineer. Create repo `kenang-desktop`: a Windows-first Compose Multiplatform (JVM) application skeleton **plus the BYOK foundation** (key vault, provider clients, config repository, and a stubbed `LicenseGate`) that Phases 03/04 plug into. **Licensing is deferred (D-002): no activation UI, no watermark, no limits in this phase.** UI language: Indonesian. Code/comments: English.

## SESSION PROTOCOL
Read `MEMORY.md` + `PROGRESS.md`; plan; small commits; update docs. Record any library choice not fixed here as a decision in MEMORY §9.

## STACK (fixed unless MEMORY says otherwise)
Kotlin (latest stable) · Compose Multiplatform Desktop · Coroutines/Flow · Ktor Client · SQLDelight (SQLite) · Koin DI · kotlinx.serialization · Napier or kotlin-logging · jpackage MSI via Compose Gradle plugin. Verify current stable versions before pinning; record them in MEMORY.

## PROJECT LAYOUT
```
kenang-desktop/
 ├─ app/            # composeApp: UI, navigation, DI wiring
 ├─ core/data/      # ConfigRepository (bundled app-config.json → optional CONFIG_URL later), DTOs, repos
 ├─ core/providers/ # KeyVault, FalQueueClient, PriceBook, CostTracker (see below)
 ├─ core/db/        # SQLDelight schema + DAOs
 ├─ core/common/    # Result/Either, error mapping, dispatchers, i18n strings (ID default)
 └─ agents/         # this pipeline folder
```

## LOCAL DB (SQLDelight)
Tables mirroring MEMORY §6: `project(id, name, ratio, vibe, tier, narration, music_path, status, created_at, updated_at)`, `photo(id, project_id, local_path, upload_id?, analysis_json?)`, `scene(...Scene contract fields..., local_keyframe_path?, local_clip_path?)`, `job(id, scene_id, backend_job_id, status, error_code?)`, `output(id, project_id, path, ratio, tier, est_cost_usd, created_at)`, `gen_cost(project_id, job_id, model, qty, unit, est_usd, at)` (CostTracker), `settings(key, value)` (keys reserved for the future LicenseState cache + trial counter — unused while `LicenseGate` is stubbed). All writes in transactions; app must resume any project after force-kill.

## APP DATA LOCATIONS
`%APPDATA%/Kenang/` → `db/`, `projects/<id>/{photos,keyframes,clips,output}/`, `logs/`, `tools/ffmpeg/`, `config/app-config.json` (user-editable override; falls back to the bundled resource). Helper `AppDirs` object; never write beside the EXE.

## SCREENS (this phase)
1. **LicenseGate (stub only — D-002)** — define the seam now, enforce nothing: `interface LicenseGate { fun state(): LicenseState }` with a `DevFullLicense` implementation (full access, no watermark, no export limits) wired through DI. All future license touchpoints (activation screen, watermark, trial counter) must read only from this interface; grep-able TODO(D-002) markers where Phase 05 will plug in. First launch goes straight to the onboarding wizard → Home — no license UI anywhere.
2. **Home** — project grid (thumbnail = first photo/keyframe, status chip), top bar shows a subtle "Dev Build" tag (sourced from LicenseGate) + this-month estimated API spend from CostTracker, "Proyek Baru" button (navigates to a placeholder route Phase 03 will own), delete project (confirm; wipes folder).
3. **Settings** — **API Keys manager** (see below), output folder default, language (ID only for now), telemetry opt-out placeholder, app version (update check reads config `min_version` but stays dormant until the remote config endpoint exists — Phase 01).
4. **About/Legal** — links: privacy, terms, licenses (FFmpeg attribution).

## API KEY MANAGER (BYOK — core of this phase)
Secure storage: Windows Credential Manager via JNA (`CredWrite/CredRead`), AES-GCM encrypted-file fallback (per-machine derived key); values masked in UI (`fal_…a1b2`), never logged, never synced. Fields: **fal (wajib)**; Google Gemini (opsional — "analisis lebih tajam"); ElevenLabs (opsional — "suara premium"). Each field has **Tes koneksi**: fal → cheapest 1-token LLM ping (show "biaya tes < $0.001"); Gemini → `models.list` (free); ElevenLabs → `GET /voices` (free). First-run **onboarding wizard**: 3 guided steps with screenshots, "Buka halaman pembuatan key" browser buttons, paste field, live test, and the BYOK responsibility notice (MEMORY §7); skippable, reopenable from Settings.

## PROVIDER CLIENT LAYER (`core/providers/`)
Foundation for Phases 03–04 (there is no backend proxy):
- `KeyVault` (above) · `PriceBook` (loads config price_hints + fx_idr from ConfigRepository — bundled `app-config.json` during development, identical schema to the future remote config; `estimate(modelSlug, qty)`) · `CostTracker` (writes `gen_cost`, exposes per-project & per-month sums).
- `FalQueueClient`: submit/status/result against fal queue endpoints (slugs from config), exponential backoff, error mapping → `AppError { content_blocked | invalid_key(401) | provider_balance(402) | rate_limited(429) | provider_failed | timeout }`.
- `GeminiClient` + `ElevenLabsClient` (thin, optional) and a `TtsProvider`/`AnalysisProvider` facade choosing fal-hosted defaults vs premium keys when present.
- Unit tests with recorded JSON fixtures; zero real network calls in CI.

Global patterns: single `AppError` → snackbar/dialog mapping with the Indonesian error-translator (401 "API key salah/tidak aktif", 402 "Saldo fal habis — buka halaman billing", 429 "Terlalu banyak permintaan", `content_blocked` empathetic copy), offline banner + read-only mode when network unreachable, loading skeletons, no blocking dialogs during network calls.

## FFMPEG BUNDLING
Gradle task downloads a pinned ffmpeg release zip at build time, verifies sha256, stages into packaged app resources; first run copies to `tools/ffmpeg/` and runs `ffmpeg -version` smoke check. `FfmpegLocator` service exposed for Phase 04. Keep licensing note in About.

## PACKAGING
`packageMsi` produces installable MSI < 300 MB; app name "Kenang (Beta)"; version from gradle property; icon placeholder ok. Document build steps in `docs/BUILD.md`. CI (GitHub Actions) building MSI artifact = P1.

## DEFINITION OF DONE
Fresh Windows machine: install MSI → onboarding wizard → enter a fal key and get a green "Tes koneksi" → create/delete placeholder project → kill app → relaunch (state + key intact) → start with network disconnected (app opens fine; AI features show offline state). **No license UI, no watermark anywhere; `LicenseGate` stub + TODO(D-002) markers in place.** All Phase-02 PROGRESS boxes checked; screenshots in `docs/shell-walkthrough.md`.
