"""Throwaway: Born in Chaos and Dungeon Denizens 2.0 (with Gottsch's Monster Manual and GottschCore) in detail: mods.toml
dependencies, entities, items, every biome modifier file, structures, data-driven spawn files, classes that read the
date (holiday mobs), config file names. Usage: reports13.py <server.zip> <shots dir> <out dir>"""
import io, json, os, re, sys, time, urllib.request, urllib.error, zipfile
OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def fetch(url):
    for attempt in range(4):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180).read()
        except Exception as e:
            err = e; time.sleep(4)
    print("fetch failed", url, err); return None
def modrinth_latest(slug):
    vs = json.loads(fetch(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D"))
    f = next((f for f in vs[0]["files"] if f.get("primary")), vs[0]["files"][0])
    return vs[0]["version_number"], f["url"]
jars = {}
v, u = modrinth_latest("borninchaos"); jars[f"borninchaos {v}"] = u
v, u = modrinth_latest("gottschcore"); jars[f"gottschcore {v}"] = u
jars["ddenizens 2.0.0"] = "https://mediafilez.forgecdn.net/files/8457/533/ddenizens-1.20.1-2.0.0.jar"
jars["monster-manual 1.0.1"] = "https://mediafilez.forgecdn.net/files/8457/360/monster-manual-1.20.1-1.0.1.jar"
lines = []
for label, url in jars.items():
    data = fetch(url)
    lines.append(f"\n## {label} <- {url} ({len(data) if data else 0} bytes)")
    if not data:
        continue
    z = zipfile.ZipFile(io.BytesIO(data)); names = z.namelist()
    for x in names:
        if x == "META-INF/mods.toml":
            lines.append("  mods.toml: " + z.read(x).decode("utf-8", "replace").replace("\n", " | ")[:2500])
    lang = {}
    for x in names:
        if re.match(r"assets/[^/]+/lang/en_us\.json$", x):
            try: lang.update(json.loads(z.read(x).decode("utf-8", "replace")))
            except ValueError: pass
    ents = sorted(k for k in lang if k.startswith("entity.") and k.count(".") == 2)
    lines.append(f"  entities ({len(ents)}): " + ", ".join(f"{k.split('.',2)[2]}={lang[k]}" for k in ents))
    items = sorted(k for k in lang if k.startswith("item.") and k.count(".") == 2 and "spawn_egg" not in k)
    lines.append(f"  items ({len(items)}): " + ", ".join(f"{k.split('.',2)[2]}={lang[k]}" for k in items))
    mods = [x for x in names if "biome_modifier" in x and x.endswith(".json")]
    lines.append(f"  biome modifiers ({len(mods)}): " + ", ".join(x.split("/")[1] + ":" + x.rsplit("/", 1)[1][:-5] for x in mods))
    for x in names:
        if re.search(r"worldgen/(structure|structure_set)/", x) and x.endswith(".json"):
            lines.append(f"  {x}: {z.read(x).decode('utf-8','replace')[:300].replace(chr(10),' ')}")
        if re.search(r"(spawn|spawns|monster|denizen)s?/[^/]+\.json$", x) and x.startswith("data/") and "biome_modifier" not in x and "loot" not in x and "recipe" not in x and "advancement" not in x and "tags/" not in x:
            lines.append(f"  data {x}: {z.read(x).decode('utf-8','replace')[:400].replace(chr(10),' ')}")
    dated = [c for c in names if c.endswith(".class") and (b"java/util/Calendar" in z.read(c) or b"java/time/LocalDate" in z.read(c) or b"java/time/MonthDay" in z.read(c))]
    lines.append(f"  classes reading the date ({len(dated)}): " + ", ".join(c.rsplit('/',1)[1] for c in dated[:60]))
    cfg = sorted({m.decode() for c in names if c.endswith(".class") for m in re.findall(rb"[A-Za-z0-9_\-/]+\.(?:toml|json|cfg)", z.read(c)) if b"config" in m.lower() or m.count(b"/") <= 1})
    lines.append("  config-ish file names in code: " + ", ".join(cfg[:40]))
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines)[:6000])
