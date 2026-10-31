"""Throwaway: data and config files that give mobs effects (2147483647 ticks, strength, effects lists), in jars and the
server's config. Usage: datafx.py <server.zip> <shots dir> <out dir>"""
import glob, io, os, re, sys, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
PAT = re.compile(r"2147483647|\"(minecraft:)?strength\"|potion_effects|mob_effects|\"effects\"\s*:", re.I)
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar"):
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        for n in jar.namelist():
            if n.endswith((".json", ".toml", ".json5", ".snbt", ".mcfunction", ".cfg")) and not n.startswith(("assets/",)) and "/advancements/" not in n and "/recipes/" not in n:
                t = jar.read(n).decode("utf-8", "replace")
                if PAT.search(t) and re.search(r"zombie|monster|hostile|spawn|mob", t + n, re.I):
                    lines = [l.strip() for l in t.splitlines() if PAT.search(l)]
                    report.append(f"{name.rsplit('/', 1)[1]} :: {n} :: " + " | ".join(lines[:6])[:600])
for name in server.namelist():
    if name.startswith(("config/", "defaultconfigs/")) and name.endswith((".json", ".toml", ".json5", ".snbt", ".cfg", ".properties")):
        t = server.read(name).decode("utf-8", "replace")
        if PAT.search(t):
            lines = [l.strip() for l in t.splitlines() if PAT.search(l)]
            report.append(f"server zip :: {name} :: " + " | ".join(lines[:8])[:800])
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and path.endswith((".json", ".toml", ".json5", ".snbt", ".cfg", ".properties")):
        t = open(path, encoding="utf-8", errors="replace").read()
        if PAT.search(t):
            lines = [l.strip() for l in t.splitlines() if PAT.search(l)]
            report.append(f"generated :: {os.path.relpath(path, shots)} :: " + " | ".join(lines[:8])[:800])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "hits")
