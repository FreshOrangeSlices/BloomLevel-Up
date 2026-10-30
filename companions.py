"""Throwaway: which mods in the pack bind a dodge or roll key (every jar in the server zip, plus the client-only ones
from their download links), and Xaero's radar category defaults. Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
jars = {}
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if name.endswith(".jar"):
        jars[name] = server.read(name)
for url in ["https://mediafilez.forgecdn.net/files/9205/892/touch_n_motion_forge_1.20.1_1.0.4.jar",
            "https://cdn.modrinth.com/data/IAzUFvS6/versions/bOJEwehn/fwa%2B1.20.1-forge-1.2.31.jar",
            "https://cdn.modrinth.com/data/8aoMKplv/versions/jzgRarPk/punchy-2.8g-forge-1.20.1.jar",
            "https://cdn.modrinth.com/data/pZ2wrerK/versions/2f5mEUPY/emotecraft-for-MC1.20.1-2.2.7-b.build.50-forge.jar"]:
    try:
        jars[url.rsplit("/", 1)[1]] = urllib.request.urlopen(url).read()
    except Exception as error:
        print("couldn't fetch", url, error)
lines = []
def scan(label, data):
    try:
        jar = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return
    for entry in jar.namelist():
        if entry.endswith(".jar"):
            scan(label + "!" + entry, jar.read(entry))
        elif entry.endswith("en_us.json"):
            try:
                lang = json.loads(jar.read(entry).decode("utf-8", "replace"))
            except ValueError:
                continue
            for key, value in lang.items():
                if re.search(r"(?i)(roll|dodge|dash|evade|evasion|sidestep|slide)", key + " " + str(value)) and re.search(r"(?i)^key|keybind|category", key):
                    lines.append(f"{label}\t{key} = {str(value)[:150]}")
for label, data in jars.items():
    scan(os.path.basename(label), data)
open(os.path.join(out, "rolls.txt"), "w").write("\n".join(sorted(set(lines))))
print(len(lines), "lines")
