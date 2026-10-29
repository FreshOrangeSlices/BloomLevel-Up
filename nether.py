"""Throwaway: where Nether structure sets and Forge structure modifiers live in the pack's jars.
Usage: nether.py <server.zip> <out file>"""
import io
import json
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
out = open(sys.argv[2], "w")


def walk(jar, label, depth=0):
    for entry in jar.namelist():
        if entry.endswith(".jar") and depth < 2:
            try:
                walk(zipfile.ZipFile(io.BytesIO(jar.read(entry))), f"{label}!{entry}", depth + 1)
            except zipfile.BadZipFile:
                pass
            continue
        low = entry.lower()
        if "structure_modifier" in low and entry.endswith(".json"):
            out.write(f"MODIFIER {label} {entry} {jar.read(entry).decode('utf-8', 'replace')[:600]}\n")
        if ("citadel" in low or "ancient_battleground" in low) and entry.endswith(".json") and "structure_set" in low:
            out.write(f"SET {label} {entry} {jar.read(entry).decode('utf-8', 'replace')[:600]}\n")
        if entry.endswith(".json") and "/worldgen/structure_set/" in entry and not entry.startswith("data/"):
            out.write(f"ODDSET {label} {entry}\n")


for name in server.namelist():
    if name.endswith(".jar") and "/mods/" in "/" + name:
        walk(zipfile.ZipFile(io.BytesIO(server.read(name))), name.rsplit("/", 1)[-1])
out.close()
print(open(sys.argv[2]).read()[:3000])
