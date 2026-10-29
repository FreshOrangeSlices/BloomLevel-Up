"""Throwaway: what happens when Migamigos' and Zoni's Companions' companions die, from their jars.
Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
summary = []
WORDS = re.compile(rb"(?i)revive|respawn|resurrect|downed|knock|soul|heart|keepsake|token|tombstone|grave|bed|die|death|dead|faint|recall|summon|device|bond|persist|despawn")
for name in server.namelist():
    low = name.lower()
    if not name.endswith(".jar") or not ("migamigos" in low or "zoni" in low):
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    stem = name.rsplit("/", 1)[-1]
    report = open(os.path.join(out, stem + ".txt"), "w")
    names = jar.namelist()
    for entry in names:
        if entry.endswith("lang/en_us.json"):
            report.write(f"== {entry}\n{jar.read(entry).decode('utf-8', 'replace')}\n")
    report.write("== classes\n" + "\n".join(n for n in names if n.endswith(".class")) + "\n")
    report.write("== data\n" + "\n".join(n for n in names if n.startswith("data/")) + "\n")
    for entry in names:
        if not entry.endswith(".class"):
            continue
        data = jar.read(entry)
        # String constants from the class file (UTF8 entries), filtered to the interesting ones.
        strings = sorted({m.decode("utf-8", "replace") for m in re.findall(rb"[\x20-\x7e]{4,}", data) if WORDS.search(m)})
        if strings:
            report.write(f"== {entry}\n" + "\n".join(strings[:200]) + "\n")
    report.close()
    summary.append(f"{stem}: {len(names)} entries")
print("\n".join(summary))
