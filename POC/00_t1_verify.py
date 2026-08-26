"""T1: shortlist Indonesian ElevenLabs voices (key is scoped: voices_read + tts only;
models_read/user_read denied — model ids taken from public docs: eleven_multilingual_v2, eleven_v3)."""
import os, requests
from kenang_poc import log

EL_KEY = os.environ["ELEVENLABS_API_KEY"]
H = {"xi-api-key": EL_KEY}

r = requests.get("https://api.elevenlabs.io/v2/voices?page_size=50", headers=H, timeout=30)
r.raise_for_status()
own = r.json().get("voices", [])
print(f"OWN/DEFAULT VOICES: {len(own)}")
for v in own:
    labels = v.get("labels") or {}
    print(f"  {v['voice_id']}  {v['name']:<12} gender={labels.get('gender')} "
          f"age={labels.get('age')} lang={labels.get('language')} desc={labels.get('description')}")

for sort in ("cloned_by_count", "trending"):
    r = requests.get("https://api.elevenlabs.io/v1/shared-voices", headers=H, timeout=30,
                     params={"language": "id", "page_size": 30, "sort": sort})
    if not r.ok:
        print("shared-voices failed:", r.status_code, r.text[:200])
        continue
    shared = r.json().get("voices", [])
    print(f"\nSHARED INDONESIAN VOICES (sort={sort}): {len(shared)}")
    for v in shared:
        print(f"  {v['voice_id']}  {v['name']:<20} gender={v.get('gender')} age={v.get('age')} "
              f"usecase={v.get('use_case')} cloned={v.get('cloned_by_count')} "
              f"desc={(v.get('description') or '')[:70]}")
    break

log("T1", "elevenlabs", "api-metadata", "voices+shared-voices", cost_usd=0.0,
    notes="free metadata; key scoped tts+voices only")
print("\nT1 ElevenLabs voice listing done.")
