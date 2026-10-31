"""Throwaway: Born in Chaos's pumpkin family (biome modifiers, spawn rules, game rules) and whatever gives zombies potion
effects in the pack. Usage: pumpkins.py <server.zip> <shots dir> <out dir>"""
import glob, io, json, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []


def javap(jar, entries, limit=40000):
    texts = {}
    with tempfile.TemporaryDirectory() as tmp:
        for n in entries:
            path = os.path.join(tmp, n.replace("/", "_"))
            open(path, "wb").write(jar.read(n))
            texts[n] = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout[:limit]
    return texts


jars = {}
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar"):
        jars[name] = zipfile.ZipFile(io.BytesIO(server.read(name)))

# ---- 1. Born in Chaos: pumpkin biome modifiers, entity spawn rules, game rules
PUMPKIN = re.compile(r"pumpkin|spirit|dunce|felsteed", re.I)
bic = next(j for n, j in jars.items() if "born_in_chaos" in n.lower())
names = bic.namelist()
report.append("# Born in Chaos biome modifiers (pumpkin family, plus a sample of others)")
for n in sorted(names):
    if "biome_modifier" in n and n.endswith(".json"):
        text = bic.read(n).decode("utf-8", "replace")
        if PUMPKIN.search(n) or PUMPKIN.search(text):
            report.append(f"## {n}\n{text}")
report.append("# all biome modifier files: " + ", ".join(n.rsplit("/", 1)[1] for n in names if "biome_modifier" in n))
# Entity classes for the pumpkin family, their init() (spawn placement) and what it calls.
ents = [n for n in names if re.search(r"/entity/[A-Za-z]*(Pumpkin|Spirit|Dunce|Felsteed)[A-Za-z]*Entity\.class$", n)]
report.append("# pumpkin entity classes: " + ", ".join(e.rsplit("/", 1)[1] for e in ents))
procs = set()
for n, text in javap(bic, ents).items():
    lines = text.splitlines()
    keep, on = [], False
    for l in lines:
        if re.search(r"static void init\(\)|lambda\$init|checkSpawnRules|m_5545_|lambda\$", l):
            on = True
        elif re.match(r"  \S", l) and on and not re.search(r"init|lambda", l):
            on = False
        if on:
            keep.append(l)
    for m in re.finditer(r"procedures/(\w+)\.execute", text):
        procs.add(m.group(1))
    report.append(f"## {n.rsplit('/', 1)[1]} calls: {sorted(set(re.findall(r'procedures/(\w+)', text)))}\n" + "\n".join(keep[:200]))
spawnish = sorted(p for p in procs if re.search(r"spa[wv]n|condition|natural", p, re.I))
report.append("# spawn-ish procedures they call: " + ", ".join(spawnish))
for n, text in javap(bic, [n for n in names if re.search(r"/procedures/(" + "|".join(spawnish) + r")\.class$", n)] if spawnish else []).items():
    report.append(f"## {n}\n{text[:12000]}")
# Who spawns Lord Pumpkinhead (with or without his horse) and the Pumpkin Spirit, and the game rules' defaults.
for needle in [b"LORD_PUMPKINHEAD", b"PUMPKIN_SPIRIT", b"PUMPKIN_DUNCE", b"PUMPKINHEAD:"]:
    hits = [n.rsplit("/", 1)[1] for n in names if n.endswith(".class") and needle in bic.read(n)]
    report.append(f"# classes naming {needle.decode()}: {hits[:40]}")
rules = [n for n in names if n.endswith("ModGameRules.class")]
for n, text in javap(bic, rules).items():
    report.append(f"## {n}\n" + "\n".join(l for l in text.splitlines() if re.search(r"ldc|iconst|bipush|Field .*GameRules|putstatic|m_46250_|create", l))[:8000])
