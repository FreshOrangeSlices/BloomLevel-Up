"""Throwaway: what gives zombies potion effects, and the pumpkin family's entity rows. Usage: zombies.py <server.zip> <shots dir> <out dir>"""
import glob, io, json, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []


def javap(jar, entry, limit=30000):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, entry.replace("/", "_"))
        open(path, "wb").write(jar.read(entry))
        return subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout[:limit]


jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
# Classes that name a zombie (vanilla class or any "zombie" string) and add an effect (addEffect: m_7292_ / m_147207_).
ADD = re.compile(rb"m_7292_|m_147207_|addEffect")
ZOMBIE = re.compile(rb"monster/Zombie|zombie|Zombie")
hits = []
for jname, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".class"):
            b = jar.read(n)
            if ADD.search(b) and ZOMBIE.search(b):
                hits.append((jname, n))
report.append("# classes naming zombies and adding effects")
report += [f"{j.rsplit('/', 1)[1]} :: {n}" for j, n in hits]
# Full javap of the likely ones: event handlers, mixins, procedures (not a mob's own attack code).
picked = [(j, n) for j, n in hits if re.search(r"mixin|Mixin|event|Event|Handler|handler|procedures/|Spawn|spawn|Infect|infect", n)]
picked += [(j, n) for j, n in [(j, n) for j, jar in jars.items() for n in jar.namelist()]
           if re.search(r"mythsandlegends/(mixin/.*Zombie|event/ServerEvents)|graveyard.*MobEntitySpawnMixin", n) and (j, n) not in picked]
for j, n in picked[:30]:
    text = javap(jars[j], n)
    keep = [l for l in text.splitlines() if re.search(r"class |public|private|static|MobEffect|Zombie|zombie|Event|ldc|Config|config|random|m_7292_|m_147207_|instanceof|Inject|Mixin|f_19", l)]
    report.append(f"## {j.rsplit('/', 1)[1]} :: {n}\n" + "\n".join(keep[:250]))
# Mixin configs and their targets that name zombies.
report.append("# mixin json naming zombies")
for j, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".json") and "mixin" in n.lower():
            t = jar.read(n).decode("utf-8", "replace")
            if re.search(r"Zombie", t):
                report.append(f"{j.rsplit('/', 1)[1]} :: {n} :: {t[:1500]}")
# Configs anywhere in the server or client folders that name zombies.
report.append("# config files naming zombies")
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and path.endswith((".toml", ".json", ".json5", ".cfg", ".properties", ".yml", ".yaml", ".txt")):
        text = open(path, encoding="utf-8", errors="replace").read()
        lines = [l.strip() for l in text.splitlines() if re.search(r"zombie", l, re.I)]
        if lines:
            report.append(f"## {os.path.relpath(path, shots)}\n" + "\n".join(lines[:40]))
# The pumpkin family's entity rows (class chain, health, boss bar).
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)), None)
report.append("# born in chaos entity rows")
if dump:
    for line in open(dump):
        if '"kind":"entity"' in line.replace(" ", "") and "born_in_chaos" in line:
            r = json.loads(line)
            r.pop("tags", None)
            report.append(json.dumps(r))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "lines")
