"""T2: vibe transform batch — 5 photos x 3 vibes via Nano Banana edit (std)."""
import sys, requests
import fal_client
from pathlib import Path
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

MODEL = "fal-ai/nano-banana/edit"
PRICE = 0.039  # USD per image (verified 2026-08-25)

PHOTOS = {
    "foto01_bw_tua.jpg": "bw_old",
    "foto03_warna_pudar.jpg": "faded_color",
    "foto05_keluarga_grup.jpg": "group",
    "foto07_lansia_solo.jpg": "elderly_solo",
    "foto10_anak.jpg": "child",
}
VIBES = {
    "taman": ("a lush tropical garden with soft greenery and blooming flowers", "9:16 portrait"),
    "pantai": ("a calm tropical beach at golden hour", "16:9 landscape"),
    "ruang_tamu": ("a cozy Indonesian living room with warm evening lamplight", "9:16 portrait"),
}

OUT = POC / "out" / "vibe"
OUT.mkdir(parents=True, exist_ok=True)

uploaded = {}
for name in PHOTOS:
    p = POC / "ASSETS" / name
    uploaded[name] = fal_client.upload_file(str(p))
    print(f"uploaded {name}")

only = sys.argv[1:] if len(sys.argv) > 1 else None
for name, cat in PHOTOS.items():
    for vibe, (desc, ratio) in VIBES.items():
        out_file = OUT / f"{Path(name).stem}__{vibe}.jpg"
        if out_file.exists():
            print(f"skip existing {out_file.name}")
            continue
        if only and f"{Path(name).stem}:{vibe}" not in only:
            continue
        budget_guard(PRICE)
        prompt = (f"Place the exact same people in a {desc} setting. "
                  f"Preserve faces, age, body, and clothing exactly. "
                  f"Photorealistic, warm natural light, {ratio}.")
        if cat == "pet":
            prompt = prompt.replace("people", "pet")
        try:
            with Timer() as t:
                res, key_label = fal_subscribe(MODEL, {
                    "prompt": prompt,
                    "image_urls": [uploaded[name]],
                    "num_images": 1,
                    "output_format": "jpeg",
                })
            img_url = res["images"][0]["url"]
            out_file.write_bytes(requests.get(img_url, timeout=120).content)
            log("T2", "fal", MODEL, f"{name}|{vibe}", ratio.split()[0], "", t.dt,
                PRICE, "ok", "", f"saved {out_file.name}", key_label=key_label)
        except Exception as e:
            err = str(e)[:180]
            with open(POC / "errors.log", "a", encoding="utf-8") as f:
                f.write(f"T2 {name} {vibe}: {e}\n")
            # policy rejections still bill on some providers; assume billed unless clearly a 4xx slug/validation error
            billed = 0.0 if ("404" in err or "not found" in err.lower() or "422" in err) else PRICE
            log("T2", "fal", MODEL, f"{name}|{vibe}", ratio.split()[0], "",
                getattr(t, "dt", 0.0), billed, "error", err[:60], "see errors.log",
                key_label=kenang_poc.LAST_FAL_KEY)

print("T2 batch complete.")