for n, text in javap(bic, [n for n in names if re.search(r"/procedures/(HalloweenEventSpawn|SirPumpkinheadOnInitialEntitySpawn|LordPumpkinhead\w*Spawn\w*)\w*\.class$", n)]).items():
    report.append(f"## {n}\n{text[:12000]}")

# ---- 2. Zombie potion effects: classes that add an effect when a mob spawns or joins, in any mod.
report.append("# classes that add effects on spawn/join (any mod)")
EFFECT = re.compile(rb"MobEffectInstance")
SPAWN = re.compile(rb"EntityJoinLevelEvent|FinalizeSpawn|MobSpawnEvent|m_6518_|SpecialSpawn|LivingSpawnEvent|finalizeSpawn")
ZOMBIE = re.compile(rb"monster/Zombie|zombie", re.I)
cands = []
for jname, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".class"):
            b = jar.read(n)
            if EFFECT.search(b) and SPAWN.search(b):
                cands.append((jname, n, bool(ZOMBIE.search(b))))
for jname, n, z in cands:
    report.append(f"{'ZOMBIE ' if z else ''}{jname.rsplit('/', 1)[1]} :: {n}")
for jname, n, z in [c for c in cands if c[2]][:25]:
    text = javap(jars[jname], [n], 15000)[n]
    report.append(f"## {jname.rsplit('/', 1)[1]} :: {n}\n" + "\n".join(l for l in text.splitlines() if re.search(r"public|static|MobEffect|Zombie|zombie|Event|ldc|Config|config|random|nextFloat|nextInt|if", l))[:6000])
# Mixins into Zombie or Mob.finalizeSpawn
report.append("# mixins naming Zombie / finalizeSpawn")
for jname, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".json") and "mixin" in n.lower():
            t = jar.read(n).decode("utf-8", "replace")
            hits = [m for m in re.findall(r'"([\w.$]*(?:Zombie|Spawn|Mob)[\w.$]*)"', t)]
            if hits:
                report.append(f"{jname.rsplit('/', 1)[1]} :: {n} :: {hits[:20]}")

# ---- 3. Config files that mention zombies and effects
report.append("# config lines about zombies or effects (server and client config)")
for path in glob.glob(os.path.join(shots, "**", "config", "**", "*"), recursive=True) + glob.glob(os.path.join(shots, "**", "serverconfig", "*"), recursive=True):
    if os.path.isfile(path) and path.endswith((".toml", ".json", ".json5", ".cfg", ".properties", ".txt", ".yml", ".yaml")):
        try:
            text = open(path, encoding="utf-8", errors="replace").read()
        except Exception:
            continue
        if re.search(r"zombie", text, re.I) and re.search(r"effect|potion", text, re.I):
            lines = [l.strip() for l in text.splitlines() if re.search(r"zombie|effect|potion|buff", l, re.I)]
            report.append(f"## {os.path.relpath(path, shots)}\n" + "\n".join(lines[:60]))

# ---- 4. The dump: where the pumpkin family spawns and at what weight
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)), None)
report.append("# pumpkin family in the dump's biome spawn lists")
if dump:
    by_mob = {}
    for line in open(dump):
        if '"kind":"biome"' in line.replace(" ", ""):
            r = json.loads(line)
            for cat, lst in r.get("spawns", {}).items():
                for s in lst:
                    if PUMPKIN.search(json.dumps(s)) and "born_in_chaos" in json.dumps(s):
                        key = json.dumps({k: v for k, v in s.items() if k not in ("biome",)}, sort_keys=True)
                        by_mob.setdefault(key, []).append(r["id"])
    for key, biomes in sorted(by_mob.items()):
        report.append(f"{key} in {len(biomes)} biomes: {', '.join(sorted(biomes))[:1500]}")
    sample = None
    for line in open(dump):
        if '"id":"minecraft:plains"' in line.replace(" ", "") and '"kind":"biome"' in line.replace(" ", ""):
            sample = line
    report.append(f"# plains row: {sample[:4000] if sample else None}")

open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "sections")
