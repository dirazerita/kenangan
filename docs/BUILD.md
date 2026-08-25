# Building Kenang Desktop

## Prerequisites

- **JDK 21** (Temurin recommended). `JAVA_HOME` must point to it.
- **Gradle 9.7.1** (or use the checked-in wrapper: `gradlew.bat`).
- Windows 10/11 x64.
- For `packageMsi`: **WiX Toolset 3.x** on PATH (jpackage requirement).
- Internet on first build (dependencies + pinned FFmpeg download).

## Common commands

```powershell
# Compile + unit tests (all tests are offline; fixtures only)
.\gradlew.bat build

# Quick dev build without bundling the 160 MB FFmpeg zip
.\gradlew.bat build -PskipFfmpeg=true

# Run the app from sources
.\gradlew.bat :app:run

# Build the Windows MSI installer (needs WiX; output: app/build/compose/binaries/main/msi)
.\gradlew.bat :app:packageMsi
```

## Version

The app version lives in `gradle.properties` (`appVersion`) — the single
source of truth for packaging. Override per-build: `-PappVersion=0.2.0`.

## FFmpeg bundling

`:app:downloadFfmpeg` downloads a **pinned** BtbN build
(`ffmpeg-n8.1.2-44-g7c533d0f86-win64-gpl-8.1.zip`, tag
`autobuild-2026-08-24-13-10`), verifies its sha256
(`5efb8182e0770c7af639ce46c229e5a5ea585884f17d2e94983051d8da90bdab`), and
stages `ffmpeg.exe` into app resources. On first launch the app copies it to
`%APPDATA%/Kenang/tools/ffmpeg/` and smoke-checks `ffmpeg -version`.

- Re-use an already-downloaded copy: `-PffmpegLocalZip=C:\path\to\the-same.zip`
  (still sha256-verified).
- Skip entirely (dev only): `-PskipFfmpeg=true` — assembly features report
  "unavailable" at runtime.
- FFmpeg is GPL: attribution shown in the About screen; keep it when updating
  the pinned build.

## App data locations (runtime)

`%APPDATA%/Kenang/` → `db/`, `projects/<id>/{photos,keyframes,clips,output}/`,
`logs/`, `tools/ffmpeg/`, `config/app-config.json` (user override of the
bundled config; same schema as the future remote config — AD-10).

## Notes

- Tests never hit the network (Ktor MockEngine + fixtures in
  `core/providers/src/test/resources/fixtures/`).
- CI building the MSI artifact is P1 (GitHub Actions) — not set up yet.
