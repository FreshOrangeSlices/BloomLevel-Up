"""Throwaway: Skarrier Mobs' natural spawn conditions (MCreator procedures), Iron's necromancer and Mutant Monsters'
spawn rules, disassembled. Usage: skarrierconds.py <server.zip> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
WANT = {
    "skarrier_mobs": re.compile(r"procedures/\w*Natural\w*Procedure\.class$"),
    "irons_spellbooks": re.compile(r"(NecromancerEntity|ModSetup|SpawnPlacement\w*|CommonSetup\w*)\.class$"),
    "MutantMonsters": re.compile(r"(MutantMonsters|ModRegistry|Mutant(Zombie|Skeleton|Creeper|Enderman))\.class$"),
    "graveyard": re.compile(r"(SpawnRules?|Spawning|ModSpawns?|TGSpawns?|EntitySpawns?|SpawnRestrictions?)\w*\.class$"),
}
for name in sorted(server.namelist()):
    if not name.endswith(".jar") or "/mods/" not in "/" + name:
        continue
    jar_name = name.rsplit("/", 1)[-1]
    key = next((k for k in WANT if k.lower() in jar_name.lower()), None)
    if not key:
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    report = [f"# {jar_name}"]
    with tempfile.TemporaryDirectory() as tmp:
        for entry in jar.namelist():
            if WANT[key].search(entry):
                path = os.path.join(tmp, entry)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(jar.read(entry))
                text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
                report.append(f"## javap {entry}\n{text[:40000]}")
    open(os.path.join(out, re.sub(r"[^A-Za-z0-9_.-]", "_", jar_name) + ".txt"), "w").write("\n".join(report))
    print(jar_name, len(report) - 1, "classes")
