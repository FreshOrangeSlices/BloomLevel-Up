"""Throwaway: find the add-on's naughtiness line (and other LegionWareX lines) in the pack test's kept files.
Usage: naughty.py <server.zip> <shots dir> <out dir>"""
import os, sys
_, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
report = []
for root, _, files in os.walk(shots):
    for f in files:
        path = os.path.join(root, f)
        report.append(f"file {os.path.relpath(path, shots)} {os.path.getsize(path)}")
        if os.path.getsize(path) < 200_000_000:
            try:
                for line in open(path, encoding="utf-8", errors="replace"):
                    if "naughtiness" in line or "LegionWareX:" in line:
                        report.append("  " + line.strip()[:400])
            except OSError:
                pass
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
