"""Throwaway: what Xaero's Minimap left in the test client's config (from the pack-screenshots artifact), and the
client log's Xaero lines. Usage: companions.py <server.zip> <out dir> (reads ./shots)."""
import os
import sys

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
lines = []
for root, dirs, files in os.walk("shots"):
    for f in files:
        path = os.path.join(root, f)
        if "xaero" in path.lower():
            lines.append(f"== {path} ({os.path.getsize(path)} bytes)")
            try:
                lines.extend(open(path, errors="replace").read().splitlines()[:80])
            except OSError:
                pass
        if f == "latest.log":
            for l in open(path, errors="replace"):
                if "xaero" in l.lower() or "minimap" in l.lower():
                    lines.append("LOG " + l.rstrip()[:300])
open(os.path.join(out, "xaero-config.txt"), "w").write("\n".join(lines[:2000]))
print(len(lines), "lines")
