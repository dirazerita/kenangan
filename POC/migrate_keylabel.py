"""One-shot migration: add key_label column to results.csv (default 'key1' for existing rows)."""
import csv
rows = list(csv.DictReader(open("results.csv", newline="", encoding="utf-8")))
if rows and "key_label" in rows[0]:
    print("already migrated")
else:
    for r in rows:
        r["key_label"] = "key1"
    cols = list(rows[0].keys())
    with open("results.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        w.writerows(rows)
    print(f"migrated {len(rows)} rows, columns now: {cols}")
