import csv
rows = list(csv.DictReader(open("results.csv", newline="", encoding="utf-8")))
t = sum(float(r["cost_usd"]) for r in rows)
by = {}
for r in rows:
    by[r["provider"]] = by.get(r["provider"], 0) + float(r["cost_usd"])
print(f"rows={len(rows)} total=${t:.2f}", {k: round(v, 3) for k, v in by.items()})
errs = [(r["task"], r["model"], r["input_ref"], r["error_code"]) for r in rows if r["status"] == "error"]
print(f"errors={len(errs)}")
for e in errs:
    print("  ", e)
