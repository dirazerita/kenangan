"""Owner's own-family batch: per photo one Nano Banana std taman keyframe
+ one 5s Kling 3.0 Standard clip (generate_audio:false)."""
import requests
import fal_client
from pathlib import Path
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

NB, NB_PRICE = "fal-ai/nano-banana/edit", 0.039
KLING, KLING_PRICE = "fal-ai/kling-video/v3/standard/image-to-video", 0.42

# (file, ratio, motion prompt from MEMORY §7 templates)
PHOTOS = [
    ("kakek1.jpg", "9:16",
     "The elderly couple smiles warmly and looks at the camera; gentle slow push-in."),
    ("kakek2.jpg", "16:9",
     "They look at the camera and smile softly; gentle slow push-in."),
    ("keluarga.jpg", "9:16",
     "The family looks at the camera and smiles softly; gentle slow push-in."),
]

VIBE_OUT = POC / "out" / "vibe"
I2V_OUT = POC / "out" / "i2v" / "kling3-std"
VIBE_OUT.mkdir(parents=True, exist_ok=True)
I2V_OUT.mkdir(parents=True, exist_ok=True)

for name, ratio, motion in PHOTOS:
    stem = Path(name).stem
    kf_file = VIBE_OUT / f"{stem}__taman.jpg"
    ratio_word = "9:16 portrait" if ratio == "9:16" else "16:9 landscape"

    # 1) keyframe: vibe taman
    kf_url = None
    if not kf_file.exists():
        budget_guard(NB_PRICE)
        prompt = (f"Place the exact same people in a lush tropical garden with soft "
                  f"greenery and blooming flowers setting. Preserve every person's face, "
                  f"age, body, and clothing exactly; keep the same number of people, no "
                  f"additional people. Photorealistic, warm natural light, {ratio_word}.")
        try:
            with Timer() as t:
                res, key_label = fal_subscribe(NB, {
                    "prompt": prompt, "image_urls": [fal_client.upload_file(str(POC / "ASSETS" / name))],
                    "num_images": 1, "output_format": "jpeg"})
            kf_url = res["images"][0]["url"]
            kf_file.write_bytes(requests.get(kf_url, timeout=120).content)
            log("T2+", "fal", NB, f"{name}|taman", ratio, "", t.dt, NB_PRICE,
                "ok", "", f"own_family keyframe {kf_file.name}", key_label=key_label)
        except Exception as e:
            log("T2+", "fal", NB, f"{name}|taman", ratio, "", 0.0, 0.0, "error",
                str(e)[:60], "own_family", key_label=kenang_poc.LAST_FAL_KEY)
            continue

    # 2) 5s Kling std clip from the keyframe
    clip_file = I2V_OUT / f"{stem}__taman__{ratio.replace(':', 'x')}.mp4"
    if clip_file.exists():
        continue
    budget_guard(KLING_PRICE)
    if kf_url is None:
        kf_url = fal_client.upload_file(str(kf_file))
    try:
        with Timer() as t:
            res, key_label = fal_subscribe(KLING, {
                "start_image_url": kf_url, "prompt": motion,
                "duration": "5", "generate_audio": False})
        clip_file.write_bytes(requests.get(res["video"]["url"], timeout=300).content)
        log("T4+", "fal", KLING, f"{stem}__taman", ratio, 5, t.dt, KLING_PRICE,
            "ok", "", f"own_family clip {clip_file.name}", key_label=key_label)
    except Exception as e:
        log("T4+", "fal", KLING, f"{stem}__taman", ratio, 5, getattr(t, "dt", 0.0),
            0.0, "error", str(e)[:60], "own_family", key_label=kenang_poc.LAST_FAL_KEY)

print("own_family batch complete.")
