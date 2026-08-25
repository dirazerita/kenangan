# Stabilization test recipe (one page)

## The 5 projects this week (vary ratio + vibe across them)

| # | Project | Photos | Settings | What it stresses |
|---|---|---|---|---|
| 1 | **BW jadul** | 2–3 black-white only, include one blurry (à la foto01/02/13) | 9:16 · taman · narasi pendek | BW face consistency, blur badges, era handling |
| 2 | **Fusion-heavy** | 2 fusion-worthy pairs + 1 solo (à la foto09+foto14+foto07) | 9:16 · golden_hour | Fusion keyframes, "exactly N people" guardrail, ≥4 scenes |
| 3 | **Rombongan besar** | biggest group photo you have (~20 people) + 1 solo | 16:9 · ruang_keluarga | Group-photo weakness seen in Phase 00; crop at 16:9 |
| 4 | **Hewan kesayangan** | pet photo + owner photo (à la foto12 + solo) | 9:16 · asli | `pet_animal` motion template, non-human subject |
| 5 | **Narasi panjang** | any 2 photos, scene duration 5s | 16:9 · **narration at the 500-char max** | F5.3 duration dialog — try **tempo** once and **persingkat teks** once; subtitle timing over many sentences |

Log each in DOGFOOD_LOG.md. Gate needs all 5 with **zero blocking bugs**.

## Edge cases to poke (once each, any project)

- **Force-kill mid-generation**: close the app (or kill via Task Manager) while scenes are "Diproses" → reopen → Home must route back into generation and finish **without double-billing** (check Settings per-key spend before/after; scene count of gen_cost rows must not grow).
- **Network kill mid-generation**: disable Wi-Fi during "Diproses", wait ~1 min, re-enable → generation must complete by itself, no error dialog needed.
- **Cancel path**: press "Kembali" on the generation screen mid-run, browse Home, come back in — state must be consistent.
- **Disk almost full**: fill a USB/partition (or temp-fill C:) and assemble → expect the Indonesian "Perakitan video terkendala" card, no crash, no stray `.tmp.mp4` left behind.
- **Huge photo**: one >20 MB file (wizard must refuse politely) and one huge-resolution-but-<20 MB (must upload downscaled, analysis unaffected).
- **Empty narration**: project with no narration + bundled music → video with music only, no subtitle option offered; also try **Tanpa musik + no narration** → silent video still plays.
- **Key failover**: move "Cadangan" to first position with a near-empty account if you have one, or temporarily paste a broken key → invalid-key CTA must deep-link to Pengaturan Key; with all-exhausted you must get the pause card with "Top up / kelola key".
- **Nama proyek aneh**: name a project `Kenangan: Ibu & Bapak? (1998)` → output file must save/open fine.
- **Delete during generation** (KI-012): try deleting a generating project from Home — note what happens; copy should be honest that provider may still bill in-flight scenes.

## "Too slow" thresholds (log a P1 if exceeded)

| Step | Target | Notes |
|---|---|---|
| Analysis, 5 photos | **≤ 30 s** | normal uplink; KI-011 wants a real measurement |
| Keyframe per scene | ~15–40 s | provider-side; note if > 2 min |
| I2V per 5s scene | 1–3 min typical | provider queue; note if > 10 min |
| Assembly, 30s @1080p | **≤ 60 s** | measured ~30 s for 14 s video on this laptop |
| App start → Home | < 5 s | |
| UI during everything | **never freezes** | scrolling stays smooth while generating/assembling |

## What to write down

Per session in DOGFOOD_LOG.md: date, project, what worked, bugs with **P0/P1/P2**
(P0 crash/data-loss/double-bill · P1 broken/no-workaround · P2 friction), and
any copy that felt cold or confusing (UX copy pass is its own gate box).
Paste bug reports to the agent as you find them — each gets a KNOWN_ISSUES id,
a fix, and a commit.
