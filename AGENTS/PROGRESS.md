# PROGRESS.md — Kenang (BYOK · licensing deferred)

> Agents: update checkboxes after EVERY completed task. Keep STATUS header current.

## STATUS
- Current phase: **04 — DONE code-complete (D-010, E2E PASSED both ratios vs real API). Next: STABILIZATION**
- Order (D-002): 00 (gate) → 02 (∥ 00) → 03 → 04 → **STABILIZATION** → 01 → 05
- Phase states: 00 ☑ PASS | 02 ☑ DONE | 03 ☑ DONE | 04 ☑ **DONE** (owner playback check pending) | STAB ☐ **unblocked** | 01 ☐ (FROZEN until STAB) | 05 ☐ (blocked by 01)
- Last session: 2026-08-25 — Phase 04 executed: Hemat locked DISABLED (D-009, spot-check verdict 3); orchestrator/TTS/subtitles/FFmpeg assembly/result screen built; E2E on own-family photos PASSED both ratios ($3.05 of $15 cap, per-call ledger in gen_cost); force-kill resume proven (0 resubmissions); SQLITE_BUSY concurrency fix (WAL+IMMEDIATE, DbConcurrencyTest). See kenang-desktop/docs/demo-04.md.
- ✅ Wan 2.6 flash spot-check CLOSED (D-009): clip generated ($0.25), owner scored verdict 3 → **Hemat locked DISABLED at launch**, Standar stays default. Optional: valid GOOGLE_API_KEY for the Gemini path.
- Phase-03 leftovers (non-blocking, tracked in demo-03.md): bundled music assets, true drag-reorder, EL voice preview, analysis ≤30s target re-measure on normal uplink (Stabilization gate).
- Toolchain (local, not in repo): `.tools/jdk21` (Temurin 21.0.12.1), `.tools/gradle-9.7.1`, pinned FFmpeg zip in `.tools/dl/` (reuse via `-PffmpegLocalZip=`). Owner fal keys seeded in Credential Manager (`Kenang/fal_keys`: Utama + Cadangan).

## Phase 00 — PoC, Policy & Single-Key Gate
- [x] T1 Slugs & rates verified (incl. fal VLM analysis + fal TTS id-ID candidates) → MEMORY §3 (zero TBD)
- [x] T2 Vibe transform batch (Nano Banana) generated (15/15, 0 rejections) — owner scoring pending
- [x] T3 Fusion batch (2-subject merge) generated (std vs Pro on hardest pair; "exactly N people" clause required) — owner scoring pending
- [x] T4 I2V matrix COMPLETE (2026-08-25 rerun after top-up): Kling 6/6, Wan 6/6, Seedance 6/6 + 3 own_family Kling clips — 0 policy rejections; scored: Kling best (8/9 pass)
- [x] T5 TTS: MiniMax id-ID locked as default voice (`Calm_Woman`, blind-test winner) + fal-VLM JSON 3/3 valid; ElevenLabs = optional premium (paid plan), Gemini diff = optional (fal VLM route proven)
- [x] T6 `results.csv` complete (72 rows, real costs, key_label per call, balance-locked rows corrected to $0)
- [x] T7 `REPORT.md` FINAL: **gate = PASS**, single-key = **fal-only LOCKED**; owner scored 49/49 rows (11/11 core cases ≥4) → MEMORY §3 zero TBD, routing locked (Hemat provisional pending Wan-flash spot-check)

