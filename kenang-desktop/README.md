# Kenang Desktop

Windows-first Compose Multiplatform (JVM) app that turns old photos into short
cinematic memory videos. BYOK: users bring their own AI provider keys (fal.ai
required; Google Gemini / ElevenLabs optional) and pay providers directly —
user media never touches any Kenang server.

- Build instructions: [docs/BUILD.md](docs/BUILD.md)
- Shell walkthrough: [docs/shell-walkthrough.md](docs/shell-walkthrough.md)
- Product/pipeline docs live in the parent workspace (`agents/`, PRD v1.1).

## Modules

| Module | Contents |
|---|---|
| `app` | Compose UI, navigation, DI wiring, FFmpeg bundling task, MSI packaging |
| `core/common` | AppError/AppResult, Indonesian error translator + strings, LicenseGate stub (D-002), dispatchers, logging |
| `core/data` | ConfigRepository (bundled `app-config.json` → future CONFIG_URL), AppDirs, repositories, FfmpegLocator |
| `core/db` | SQLDelight schema + DatabaseFactory (resume-safe SQLite) |
| `core/providers` | KeyVault (Credential Manager + AES-GCM fallback), FalQueueClient (multi-key failover, AD-14), PriceBook, CostTracker, optional Gemini/ElevenLabs clients |

## Non-negotiables (from agents/MEMORY.md)

- Licensing is deferred: `LicenseGate` stub is the ONLY seam (TODO(D-002) markers).
- Never hardcode prices/model slugs — config only.
- User API keys: never logged, never synced, masked in UI.
- Every cost figure is an estimate ("estimasi").
