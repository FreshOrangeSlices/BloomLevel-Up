"""Zombies and skeletons spawn in a well-lit base every night, a set distance from players: which mods change spawning?

Downloads every jar in the pack and lists the classes (and refmaps) that name the natural spawner, the monsters' light
test, the spawn rules or Forge's spawn events; then decompiles the mixins among them and Easy Mob Spawn Control's
spawn code.
"""
import json, os, re, subprocess, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"}
PATTERNS = [b"NaturalSpawner", b"isDarkEnoughToSpawn", b"checkMonsterSpawnRules", b"m_219009_", b"m_219013_",
            b"monsterSpawnBlockLightLimit", b"monsterSpawnLightTest", b"SpawnPlacementCheck", b"MobSpawnEvent$PositionCheck",
            b"MobSpawnEvent$FinalizeSpawn", b"LevelEvent$PotentialSpawns", b"spawnCategoryForPosition", b"m_47038_",
            b"m_47045_", b"m_220437_", b"SpawnPlacements", b"getMaxLocalRawBrightness"]


def get(urls):
    for url in urls:
        for _ in range(3):
            try:
                return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
            except Exception:
                pass
    return None


jars = json.load(open("probe/alljars.json"))
os.makedirs("jars", exist_ok=True)


def fetch(item):
    slug, urls = item
    path = os.path.join("jars", re.sub(r"[^A-Za-z0-9._-]", "_", slug) + ".jar")
    data = get(urls)
    if data:
        open(path, "wb").write(data)
        return slug, path
    return slug, None


out = []
p = out.append
with ThreadPoolExecutor(8) as pool:
    fetched = list(pool.map(fetch, jars.items()))
p(f"downloaded {sum(1 for _, x in fetched if x)} of {len(fetched)}; missing {[s for s, x in fetched if not x]}")

decompile = []
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    lines = []
    for name in z.namelist():
        if name.startswith(("net/minecraft/", "com/mojang/")) or not (name.endswith(".class") or name.endswith("refmap.json")):
            continue
        data = z.read(name)
        hits = [pat.decode() for pat in PATTERNS if pat in data]
        if not hits:
            continue
        lines.append(f"  {name}: {', '.join(hits)}")
        mixin = "mixin" in name.lower()
        spawny = any(h in hits for h in ("NaturalSpawner", "isDarkEnoughToSpawn", "checkMonsterSpawnRules", "m_219009_",
                                         "m_219013_", "monsterSpawnBlockLightLimit", "monsterSpawnLightTest", "m_47038_",
                                         "m_47045_", "m_220437_", "spawnCategoryForPosition"))
        if name.endswith(".class") and (mixin and spawny or slug == "easy-mob-spawn-control" and spawny):
            decompile.append((slug, path, name[:-6].replace("/", ".")))
        if name.endswith("refmap.json") and spawny:
            refs = re.findall(r'"([^"]*(?:NaturalSpawner|isDarkEnough|checkMonsterSpawn|m_219009_|m_47038_|m_47045_|m_220437_)[^"]*)"',
                              data.decode("utf-8", "replace"))
            lines.append("    refmap: " + "; ".join(sorted(set(refs)))[:800])
    if lines:
        p(f"==== {slug}")
        out.extend(lines[:40])

p(f"\n==== decompiling {len(decompile)} classes")
for slug, path, cls in decompile[:30]:
    r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    body = [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
    p(f"---- {slug} {cls}")
    p("\n".join(l[:220] for l in body)[:4500])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/spawning.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
