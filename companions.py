"""Throwaway: JEI's load times in the pack test's client log (L6), and Iron's Spellbooks' jar.
Usage: companions.py <server.zip> <out dir>"""
import glob
import os
import re
import sys
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
logs = glob.glob("shots/**/latest.log", recursive=True)
with open(os.path.join(out, "jeilog.txt"), "w") as f:
    for log in logs:
        f.write(f"== {log}\n")
        for line in open(log, errors="replace"):
            if re.search(r"jei|JEI|Registering recipes|took", line) and len(line) < 600:
                f.write(line)
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if name.endswith(".jar") and "irons_spellbooks" in name.lower():
        data = server.read(name)
        open(os.path.join(out, "irons.jar.bin"), "wb").write(data)
        print(name, len(data))
print(len(logs), "logs")
