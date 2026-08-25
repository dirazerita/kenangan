# Phase 03 Scripted Demo — Storyboard Engine

Executed 2026-08-25 against **real fal APIs** (BYOK keys from the owner's
vault; total demo spend **$0.2036**, tracked per key by CostTracker).

Two ways to run it:

- **Scripted (repeatable):** `gradlew :app:demoDriver "-PdemoPhotos=<POC assets dir>"`
  — drives the exact same services the UI uses and validates every DoD point.
- **By hand:** `gradlew :app:run` → Proyek Baru → follow the wizard → storyboard.

## Recorded run (demoDriver, POC/ASSETS photos)

**Input:** 4 real photos — `foto07_lansia_solo`, `foto05_keluarga_grup`, and the
fusion-worthy pair `foto09_dewasa_fusion` + `foto14_fusion_b`. Vibe `taman`,
ratio 9:16, 5s scenes, Indonesian narration.

| DoD point | Result |
|---|---|
| Wizard → analysis → storyboard | ✔ 4 scenes, status flow draft→keyframe_pending→keyframe_ready persisted |
| Analysis pipeline | ✔ upload (downscaled ≤2048px) → moderation pre-check (0 blocked) → PhotoAnalysis 4/4 valid JSON → story plan; 73s total on a slow uplink (LLM time ~35s) |
| All keyframes render | ✔ 4/4 Nano Banana keyframes generated + downloaded locally |
| Edit 1 prompt | ✔ template picker output persisted (`look_at_camera` + `static`) |
| Regen 1 keyframe | ✔ regen ran, `regen_count` incremented (feeds estimator) |
| Reorder | ✔ last scene moved to front, `order_index` persisted |
| Delete 1 scene | ✔ deleted (min-1 guard verified separately by unit test) |
| Estimator = hand-computed | ✔ **$1.2990** = 15s×$0.084 + 1×$0.039 (exact match, `match=true`); Rp via config fx 16500 |
| Confirm event | ✔ `StartGenerationRequest(projectId=…, tier=standar)` received on the Phase-04 seam |
| §6 Scene contract | ✔ 3/3 confirmed scenes conform (all fields, template-valid motion prompts) |
| Forbidden verb rejected/repaired | ✔ unit-tested: `"dancing wildly"` → repaired to `walk_slowly`, `crash_zoom` → `slow_push_in`, adjective `spinning` dropped (`StoryPlanParsingTest`, `MotionTemplatesTest`) |

Screenshots: `img/wizard.png`, `img/storyboard.png`.

## fal integration notes (learned in this session, now encoded in the client)

- **Upload** uses the v3 CDN flow: token from
  `POST rest.fal.ai/storage/auth/token?storage_type=fal-cdn-v3`, then
  `POST v3.fal.media/files/upload` (the old `storage/upload/initiate?storage_type=gcs`
  returns 400 now). Photos are downscaled to ≤2048px JPEG before upload
  (originals stay local); 3 attempts against transient EOFs on slow links.
- **Queue URLs:** sub-paths beyond `{owner}/{alias}` (e.g. `openrouter/router/vision`)
  belong to the SUBMIT URL only; status/result live at
  `{owner}/{alias}/requests/{id}` — the client stores the URLs returned by
  submit and derives them for resumed jobs.

## Known gaps / next-phase notes

- Analysis wall-clock 73s for 4 photos on this connection (target ≤30s/5 photos
  is a Stabilization gate; most of the delta is photo upload, already mitigated
  by downscaling — re-measure on a normal uplink).
- Story plan chose 4 single scenes this run (no fusion) — fusion is
  model-discretionary ("at most one, only when subjects belong together");
  the exactly-N-people fusion clause is unit-tested either way.
- Bundled royalty-free music list ships empty (asset task); upload path with
  copyright acknowledgement works.
- Drag-to-reorder implemented as ↑/↓ buttons (P1 polish: true drag).
- ElevenLabs voice PREVIEW not wired (needs paid EL TTS); EL voices appear
  once a key exists, preview lands with Phase 04 TTS work.
