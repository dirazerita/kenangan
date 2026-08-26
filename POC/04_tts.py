"""T5: TTS comparison (3 ElevenLabs Indonesian voices vs fal MiniMax Speech-02 HD)
+ PhotoAnalysis JSON: fal-hosted VLM (openrouter/router/vision) vs Gemini direct."""
import json, os, requests
import fal_client
import kenang_poc
from kenang_poc import POC, log, budget_guard, Timer, fal_subscribe

EL_KEY = os.environ["ELEVENLABS_API_KEY"]
EL_MODEL = "eleven_multilingual_v2"
EL_EST_PER_1K = 0.15  # USD est; EL bills subscription characters (user_read scope blocked)

NARRATIONS = {
    "n1_rumah": ("Waktu boleh berlalu, tapi senyummu tetap tinggal di hati kami. "
                 "Di rumah ini, tawamu masih terdengar hangat, menemani setiap doa "
                 "yang kami kirimkan. Terima kasih untuk kasih sayang yang tak pernah "
                 "pudar. Kenangan ini akan kami jaga selamanya."),
    "n2_taman": ("Di taman kecil itu, kita pernah tertawa bersama. Angin sore membawa "
                 "cerita-cerita lama, tentang pelukan hangat dan doa yang tulus. Meski "
                 "kini kau jauh di sana, cintamu tetap tumbuh di hati kami, seperti "
                 "bunga yang tak pernah layu."),
}

EL_VOICES = {  # shortlisted Indonesian voices (T1)
    "meraki_F": "OKanSStS6li6xyU1WdXa",   # somber, calm, soft — memorial fit
    "maya_F": "U3dExJoUNcmTY5H6GMuG",     # warm, calm
    "bram_M": "X8n8hOy3e8VLQnHTUcc5",     # warm storyteller (in account voices)
}
EL_FALLBACKS = {"meraki_F": ("mizan_M", "ACRfKVNOAnzVitkYerdl"),
                "maya_F": ("bian_M", "1k39YpzqXZn52BgyLyGO")}

OUT_TTS = POC / "out" / "tts"
OUT_AN = POC / "out" / "analysis"
OUT_TTS.mkdir(parents=True, exist_ok=True)
OUT_AN.mkdir(parents=True, exist_ok=True)


def el_tts(tag, voice_name, voice_id, text):
    out = OUT_TTS / f"{tag}__el_{voice_name}.mp3"
    if out.exists():
        return True
    cost = len(text) / 1000 * EL_EST_PER_1K
    budget_guard(cost)
    with Timer() as t:
        r = requests.post(
            f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}",
            headers={"xi-api-key": EL_KEY},
            json={"text": text, "model_id": EL_MODEL,
                  "voice_settings": {"stability": 0.55, "similarity_boost": 0.75,
                                     "style": 0.2}},
            timeout=120)
    if r.ok:
        out.write_bytes(r.content)
        log("T5", "elevenlabs", EL_MODEL, f"{tag}|{voice_name}", "", "", t.dt,
            cost, "ok", "", f"{len(text)} chars, est cost (subscription quota)",
            key_label="-")
        return True
    err = r.text[:120]
    log("T5", "elevenlabs", EL_MODEL, f"{tag}|{voice_name}", "", "", t.dt,
        0.0, "error", str(r.status_code), err, key_label="-")
    return False


for tag, text in NARRATIONS.items():
    for vname, vid in EL_VOICES.items():
        ok = el_tts(tag, vname, vid, text)
        if not ok and vname in EL_FALLBACKS:
            fb_name, fb_id = EL_FALLBACKS[vname]
            el_tts(tag, fb_name, fb_id, text)

