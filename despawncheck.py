"""Throwaway: does Easy Mob Spawn Control's despawn/cap logic spare persistent mobs, and how do the Orchid Shrine's queen and
the Champion's Graves' draugr boss get placed? Usage: despawncheck.py <server.zip> <shots dir> <out dir>"""
import gzip, io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
report = []


def javap(jar, n):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "x.class")
        open(path, "wb").write(jar.read(n))
        return subprocess.run(["javap", "-c", "-p", path], capture_output=True, text=True).stdout


emsc = next(j for n, j in jars.items() if re.search(r"easy.?mob|emsc", n, re.I))
report.append("# EMSC classes: " + ", ".join(n for n in emsc.namelist() if n.endswith(".class") and re.search(r"despawn|cap|spawn", n, re.I))[:3000])
for n in emsc.namelist():
    if n.endswith(".class") and re.search(r"despawn|cap", n, re.I):
        text = javap(emsc, n)
        keep = [l for l in text.splitlines() if re.search(r"^\S|^  \S|m_21532_|m_8023_|isPersistence|requiresCustomPersistence|m_6785_|removeWhenFarAway|MobSpawnType|SpawnPlacementCheck|FinalizeSpawn|EntityJoinLevel|getSpawnType|m_21023_|isNoAi|m_21525_|Event|STRUCTURE|CHUNK_GENERATION|NATURAL", l)]
        report.append(f"## {n}\n" + "\n".join(keep)[:6000])


def nbt_entities(data):
    raw = gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data
    return sorted(set(m.decode() for m in re.findall(rb"[a-z0-9_]+:[a-z0-9_/]+", raw) if re.search(rb"queen|orchid|draugr", m)))


for label, pat in (("oddities", r"oddities"), ("soulsweapons", r"soulsweapons|soulslike|marium")):
    for n, j in jars.items():
        if re.search(pat, n, re.I):
            for e in j.namelist():
                if e.endswith(".nbt") and re.search(r"orchid|shrine|grave|champion|draugr", e, re.I):
                    report.append(f"## {n}::{e} names: {nbt_entities(j.read(e))}")
                if e.endswith(".class") and re.search(r"Orchid|Shrine|Draugr|Champion", e) and re.search(r"structure|world|feature|piece", e, re.I):
                    text = javap(j, e)
                    report.append(f"## {n}::{e}\n" + "\n".join(l for l in text.splitlines() if re.search(r"^\S|^  \S|m_21530_|setPersistence|m_7967_|addFreshEntity|EntityType|spawn", l))[:3000])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
