"""Throwaway: the three new mob mods on Modrinth (1.20.1 Forge versions, their required dependencies) and their jars'
entities, items and worldgen, once downloaded. Usage: reports12.py <server.zip> <shots dir> <out dir>"""
import io, json, os, re, sys, time, urllib.parse, urllib.request, urllib.error, zipfile
OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(path, raw=False, **params):
    q = "&".join(f"{k}={urllib.parse.quote(v if isinstance(v, str) else json.dumps(v))}" for k, v in params.items())
    url = path if path.startswith("http") else f"https://api.modrinth.com/v2{path}" + (f"?{q}" if q else "")
    for attempt in range(4):
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120).read()
            return data if raw else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(3)
        except Exception:
            time.sleep(3)
    return None
lines = []
found = {}
for query in ["undead unleashed", "born in chaos", "dungeon denizens", "gottschcore", "monster manual gottsch"]:
    r = get("/search", query=query, facets=[["project_type:mod"], ["versions:1.20.1"]], limit="6") or {"hits": []}
    lines.append(f"## search '{query}'")
    for h in r["hits"]:
        lines.append(f"  {h['slug']} | {h['title']} | {h['project_id']} | {h.get('downloads')} dl | {h.get('date_modified','')[:10]} | {h.get('categories')}")
for slug in ["9kMGkRHe", "undead-unleashed-continued", "undead-unleashed", "born-in-chaos", "borninchaos", "dungeon-denizens", "gottschcore", "gottschs-monster-manual", "monster-manual"]:
    p = get(f"/project/{slug}")
    if not p:
        lines.append(f"## {slug}: not on Modrinth"); continue
    vs = get(f"/project/{p['id']}/version", loaders=["forge"], game_versions=["1.20.1"]) or []
    lines.append(f"## {slug} -> {p['slug']} ({p['title']}, {p['id']}, license {p.get('license',{}).get('id')}, {p.get('downloads')} dl, updated {p.get('updated','')[:10]})")
    for v in vs[:6]:
        deps = []
        for d in v["dependencies"]:
            if d.get("project_id"):
                dp = get(f"/project/{d['project_id']}")
                deps.append(f"{d['dependency_type']}:{dp['slug'] if dp else d['project_id']}")
        lines.append(f"  {v['version_number']} [{v['version_type']}] {v['date_published'][:10]} files={[f['filename'] for f in v['files']]} deps={deps}")
    if vs:
        found[p["slug"]] = vs[0]
# The newest jar of each: entities, items, structures, spawns (biome modifiers), configs it names.
for slug, v in found.items():
    f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
    data = get(f["url"], raw=True)
    if not data:
        lines.append(f"## {slug}: couldn't download {f['url']}"); continue
    z = zipfile.ZipFile(io.BytesIO(data)); names = z.namelist()
    lang = {}
    for x in names:
        if re.match(r"assets/[^/]+/lang/en_us\.json$", x):
            try: lang.update(json.loads(z.read(x).decode("utf-8", "replace")))
            except ValueError: pass
    ents = sorted(k for k in lang if k.startswith("entity.") and k.count(".") == 2)
    items = sorted(k for k in lang if k.startswith("item.") and k.count(".") == 2)
    lines.append(f"## {slug} {v['version_number']} jar: {len(names)} files")
    lines.append(f"  entities ({len(ents)}): " + ", ".join(f"{k.split('.',1)[1]}={lang[k]}" for k in ents))
    lines.append(f"  items ({len(items)}): " + ", ".join(f"{k.split('.',2)[2]}={lang[k]}" for k in items))
    for x in names:
        if re.search(r"worldgen/(structure|structure_set)/|forge/biome_modifier/|biome_modifier|/loot_tables/(chests|entities)/", x) and x.endswith(".json"):
            lines.append(f"  {x}: {z.read(x).decode('utf-8','replace')[:400].replace(chr(10),' ')}")
        if x.endswith(".toml") and "META-INF" in x:
            lines.append(f"  {x}: {z.read(x).decode('utf-8','replace')[:1500].replace(chr(10),' | ')}")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines[:80]))