# fal MiniMax Speech-02 HD — Indonesian
MM = "fal-ai/minimax/speech-02-hd"
for tag, text in NARRATIONS.items():
    for vname, vs in [("calm_woman", {"voice_id": "Calm_Woman", "speed": 0.95}),
                      ("wise_woman", {"voice_id": "Wise_Woman", "speed": 0.95})]:
        out = OUT_TTS / f"{tag}__minimax_{vname}.mp3"
        if out.exists():
            continue
        cost = len(text) / 1000 * 0.10
        budget_guard(cost)
        args = {"text": text, "voice_setting": vs, "output_format": "url",
                "language_boost": "Indonesian"}
        try:
            with Timer() as t:
                res, key_label = fal_subscribe(MM, args)
        except Exception as e:
            if "language_boost" in str(e) or "422" in str(e):
                args.pop("language_boost")
                with Timer() as t:
                    res, key_label = fal_subscribe(MM, args)
            else:
                log("T5", "fal", MM, f"{tag}|{vname}", "", "", 0.0, 0.0,
                    "error", str(e)[:60], "", key_label=kenang_poc.LAST_FAL_KEY)
                continue
        out.write_bytes(requests.get(res["audio"]["url"], timeout=120).content)
        log("T5", "fal", MM, f"{tag}|{vname}", "", res.get("duration_ms", 0) / 1000,
            t.dt, cost, "ok", "", f"{len(text)} chars @ $0.10/1k", key_label=key_label)

# --- PhotoAnalysis JSON: fal VLM vs Gemini direct ---
SCHEMA_PROMPT = """Analyze this old family photo. Return ONLY valid JSON exactly matching:
{"photo_id": "<id>",
 "subjects": [{"id":"s1","desc":"<person desc: age, clothing>","face_quality":0.0}],
 "setting": "<scene description>",
 "era_style": "<photo era/style e.g. 'faded color print', 'BW 1960s'>",
 "mood": "<mood>",
 "quality_score": 0.0,
 "issues": ["<defects: blur, fading, damage>"]}
face_quality and quality_score are 0-1 floats. No markdown, no extra text."""

PHOTOS = ["foto01_bw_tua.jpg", "foto05_keluarga_grup.jpg", "foto13_buram.jpg"]

from google import genai
gclient = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])

for name in PHOTOS:
    pid = name.split("_")[0]
    url = fal_client.upload_file(str(POC / "ASSETS" / name))

    # (a) fal-hosted VLM
    try:
        budget_guard(0.01)
        with Timer() as t:
            res, key_label = fal_subscribe("openrouter/router/vision", {
                "prompt": SCHEMA_PROMPT.replace("<id>", pid),
                "image_urls": [url],
                "model": "google/gemini-2.5-flash",
                "temperature": 0.2, "max_tokens": 800})
        raw = res["output"].strip().removeprefix("```json").removesuffix("```").strip("` \n")
        (OUT_AN / f"{pid}__fal_vlm.json").write_text(raw, encoding="utf-8")
        cost = (res.get("usage") or {}).get("cost") or 0.005
        valid = "yes"
        try:
            json.loads(raw)
        except Exception:
            valid = "NO"
        log("T5", "fal", "openrouter/router/vision:gemini-2.5-flash", name, "", "",
            t.dt, cost, "ok", "", f"json_valid={valid}, actual billed cost from usage",
            key_label=key_label)
    except Exception as e:
        log("T5", "fal", "openrouter/router/vision", name, "", "", 0.0, 0.0,
            "error", str(e)[:60], "", key_label=kenang_poc.LAST_FAL_KEY)

    # (b) Gemini direct
    try:
        img_bytes = (POC / "ASSETS" / name).read_bytes()
        with Timer() as t:
            resp = gclient.models.generate_content(
                model="gemini-2.5-flash",
                contents=[genai.types.Part.from_bytes(data=img_bytes, mime_type="image/jpeg"),
                          SCHEMA_PROMPT.replace("<id>", pid)],
                config=genai.types.GenerateContentConfig(
                    response_mime_type="application/json", temperature=0.2))
        raw = resp.text
        (OUT_AN / f"{pid}__gemini.json").write_text(raw, encoding="utf-8")
        um = resp.usage_metadata
        cost = (um.prompt_token_count or 0) / 1e6 * 0.30 + \
               (um.candidates_token_count or 0) / 1e6 * 2.50
        valid = "yes"
        try:
            json.loads(raw)
        except Exception:
            valid = "NO"
        log("T5", "google", "gemini-2.5-flash", name, "", "", t.dt, cost, "ok", "",
            f"json_valid={valid}, tokens {um.prompt_token_count}+{um.candidates_token_count}",
            key_label="-")
    except Exception as e:
        log("T5", "google", "gemini-2.5-flash", name, "", "", 0.0, 0.0,
            "error", str(e)[:60], "", key_label="-")

print("T5 complete.")
