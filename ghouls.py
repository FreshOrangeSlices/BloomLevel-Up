"""Throwaway: Monster Manual's (gmm) mobs: which add effects to themselves when they spawn, their spawn biomes, and its config.
Usage: ghouls.py <server.zip> <shots dir> <out dir>"""
import glob, io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
gmm = next(j for n, j in jars.items() if "monster-manual" in n)
with tempfile.TemporaryDirectory() as tmp:
    for n in gmm.namelist():
        if n.endswith(".class") and re.search(rb"MobEffectInstance", gmm.read(n)) and re.search(r"entity/monster", n):
            path = os.path.join(tmp, n.replace("/", "_"))
            open(path, "wb").write(gmm.read(n))
            text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
            keep = [l for l in text.splitlines() if re.search(r"class |public|protected|m_6518_|finalizeSpawn|MobEffect|f_19\d+_|m_7292_|ldc|random|nextFloat|Config|config", l)]
            report.append(f"## {n}\n" + "\n".join(keep[:160]))
for n in gmm.namelist():
    if not n.endswith((".class", ".png", ".ogg", ".nbt")) and re.search(r"biome_modifier|config|spawn", n, re.I):
        report.append(f"## file {n}\n{gmm.read(n).decode('utf-8', 'replace')[:2500]}")
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and re.search(r"gmm|monster.?manual", path, re.I):
        report.append(f"## {os.path.relpath(path, shots)}\n" + open(path, encoding="utf-8", errors="replace").read()[:5000])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "sections")
