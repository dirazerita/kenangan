"""T4: I2V matrix — 6 keyframes x {Kling 3.0 Std, Wan 2.6, Seedance 2.0 Mini}, 5s each.
Motion prompts strictly from MEMORY §7 templates (smile, hug, slight_head_turn,
look_at_camera + slow push-in / static)."""
import requests
import fal_client
from pathlib import Path
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

# (path, ratio_label, motion_prompt)
KEYFRAMES = [
    ("out/vibe/foto01_bw_tua__taman.jpg", "9:16",
     "The woman turns her head slightly and smiles warmly; gentle slow push-in."),
    ("out/vibe/foto05_keluarga_grup__pantai.jpg", "16:9",
     "The family looks at the camera and smiles softly; gentle slow push-in."),
    ("out/vibe/foto07_lansia_solo__ruang_tamu.jpg", "9:16",
     "The elderly woman smiles warmly and turns her head slightly; camera static."),
    ("out/fusion/A_lansia+dewasa__pro.jpg", "9:16",
     "The two men hug warmly and smile; gentle slow push-in."),
    ("ASSETS/foto05_keluarga_grup.jpg", "16:9",
     "The family looks at the camera and smiles softly; gentle slow push-in."),
    ("ASSETS/foto07_lansia_solo.jpg", "9:16",
     "The elderly woman turns her head slightly and smiles warmly; camera static."),
]

MODELS = {
    "kling3-std": dict(slug="fal-ai/kling-video/v3/standard/image-to-video",
                       price_5s=0.42),   # $0.084/s audio off
    "wan26": dict(slug="wan/v2.6/image-to-video", price_5s=0.50),  # $0.10/s 720p
    "seedance2-mini": dict(slug="bytedance/seedance-2.0/mini/reference-to-video",
                           price_5s=0.7735),  # $0.1547/s output 720p
}


def build_args(model_key, img_url, motion, ratio):
    if model_key == "kling3-std":
        return {"start_image_url": img_url, "prompt": motion, "duration": "5",
                "generate_audio": False}
    if model_key == "wan26":
        return {"image_url": img_url, "prompt": motion, "duration": "5",
                "resolution": "720p"}
    return {"image_urls": [img_url], "prompt": f"@Image1 {motion}",
            "duration": "5", "resolution": "720p", "aspect_ratio": ratio,
            "generate_audio": False}


uploaded = {}
def upload(rel):
    if rel not in uploaded:
        uploaded[rel] = fal_client.upload_file(str(POC / rel))
    return uploaded[rel]


for model_key, cfg in MODELS.items():
    outdir = POC / "out" / "i2v" / model_key
    outdir.mkdir(parents=True, exist_ok=True)
    for rel, ratio, motion in KEYFRAMES:
        stem = Path(rel).stem
        out_file = outdir / f"{stem}__{ratio.replace(':', 'x')}.mp4"
        if out_file.exists():
            print("skip", model_key, out_file.name)
            continue
        budget_guard(cfg["price_5s"])
        try:
            with Timer() as t:
                res, key_label = fal_subscribe(cfg["slug"],
                                               build_args(model_key, upload(rel), motion, ratio))
            url = res["video"]["url"]
            out_file.write_bytes(requests.get(url, timeout=300).content)
            dur = res["video"].get("duration") or 5
            log("T4", "fal", cfg["slug"], stem, ratio, dur, t.dt, cfg["price_5s"],
                "ok", "", f"{model_key} saved {out_file.name}", key_label=key_label)
        except Exception as e:
            err = str(e)[:300]
            with open(POC / "errors.log", "a", encoding="utf-8") as f:
                f.write(f"T4 {model_key} {stem}: {err}\n")
            billed = 0.0 if any(k in err for k in ("404", "422", "ValidationError", "not found")) else cfg["price_5s"]
            log("T4", "fal", cfg["slug"], stem, ratio, 5, getattr(t, "dt", 0.0),
                billed, "error", err[:60], f"{model_key} see errors.log",
                key_label=kenang_poc.LAST_FAL_KEY)

print("T4 matrix complete.")
