# MASTER_PROMPT 05 — Licensing UX, Store, Packaging & Beta Launch

## PRECONDITIONS
The desktop **Stabilization checklist** in PROGRESS.md is fully checked (the app is "sempurna": stable, feature-complete, dogfooded) AND Phase 01 (license backend on the dedicated license website) is DONE. Spans the license website + desktop repo; keep commits separated per repo.

## ROLE
Full-stack engineer (Laravel + Kotlin) turning the working app into a sellable product for 20–30 beta users.

## SESSION PROTOCOL
Read `MEMORY.md` + `PROGRESS.md`; plan; small commits; update docs. Prices/flags stay in remote config.

## 5.1 STORE (publisher side)
- Wire the owner's **dedicated license website** (already prepared): hero (demo video), privacy pitch ("Fotomu tidak pernah lewat server kami"), SKU cards (Trial download / Personal / Studio per MEMORY §4), FAQ (incl. "berapa biaya API per video?" with the Rp15–45rb table), buy → `POST /checkout` → Midtrans → key by email.
- Alternative channels (Lynk.id/Mayar/Gumroad) = documented option in `docs/CHANNELS.md`, not built now.

## 5.2 ACTIVATION & TRIAL UX (desktop, polish of Phase-02 foundation)
- **Turn licensing ON**: replace `DevFullLicense` with the real `LicenseGate` implementation (activate/heartbeat/deactivate, Ed25519 verification, 30-day offline grace) at the TODO(D-002) seams from Phases 02/04 — this is a swap, not a refactor. Watermark + 3-export Trial rules become live; existing dev/beta installs gracefully drop to Trial with a friendly in-app explanation and a gift-key path.
- First-run: Trial banner explaining watermark + 3-export limit + "Beli lisensi" button (opens store page).
- Trial export counter: stored locally AND echoed via `/events` (anti-trivial-reset; accept that determined users can bypass — no dark patterns, just friction).
- Activate screen: paste key → success animation → watermark off; license card in Settings (plan, seats, update_until, Deactivate).
- Expired `update_until`: app fully works; update prompts offer Update Pass — never lock features retroactively.

## 5.3 HARDENING & PACKAGING
- ProGuard/R8 for the desktop JAR: produce a **working** rules file (keep Compose, Ktor, SQLDelight, kotlinx.serialization, JNA reflection surfaces); verify the obfuscated MSI passes the full E2E of Phase 04.
- Tamper posture: license blob verified with embedded Ed25519 public key on every launch; heartbeat weekly; 30-day offline grace honored; graceful "license invalid" state (Trial fallback), never a crash.
- Versioning `0.9.x-beta`; `/config` min_version hard-block + latest_version soft prompt with download URL. `docs/RELEASE.md` checklist; CI builds MSI artifact.
- Error-translator table finalized (shared i18n): `invalid_key → "API key salah/tidak aktif"`, `provider_balance → "Saldo fal habis — buka halaman billing"`, `rate_limited → "Terlalu banyak permintaan, coba sebentar lagi"`, `content_blocked → empathetic copy + edit suggestions`.

## 5.4 TELEMETRY, SUPPORT & LEGAL
- Events (opt-out, no content/PII): `app_open, trial_export, license_activated, project_created, storyboard_confirmed, video_done, video_failed(error_code), export_done`. Owner metrics page (Phase 01 admin) shows funnel + §11 PRD targets (activation ≥40%, completion ≥70%, trial→paid ≥8%).
- Crash: global handler → local log; "Kirim laporan" is user-initiated upload of the last log only.
- `docs/legal-draft-id.md`: privacy (headline: media never touches publisher servers; providers process under the user's own account — link their policies), terms (license seats, non-commercial vs Studio, prohibited uses per MEMORY §7), BYOK responsibility notice. Owner reviews before publishing; consent gate (Phase 03) links here.

## 5.5 BETA CHECKLIST (execute, don't just write)
1. Clean-VM runs: Trial E2E, then Personal activation E2E, on obfuscated build.
2. Offline test: activate → disconnect 3 days → app works; simulate >grace → correct lock-to-trial behavior.
3. Revoke test: revoke in admin → next heartbeat downgrades gracefully.
4. Kill-switch: flip a tier off in config → desktop hides it live.
5. Fresh-user friction run: a non-developer follows only the onboarding wizard to get a fal key and finishes a video — time it, note stumbles in `docs/BETA_NOTES.md`.
6. Invite 20–30 testers (jasa foto/tribute + community) with gift Personal keys (admin-issued); feedback form linked in-app.

## DEFINITION OF DONE
A stranger can: download Trial → make a watermarked video with their own fal key → buy Personal via Midtrans → receive key by email → activate → export clean video — zero developer intervention, on the obfuscated build. All Phase-05 PROGRESS boxes checked; `docs/LAUNCH_NOTES.md` lists known issues + Fase-2 candidates.
