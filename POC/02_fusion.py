"""T3: fusion batch — 3 photo pairs merged into one keyframe; std vs Pro on the hardest pair."""
import requests
import fal_client
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

STD, STD_PRICE = "fal-ai/nano-banana/edit", 0.039
PRO, PRO_PRICE = "fal-ai/nano-banana-pro/edit", 0.15

PAIRS = [
    # (id, photoA, photoB, setting, ratio)
    ("A_lansia+dewasa", "foto08_lansia_solo2.jpg", "foto09_dewasa_fusion.jpg",
     "a cozy Indonesian living room with warm evening lamplight", "9:16 portrait"),
    ("B_lansia+fusi", "foto07_lansia_solo.jpg", "foto14_fusion_b.jpg",
     "a lush tropical garden with soft greenery", "16:9 landscape"),
    ("C_anak+fusi", "foto10_anak.jpg", "foto14_fusion_b.jpg",
     "a cozy Indonesian living room with warm morning light", "9:16 portrait"),
]

OUT = POC / "out" / "fusion"
OUT.mkdir(parents=True, exist_ok=True)

up = {}
def upload(name):
    if name not in up:
        up[name] = fal_client.upload_file(str(POC / "ASSETS" / name))
    return up[name]

def run(model, price, pair_id, a, b, setting, ratio, tag):
    out_file = OUT / f"{pair_id}__{tag}.jpg"
    if out_file.exists():
        print("skip", out_file.name); return
    budget_guard(price)
    prompt = (f"Combine the person from the first image and the person from the second image "
              f"into one natural photo together in {setting}. They stand close together, warm "
              f"and affectionate. Preserve each person's face, age, body, and clothing exactly "
              f"as in their source photo. Photorealistic, warm natural light, {ratio}.")
    try:
        with Timer() as t:
            res, key_label = fal_subscribe(model, {
                "prompt": prompt,
                "image_urls": [upload(a), upload(b)],
                "num_images": 1,
                "output_format": "jpeg",
            })
        img_url = res["images"][0]["url"]
        out_file.write_bytes(requests.get(img_url, timeout=120).content)
        log("T3", "fal", model, f"{a}+{b}", ratio.split()[0], "", t.dt, price,
            "ok", "", f"saved {out_file.name}", key_label=key_label)
    except Exception as e:
        err = str(e)[:180]
        with open(POC / "errors.log", "a", encoding="utf-8") as f:
            f.write(f"T3 {pair_id} {tag}: {e}\n")
        billed = 0.0 if ("404" in err or "422" in err) else price
        log("T3", "fal", model, f"{a}+{b}", ratio.split()[0], "",
            getattr(t, "dt", 0.0), billed, "error", err[:60], "see errors.log",
            key_label=kenang_poc.LAST_FAL_KEY)

for pair_id, a, b, setting, ratio in PAIRS:
    run(STD, STD_PRICE, pair_id, a, b, setting, ratio, "std")

# hardest pair = A (elderly BW-era portrait + modern adult): compare Pro
run(PRO, PRO_PRICE, "A_lansia+dewasa", "foto08_lansia_solo2.jpg",
    "foto09_dewasa_fusion.jpg",
    "a cozy Indonesian living room with warm evening lamplight", "9:16 portrait", "pro")

print("T3 batch complete.")
