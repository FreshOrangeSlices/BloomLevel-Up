"""Throwaway: how the pack's summoned bosses are reached. For the boss mods: every advancement (title, description,
trigger, entities and items named), lang lines about summoning, altars and keys, and the structure templates whose
palette holds an altar, summoning or boss-spawner block (with the structures that reach them).
Usage: reports11.py <server.zip> <shots dir> <out dir>"""
import io, json, os, re, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location("r10", os.path.join(os.path.dirname(__file__), "reports10.py"))
src = open(os.path.join(os.path.dirname(__file__), "reports10.py")).read().split("# --- the dump")[0]
ns = {}
exec(src.replace("server = zipfile.ZipFile(sys.argv[1]); SHOTS = sys.argv[2]; OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)", ""), ns)
nbt = ns["nbt"]
server = zipfile.ZipFile(sys.argv[1]); OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
MODS = {"cataclysm", "block_factorys_bosses", "legendary_monsters", "monsterexpansion", "soulsweapons", "graveyard", "oddities",
        "skarrier_mobs", "legionary", "irons_spellbooks", "illagerinvasion", "aquamirae", "mythsandlegends", "mutantmonsters"}
WORDS = re.compile(r"(?i)summon|altar|ritual|awaken|offering|lure|call(s|ed)? (forth|upon)|challenge|to spawn|spawns? (the|a) boss|boss")
BLOCKS = re.compile(r"(?i)altar|summon|boss_?spawn|spawner|ritual|shrine|pedestal|tomb|sarcophag|effigy|totem")
lang, adv, palette_hits, pools, structures = {}, [], {}, {}, {}
for n in server.namelist():
    if not (n.endswith(".jar") and "/mods/" in "/" + n):
        continue
    try:
        z = zipfile.ZipFile(io.BytesIO(server.read(n)))
    except zipfile.BadZipFile:
        continue
    names = z.namelist()
    if not any(x.split("/")[1] in MODS for x in names if x.startswith(("data/", "assets/")) and x.count("/") > 1):
        continue
    for x in names:
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", x)
        if m and m[1] in MODS:
            try:
                lang.update(json.loads(z.read(x).decode("utf-8", "replace")))
            except ValueError:
                pass
        m = re.match(r"data/([^/]+)/advancements/(.+)\.json$", x)
        if m and m[1] in MODS:
            try:
                j = json.loads(z.read(x))
            except ValueError:
                continue
            d = j.get("display", {})
            t, ds = d.get("title"), d.get("description")
            t = t.get("translate", t.get("text")) if isinstance(t, dict) else t
            ds = ds.get("translate", ds.get("text")) if isinstance(ds, dict) else ds
            crit = {k: v.get("trigger") for k, v in j.get("criteria", {}).items()}
            text = json.dumps(j.get("criteria", {}))
            adv.append({"id": f"{m[1]}:{m[2]}", "title": t, "desc": ds, "parent": j.get("parent"), "criteria": crit,
                        "names": sorted(set(re.findall(r'"([a-z0-9_]+:[a-z0-9_/]+)"', text)) - {"minecraft:player"})[:12]})
        m = re.match(r"data/([^/]+)/worldgen/structure/(.+)\.json$", x)
        if m and m[1] in MODS:
            try:
                structures[f"{m[1]}:{m[2]}"] = json.loads(z.read(x)).get("start_pool")
            except ValueError:
                pass
        m = re.match(r"data/([^/]+)/worldgen/template_pool/(.+)\.json$", x)
        if m:
            try:
                j = json.loads(z.read(x))
            except ValueError:
                continue
            locs = set()
            for e in j.get("elements", []):
                el = e.get("element", {})
                for sub in [el] + el.get("elements", []):
                    if isinstance(sub.get("location"), str):
                        locs.add(sub["location"] if ":" in sub["location"] else "minecraft:" + sub["location"])
            pools[f"{m[1]}:{m[2]}"] = locs
        m = re.match(r"data/([^/]+)/structures?/(.+)\.nbt$", x)
        if m and m[1] in MODS:
            try:
                root = nbt(z.read(x))
            except Exception:
                continue
            pal = root.get("palette") or (root.get("palettes") or [[]])[0]
            hits = sorted({p.get("Name") for p in pal if isinstance(p, dict) and BLOCKS.search(str(p.get("Name", "")))})
            if hits:
                palette_hits[f"{m[1]}:{m[2]}"] = hits
lines = ["# Advancements (boss mods)"]
for a in sorted(adv, key=lambda a: a["id"]):
    lines.append(f"  {a['id']}: [{lang.get(a['title'], a['title'])}] {lang.get(a['desc'], a['desc'])} | {a['criteria']} | {a['names']}")
lines.append("\n# Lang lines about summoning, altars, bosses")
for k, v in sorted(lang.items()):
    if isinstance(v, str) and (WORDS.search(v) or WORDS.search(k)) and not k.startswith(("subtitles.", "death.")):
        lines.append(f"  {k} = {v[:300]}")
lines.append("\n# Templates with altar / summon / spawner blocks (and structures reaching them directly)")
for t, h in sorted(palette_hits.items()):
    owners = [s for s, sp in structures.items() if isinstance(sp, str) and t in pools.get(sp if ':' in sp else 'minecraft:' + sp, set())]
    lines.append(f"  {t}: {h} <- {owners}")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
print("\n".join(lines[:50]))
