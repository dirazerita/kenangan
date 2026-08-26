"""T7: generate REVIEW_SHEET.md — one row per generated output for owner scoring."""
from pathlib import Path
from kenang_poc import POC

rows = []
for sub, kind in [("out/vibe", "vibe"), ("out/fusion", "fusion")]:
    for f in sorted((POC / sub).glob("*.jpg")):
        rows.append((kind, f"{sub}/{f.name}"))
for d in sorted((POC / "out" / "i2v").iterdir()):
    if d.is_dir():
        for f in sorted(d.glob("*.mp4")):
            rows.append((f"i2v:{d.name}", f"out/i2v/{d.name}/{f.name}"))
for f in sorted((POC / "out" / "tts").glob("*.mp3")):
    rows.append(("tts", f"out/tts/{f.name}"))

lines = [
    "# REVIEW_SHEET — Phase 00 human review",
    "",
    "Score each output 1–5 (5 = excellent). For TTS, score only emotional_quality",
    "(naturalness of Indonesian, warmth) and note the winner of the blind compare.",
    "Verdict: pass / borderline / fail.",
    "",
    "| # | kind | file | face_consistency | artifacts | emotional_quality | verdict | notes |",
    "|---|------|------|------------------|-----------|-------------------|---------|-------|",
]
for i, (kind, rel) in enumerate(rows, 1):
    lines.append(f"| {i} | {kind} | {rel} |  |  |  |  |  |")

lines += [
    "",
    "## Summary (owner fills after scoring)",
    "- Face-consistency pass rate (target ≥ 8/10 cases): __ / __",
    "- Best I2V model overall: __",
    "- MiniMax Indonesian TTS acceptable as default voice? yes / no",
    "- Notes:",
]
(POC / "REVIEW_SHEET.md").write_text("\n".join(lines), encoding="utf-8")
print(f"REVIEW_SHEET.md written with {len(rows)} rows.")
