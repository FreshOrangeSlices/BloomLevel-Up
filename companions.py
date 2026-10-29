"""Throwaway: Xaero's Minimap 26.6.0 (Forge 1.20.1) from Modrinth: its option names and config files, for locking the
minimap to north. Usage: companions.py <server.zip> <out dir>"""
import io
import os
import re
import subprocess
import sys
import urllib.request
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
url = "https://cdn.modrinth.com/data/1bokaNcj/versions/dB6E0CY9/xaerominimap-forge-1.20.1-26.6.0.jar"
data = urllib.request.urlopen(url).read()
open(os.path.join(out, "xaerominimap.jar.bin"), "wb").write(data)
jar = zipfile.ZipFile(io.BytesIO(data))
lines = []
for name in jar.namelist():
    if name.endswith(".class"):
        text = jar.read(name).decode("latin-1")
        for word in set(re.findall(r"[A-Za-z0-9_./ -]{3,80}", text)):
            if re.search(r"(?i)north|lockNorth|rotat|\.cfg|\.txt|profiles|config/xaero|minimap/", word):
                lines.append(f"{name}\t{word}")
    elif name.endswith("en_us.json") or name.endswith("en_us.lang"):
        for line in jar.read(name).decode("utf-8", "replace").splitlines():
            if re.search(r"(?i)north|rotat", line):
                lines.append(f"{name}\t{line.strip()}")
open(os.path.join(out, "xaero-strings.txt"), "w").write("\n".join(sorted(set(lines))))
print(len(lines), "strings")
