"""Shared helpers for Kenang Phase 00 PoC: env, cost logging, budget guard."""
import csv, os, sys, time
from pathlib import Path
from dotenv import load_dotenv

POC = Path(__file__).parent
ROOT = POC.parent
load_dotenv(ROOT / ".env")

RESULTS = POC / "results.csv"
COLUMNS = ["task", "provider", "model", "input_ref", "ratio", "duration_s",
           "latency_s", "cost_usd", "status", "error_code", "notes", "key_label"]

# --- multi-key fal rotation (AD-14 PoC): FAL_KEY + optional FAL_KEY_2 / FAL_KEY_3 ---
FAL_KEYS = [(label, os.getenv(env)) for label, env in
            (("key1", "FAL_KEY"), ("key2", "FAL_KEY_2"), ("key3", "FAL_KEY_3"))]
FAL_KEYS = [(l, k) for l, k in FAL_KEYS if k]
FAL_COOLDOWN_S = 600  # 10 min before an exhausted key may be retried
_exhausted_at = {}    # key_label -> unix ts
LAST_FAL_KEY = "key1"  # label of the key used by the most recent fal_subscribe attempt


def _is_balance_exhausted(exc) -> bool:
    # exact signature captured in Phase 00 T4 (see results.csv / errors.log)
    s = str(exc)
    return "User is locked" in s and ("TOP_UP" in s or "Exhausted balance" in s)


def fal_subscribe(slug, arguments):
    """fal_client.subscribe with balance-exhausted failover. Returns (result, key_label)."""
    global LAST_FAL_KEY
    import fal_client
    last_exc = None
    for label, key in FAL_KEYS:
        if time.time() - _exhausted_at.get(label, 0) < FAL_COOLDOWN_S:
            continue
        LAST_FAL_KEY = label
        try:
            return fal_client.SyncClient(key=key).subscribe(slug, arguments=arguments), label
        except Exception as e:
            if _is_balance_exhausted(e):
                _exhausted_at[label] = time.time()
                print(f"[fal] key '{label}' balance exhausted — 10-min cooldown, trying next key")
                last_exc = e
                continue
            raise
    raise last_exc or RuntimeError("no usable fal key (none configured or all exhausted/cooling down)")

BUDGET_USD = 40.00
STOP_AT = 0.80 * BUDGET_USD  # 32.00


def _ensure_csv():
    if not RESULTS.exists():
        with open(RESULTS, "w", newline="", encoding="utf-8") as f:
            csv.writer(f).writerow(COLUMNS)


def spent() -> float:
    _ensure_csv()
    total = 0.0
    with open(RESULTS, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                total += float(row["cost_usd"] or 0)
            except ValueError:
                pass
    return total


def budget_guard(next_cost: float = 0.0):
    s = spent()
    if s + next_cost >= STOP_AT:
        print(f"BUDGET GUARD: spent ${s:.2f} + next ${next_cost:.2f} >= 80% cap "
              f"(${STOP_AT:.2f}). STOPPING.", file=sys.stderr)
        sys.exit(42)


def log(task, provider, model, input_ref, ratio="", duration_s="", latency_s="",
        cost_usd=0.0, status="ok", error_code="", notes="", key_label="key1"):
    _ensure_csv()
    with open(RESULTS, "a", newline="", encoding="utf-8") as f:
        csv.writer(f).writerow([task, provider, model, input_ref, ratio,
                                duration_s, f"{latency_s:.1f}" if isinstance(latency_s, float) else latency_s,
                                f"{cost_usd:.4f}", status, error_code, notes, key_label])
    print(f"[log] {task} {model} {input_ref} -> {status} (${cost_usd:.4f}, total ${spent():.2f})")


class Timer:
    def __enter__(self):
        self.t0 = time.time()
        return self

    def __exit__(self, *a):
        self.dt = time.time() - self.t0
