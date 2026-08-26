"""Hemat spot-check: ONE 5s Wan 2.6 flash clip from the T4 solo-elderly keyframe."""
import requests
import fal_client
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

SLUG, PRICE = "wan/v2.6/image-to-video/flash", 0.25  # $0.05/s x 5s @720p (verified 2026-08-25)
assert PRICE <= 1.50, "abort: implied price above threshold"

kf = POC / "out" / "vibe" / "foto07_lansia_solo__ruang_tamu.jpg"
out = POC / "out" / "i2v" / "wan26-flash" / "foto07_lansia_solo__ruang_tamu__9x16.mp4"
out.parent.mkdir(parents=True, exist_ok=True)

if out.exists():
    print("already exists:", out.name)
else:
    budget_guard(PRICE)
    motion = "The elderly woman smiles warmly and turns her head slightly; camera static."
    try:
        with Timer() as t:
            res, key_label = fal_subscribe(SLUG, {
                "prompt": motion,
                "image_url": fal_client.upload_file(str(kf)),
                "resolution": "720p",
                "duration": "5"})
        out.write_bytes(requests.get(res["video"]["url"], timeout=300).content)
        log("T4-flash", "fal", SLUG, "foto07_lansia_solo__ruang_tamu", "9:16", 5, t.dt,
            PRICE, "ok", "", f"Hemat spot-check saved {out.name}", key_label=key_label)
    except Exception as e:
        log("T4-flash", "fal", SLUG, "foto07_lansia_solo__ruang_tamu", "9:16", 5,
            getattr(t, "dt", 0.0), 0.0, "error", str(e)[:60], "Hemat spot-check",
            key_label=kenang_poc.LAST_FAL_KEY)
        raise

print("spot-check complete.")