## Phase 02 — Desktop Shell (+ BYOK foundation, NO licensing)
- [x] Gradle CMP project builds & runs on Windows (built+launched 2026-08-25; JDK 21, Gradle 9.7.1 wrapper)
- [x] SQLDelight schema (projects/photos/scenes/jobs/outputs/gen_cost/settings) + DatabaseFactory migrations, FK cascade
- [x] ConfigRepository: bundled `app-config.json` + %APPDATA% override (schema = future remote config; locked D-005 values guarded by unit tests)
- [x] **LicenseGate stub (DevFull)** wired via DI + TODO(D-002) markers; zero license UI
- [x] KeyVault (Credential Manager + AES-GCM fallback) — fal keys as ordered list `[{label,key}]` (AD-14); Google/EL single; keys masked, never logged (**Credential Manager selfTest verified live on this machine**)
- [x] API Key Manager UI: fal key list add/remove/reorder + per-key status chip (aktif/cadangan/saldo habis) + "Tes koneksi" per key/provider + onboarding wizard (incl. anti-credit-farming notice §7) — ⚠ green-test with a REAL fal key still owner-pending
- [x] `core/providers`: FalQueueClient (multi-key failover: balance-exhausted → 10-min cooldown → next key, `KeySwitched` event, all-exhausted → provider_balance CTA "Top up / kelola key"; polling uses the job's key), PriceBook, CostTracker (`gen_cost.key_label`, per-key monthly sums in Settings), optional Gemini/ElevenLabs clients — all fixture-tested (26 tests, zero network)
- [x] Home/Settings/About screens; offline states; FFmpeg bundled (pinned n8.1.2, sha256-verified); MSI 135 MB < 300 MB — ⚠ clean-machine install test owner-pending

## Phase 03 — Storyboard Engine
- [x] Input wizard (photos w/ mime/20MB/512px + blur badges, narration + MiniMax voice preview (cached, "±$0.001"), music upload + copyright ack (bundled list = empty asset task), ratio/vibe/duration, restore toggle visible-disabled via flag, consent gate first project) — autosaved per step
- [x] Client-side moderation pre-check + analysis + story plan (template validator + auto-repair in app code; fal VLM default / Gemini when key present; JSON validate+retry) — proven vs real API 2026-08-25
- [x] Storyboard grid: edit prompt (template picker: category+camera+adjectives≤8 sanitized), regen keyframe w/ PriceBook cost chip "±$0.039", reorder (↑/↓; true drag = P1), delete (min 1) — all exercised in demoDriver vs real API
- [x] Cost estimator (PriceBook, USD+Rp, "estimasi" label) + confirm dialog (tier selector, Hemat disabled-provisional, disclaimer) → `StartGenerationRequest` event on the Phase-04 seam — estimator == hand-computed ($1.2990 exact)
- [x] Scene state machine persisted (draft→keyframe_pending→ready→confirmed + failed/retry, crash recovery of stale pending); forbidden-verb test passes ("dancing wildly" → repaired walk_slowly; free-form → rejected)

## Phase 04 — Video Pipeline
- [x] Direct fal submit/poll/download orchestrator (resume-safe; per-scene key failover at submit, GenJob stores `key_label`, polling sticks to the submitting key — AD-14) — force-kill resume proven live (0 resubmissions)
- [x] CostTracker entries per completed job incl. `key_label` (per-key reporting); project cost summary (result screen shows USD+Rp)
- [x] TTS narration + subtitle (.ass) generation (MiniMax full text; proportional timing, ≤42-char lines, ratio-aware safe area; F5.3 duration dialog w/ tempo default)
- [x] FFmpeg assembly (concat+xfade, ducking, loudnorm, subs) both ratios; watermark path implemented but OFF via LicenseGate stub (flag-forced unit test) — 13.9s/1080p in ~30s
- [x] Failure UX per error map (invalid_key / provider_balance = all fal keys exhausted → partial-generation pause with CTA "Top up / kelola key" / content_blocked → edit-motion CTA / auto-retry 1× + manual retry) — implemented + unit-covered; live failure exercised via force-kill path
- [x] E2E: photos → final MP4 (9:16 & 16:9) verified (own-family photos, Standar, narasi id + bundled music); force-kill resume proven, offline-tolerant polling implemented — ⚠ owner-pending: WMP + WhatsApp playback check, in-app screen walkthrough (kenang-desktop/docs/demo-04.md)

## STABILIZATION — "app sempurna" gate (unlocks Phase 01)
> Started 2026-08-25 — owner-driven dogfooding. Docs: kenang-desktop/docs/STAB_RECIPE.md (5 projects + edge cases + perf thresholds), DOGFOOD_LOG.md (owner fills), KNOWN_ISSUES.md (KI-001…KI-012 seeded; gate needs zero OPEN P0/P1).
- [ ] 5 real projects E2E (mixed photo types incl. BW + fusion) with zero blocking bugs
- [ ] 1 week dogfooding: no crashes; all errors land in the translator (no raw exceptions to UI)
- [ ] Performance: analysis ≤30s/5 photos; 30s-1080p assembly ≤60s on a mid laptop; UI never freezes
- [ ] Resume-safety proven: force-kill during analysis, generation, and assembly → clean recovery
- [ ] Clean-VM install test (fresh Windows, no dev tools) passes the full flow
- [ ] UX copy pass (Indonesian, empathetic tone per MEMORY §7) reviewed by owner
- [ ] Bug backlog: zero P0/P1 open; docs/KNOWN_ISSUES.md written
- [ ] Owner sign-off recorded in MEMORY §9 → unfreeze Phase 01

## Phase 01 — License & Config Mini-Backend (FROZEN until Stabilization; on dedicated license website)
- [ ] Confirm site stack & Midtrans reuse; adapt MP01 if non-Laravel (contract unchanged)
- [ ] Migrations: licenses, devices, orders, remote_config, events
- [ ] Ed25519 signing service + keypair docs (`docs/KEYS.md`)
- [ ] activate / heartbeat / deactivate endpoints + seat limits + revoke
- [ ] `GET /config` payload (routing, price_hints, fx, versions, flags) → desktop `CONFIG_URL` switched over
- [ ] Checkout + Midtrans webhook → issue key + email (idempotent)
- [ ] Admin: licenses CRUD/revoke, config editor, metrics-lite
- [ ] Tests green; multipart rejected everywhere; manual walkthrough done

## Phase 05 — Licensing UX, Store & Launch (last)
- [ ] Swap `DevFullLicense` → real LicenseGate at TODO(D-002) seams; Trial watermark + 3-export live
- [ ] Store wiring on the license website + checkout live (Midtrans sandbox → prod)
- [ ] ProGuard rules producing a working obfuscated MSI (full E2E re-run)
- [ ] Error-translator table finalized in i18n
- [ ] Telemetry opt-out + crash report (user-initiated)
- [ ] Legal pages published; consent gate linked
- [ ] Beta checklist executed (clean-VM, offline-grace, revoke, kill-switch, friction run, 20–30 testers)

## ANDROID PORT (owner request 2026-08-29) — see ANDROID/README.md, D-016..D-018
- [x] Standalone Android Studio project at `ANDROID/` (AGP 8.13.2, Gradle 8.14.3, minSdk 29, compileSdk 36)
- [x] Core reused by copy: config, prompts, motion templates, fal client, analysis, storyboard, orchestration
- [x] Platform seams rewritten: AppDirs, image decode/upload, SQLDelight driver, key vault, TTS playback, pickers
- [x] Video assembly on Media3 Transformer (no xfade, no burned-in subtitles — KI-020)
- [x] Output to gallery `Movies/Kenang/<project>/` + per-scene clips, no storage permission
- [x] Phone layouts fixed (Home header, wizard step chips, Settings key rows) — desktop-width Rows pushed controls off-screen
- [x] Verified on emulator: install, run, onboarding, Home, wizard, Settings, DB, key vault
- [ ] Owner end-to-end run on a real device with a fal key (analysis → storyboard → video) — KI-019
- [ ] Decide whether to extract a shared KMP core to end the two-place maintenance (KI-021)
## BLOCKERS
- (none — Phase 00 blockers all closed 2026-08-25: B-1 fal top-up done; B-2 invalid GOOGLE_API_KEY downgraded to OPTIONAL, fal VLM route proven; B-3 paid ElevenLabs plan NOT NEEDED, MiniMax locked as default voice; B-4 owner scored 49/49 rows → PASS)

## OPEN QUESTIONS
- OQ-1 Final product name/brand position
- OQ-2 Final license pricing & SKUs (incl. Update Pass yes/no)
- OQ-3 License website stack (Laravel?) — needed before Phase 01 starts; contract is fixed either way
- OQ-4 Trial policy: watermark + 3 exports (default) vs 7-day full trial
