# KNOWN_ISSUES — Kenang desktop (Stabilization backlog)

> One row per issue, stable id `KI-###`. Status: OPEN / FIXED(commit) / WONTFIX(reason) / BY-DESIGN.
> Gate rule (PROGRESS.md): zero P0/P1 OPEN before Phase 01 unfreezes.

| Id | Sev | Area | Issue | Status |
|---|---|---|---|---|
| KI-001 | P2 | Storyboard | Scene reorder is ↑/↓ buttons only; true drag-and-drop deferred (demo-03 leftover) | OPEN |
| KI-002 | P2 | Wizard | ElevenLabs premium voice preview not wired (MiniMax preview works); EL path itself optional (paid plan required for id voices) | OPEN |
| KI-003 | P2 | Generation | Restart mid-**assembly** re-runs assembly from 0% (clips/narration cached — no API cost, just wall time) | OPEN |
| KI-004 | P2 | Result | "Buat versi rasio lain" is a disabled stub (specced P2, MASTER_PROMPT_04 §4.4) | BY-DESIGN |
| KI-005 | P2 | Audio | F5.3 "perpanjang durasi adegan" disabled after clips exist (extension must happen in storyboard before generating) | BY-DESIGN |
| KI-006 | P2 | Providers | `TtsPreviewService` hardcodes the $0.10/1k rate instead of using PriceBook (full-narration `TtsService` does it right) | OPEN |
| KI-007 | P2 | Providers | Variant slugs (e.g. Wan `/flash`) have no own price hint — auto-retry after a resumed poll estimates $0 for them; only reachable if Hemat is re-enabled (disabled at launch, D-009) | OPEN |
| KI-008 | P2 | Logging | Log files show mojibake for em-dashes (`â€”`) — writer/console codepage mismatch, cosmetic | OPEN |
| KI-009 | P1 | Verification | WMP + WhatsApp playback of Phase-04 outputs not yet owner-verified (encoding chosen for compatibility: H.264 High, yuv420p, AAC 48kHz, faststart) | OPEN (owner check) |
| KI-010 | P1 | Verification | Generation/Result screens exercised only via demoDriver04 services; in-app walkthrough (confirm → generation screen → result screen) pending owner eyes | OPEN (owner check) |
| KI-011 | P2 | Analysis | "Analysis ≤30s / 5 photos" target never re-measured on a normal uplink (demo-03 leftover; slow-uplink runs exceeded it) | OPEN (measure during dogfood) |
| KI-012 | P2 | Home | Deleting a project is possible while its generation may still be polling in a session; fal-side jobs are not cancelled (user still billed for in-flight scenes) — needs a guard or honest confirm copy | OPEN |
| KI-013 | P1 | Wizard | Photo picker showed no files on owner's machine ("No items match your search") — AWT FileDialog filename-pattern filtering broke on Windows. Replaced with JFileChooser + FileNameExtensionFilter (photos & music), remembers last folder | FIXED (dogfood 2026-08-26) |
| KI-014 | P1 | Analysis | Repeated generic "Penyedia AI bermasalah": adding a Google key routed analysis to DIRECT Gemini, and Google 404s `gemini-2.5-flash` for new accounts; no fallback existed, so analysis died while fal was healthy. Fixed: config `analysis.gemini_model` (→ gemini-3.6-flash) + automatic fal-VLM fallback on any Gemini error + provider-level HTTP logging + technical-detail line & "Buka folder log" on the error screen + `keyDoctor`/`analysisDoctor` gradle diagnostics | FIXED (dogfood 2026-08-26, verified: stuck project now analyzes, 6 scenes) |
| KI-017 | **P0** | All screens | **UI frozen on first snapshot while background work completed invisibly** — owner waited ~1h for keyframes that actually finished in 70s (files on disk, cards empty; also this morning's "Antre" generation screen). Root cause: the DataSource JDBC driver's listener hooks never deliver, so every SQLDelight `asFlow()` stopped emitting after the SQLITE_BUSY driver switch. Fixed with a NotifyingDriver wrapper implementing the listener registry; regression-locked by DbFlowNotificationTest | FIXED (dogfood 2026-08-26) |
| KI-016 | P2 | Settings | "Tes koneksi" also reads the fal credit balance (`GET api.fal.ai/v1/account/billing?expand=credits`), but fal serves that endpoint **only to Admin-scoped keys** — all six of the owner's generation keys answer **HTTP 403**, so the row shows "saldo perlu key admin". Feature is implemented and harmless (never fails a working key); it only lights up if the user stores an Admin key. Revisit if fal ever exposes balance to plain keys | OPEN (provider limitation) |
| KI-015 | P2 | Analysis | Direct-Gemini path is slow/flaky on the owner's account even on gemini-3.6-flash (60s timeouts observed; analysis took 321s vs ≤30s target). Workaround: remove the Gemini key in Pengaturan — the fal route is fast and proven; fallback now protects either way. Consider a config flag to prefer fal even when a Gemini key exists | OPEN |
