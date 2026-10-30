"""Throwaway: the pack's boss list. For every boss (Bloom's boss tag, forge:bosses or c:bosses, or a boss bar), from the
server dump and the mod jars: which structure templates hold it (as an entity, in a spawner, or any string naming it),
which structures reach those templates (jigsaw pools, followed through jigsaw blocks), classes that name both a boss and
a structure piece (code-built lairs), structure and biome spawns naming it, advancements naming it or a structure,
exploration maps in loot and structure tags, and items that look like finders or summons.
Usage: reports10.py <server.zip> <shots dir> <out dir>"""
import glob, gzip, io, json, os, re, struct, sys, zipfile

server = zipfile.ZipFile(sys.argv[1]); SHOTS = sys.argv[2]; OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)


def nbt(data):
    """A minimal NBT reader (big-endian, gzip): returns the root compound as Python values."""
    try:
        data = gzip.decompress(data)
    except OSError:
        pass
    pos = [0]

    def read(fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack_from(">" + fmt, data, pos[0])
        pos[0] += size
        return value[0]

    def string():
        n = read("H")
        s = data[pos[0]:pos[0] + n].decode("utf-8", "replace")
        pos[0] += n
        return s

    def payload(tag):
        if tag == 1: return read("b")
        if tag == 2: return read("h")
        if tag == 3: return read("i")
        if tag == 4: return read("q")
        if tag == 5: return read("f")
        if tag == 6: return read("d")
        if tag == 7:
            n = read("i"); pos[0] += n; return b""
        if tag == 8: return string()
        if tag == 9:
            inner = read("b"); n = read("i")
            return [payload(inner) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = read("b")
                if t == 0:
                    return out
                name = string()
                out[name] = payload(t)
        if tag == 11:
            n = read("i"); pos[0] += 4 * n; return []
        if tag == 12:
            n = read("i"); pos[0] += 8 * n; return []
        raise ValueError(f"tag {tag}")

    t = read("b")
    string()
    return payload(t)


def strings(value, out):
    if isinstance(value, str):
        out.add(value)
    elif isinstance(value, dict):
        for v in value.values():
            strings(v, out)
    elif isinstance(value, list):
        for v in value:
            strings(v, out)
    return out


# --- the dump
dump_file = next(iter(glob.glob(os.path.join(SHOTS, "**", "bloom-dump.jsonl"), recursive=True)), None)
rows = [json.loads(l) for l in open(dump_file) if l.strip()] if dump_file else []
entities = {r["id"]: r for r in rows if r.get("kind") == "entity"}
BOSS_TAGS = {"bloomlevelup:bosses", "forge:bosses", "c:bosses"}
bosses = {i: r for i, r in entities.items() if BOSS_TAGS & set(r.get("tags", [])) or r.get("bossBar")}
boss_ids = set(bosses)
summary = [f"dump: {dump_file} ({len(rows)} rows), bosses: {len(bosses)}"]

report = {b: {"health": r.get("health"), "category": r.get("category"), "tags": [t for t in r.get("tags", []) if "boss" in t],
              "bossBar": r.get("bossBar"), "despawns": r.get("despawns"), "templates": {}, "structures": set(), "code": set(),
              "structureSpawns": [], "biomeSpawns": [], "advancements": [], "name": None} for b, r in bosses.items()}

for r in rows:
    if r.get("kind") == "structure":
        for cat, o in r.get("spawns", {}).items():
            for s in o.get("spawns", []):
                if s["type"] in report:
                    report[s["type"]]["structureSpawns"].append(f"{r['id']} ({cat}, weight {s['weight']})")
    if r.get("kind") == "biome":
        for cat, lst in r.get("spawns", {}).items():
            for s in lst:
                if s["type"] in report:
                    report[s["type"]]["biomeSpawns"].append(f"{r['id']} ({cat}, {s['weight']})")
structure_sets = {r["id"]: (r["spacing"], r["separation"]) for r in rows if r.get("kind") == "structure_set"}

# --- the jars
lang, structures, sets, pools, templates, adv_rows, maps, struct_tags, items_of_note, code_rows = {}, {}, {}, {}, {}, [], [], {}, [], []
ITEM_WORDS = re.compile(r"(?i)summon|altar|totem|sigil|offering|horn|eye of|eye_of|_eye\b|compass|map\b|chart|key\b|_key\b|scroll|invocation|ritual|call|beacon|relic|core|heart|egg")
boss_mods = {b.split(":")[0] for b in boss_ids}

for n in server.namelist():
    if not (n.endswith(".jar") and "/mods/" in "/" + n):
        continue
    try:
        z = zipfile.ZipFile(io.BytesIO(server.read(n)))
    except zipfile.BadZipFile:
        continue
    jar = n.split("/")[-1]
    names = z.namelist()
    for x in names:
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", x)
        if m:
            try:
                lang.update(json.loads(z.read(x).decode("utf-8", "replace")))
            except ValueError:
                pass
        m = re.match(r"data/([^/]+)/worldgen/structure/(.+)\.json$", x)
        if m:
            sid = f"{m[1]}:{m[2]}"
            try:
                j = json.loads(z.read(x))
            except ValueError:
                continue
            structures[sid] = {"jar": jar, "type": j.get("type"), "start_pool": j.get("start_pool"), "biomes": j.get("biomes"),
                               "step": j.get("step"), "mentions": sorted(strings(j, set()) & boss_ids)}
        m = re.match(r"data/([^/]+)/worldgen/structure_set/(.+)\.json$", x)
        if m:
            try:
                j = json.loads(z.read(x))
            except ValueError:
                continue
            members = [s["structure"] if isinstance(s, dict) else s for s in (j.get("structures") or [])]
            sets[f"{m[1]}:{m[2]}"] = {"structures": members, "placement": j.get("placement")}
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
                    loc = sub.get("location")
                    if isinstance(loc, str):
                        locs.add(loc if ":" in loc else "minecraft:" + loc)
            pools[f"{m[1]}:{m[2]}"] = sorted(locs)
        m = re.match(r"data/([^/]+)/structures?/(.+)\.nbt$", x)
        if m:
            tid = f"{m[1]}:{m[2]}"
            try:
                root = nbt(z.read(x))
            except Exception as e:  # noqa
                templates[tid] = {"error": str(e)}
                continue
            found = strings(root, set())
            hits = {b for b in boss_ids if any(b == s or b in s for s in found)}
            jig = set()
            for blk in root.get("blocks", []) if isinstance(root, dict) else []:
                bn = blk.get("nbt") if isinstance(blk, dict) else None
                if isinstance(bn, dict) and isinstance(bn.get("pool"), str):
                    p = bn["pool"]
                    jig.add(p if ":" in p else "minecraft:" + p)
            ents = sorted({e.get("nbt", {}).get("id") for e in root.get("entities", []) if isinstance(e, dict) and isinstance(e.get("nbt"), dict)} - {None}) \
                if isinstance(root, dict) else []
            templates[tid] = {"bosses": sorted(hits), "pools": sorted(jig), "entities": ents[:20]}
            for b in hits:
                report[b]["templates"][tid] = jar
        m = re.match(r"data/([^/]+)/advancements/(.+)\.json$", x)
        if m:
            text = z.read(x).decode("utf-8", "replace")
            named = sorted(b for b in boss_ids if f'"{b}"' in text)
            structs = sorted(set(re.findall(r'"structure"\s*:\s*"([^"]+)"', text)))
            if named or structs:
                try:
                    j = json.loads(text)
                    title = j.get("display", {}).get("title")
                    title = title.get("translate") if isinstance(title, dict) else title
                except ValueError:
                    title = None
                adv_rows.append({"id": f"{m[1]}:{m[2]}", "title": title, "bosses": named, "structures": structs, "jar": jar})
        m = re.match(r"data/([^/]+)/loot_tables/(.+)\.json$", x)
        if m:
            text = z.read(x).decode("utf-8", "replace")
            if "exploration_map" in text or "furled_map" in text:
                maps.append({"table": f"{m[1]}:{m[2]}", "destinations": re.findall(r'"destination"\s*:\s*"([^"]+)"', text), "jar": jar})
        m = re.match(r"data/([^/]+)/tags/worldgen/structure/(.+)\.json$", x)
        if m:
            try:
                j = json.loads(z.read(x))
                struct_tags[f"{m[1]}:{m[2]}"] = [v if isinstance(v, str) else v.get("id") for v in j.get("values", [])]
            except ValueError:
                pass
    # Code-built lairs: classes that name a boss (its id, or its registry field) and look like structure code.
    ns = {x.split("/")[1] for x in names if x.startswith(("data/", "assets/")) and x.count("/") > 1}
    mine = [b for b in boss_ids if b.split(":")[0] in ns]
    if mine:
        for c in names:
            if not c.endswith(".class"):
                continue
            if not re.search(r"(?i)structure|piece|feature|dungeon|arena|lair|spawner|altar|summon|ritual|event", c):
                continue
            raw = z.read(c)
            hit = [b for b in mine if b.encode() in raw or b.split(":")[1].upper().encode() in raw]
            if hit:
                code_rows.append({"class": c, "bosses": hit, "jar": jar})
                for b in hit:
                    report[b]["code"].add(c.rsplit("/", 1)[-1])

# Jigsaw: structures -> pools -> templates (-> jigsaw pools -> ...)
for sid, s in structures.items():
    start = s.get("start_pool")
    if not isinstance(start, str):
        continue
    seen_pools, queue, reached = set(), [start if ":" in start else "minecraft:" + start], set()
    while queue:
        p = queue.pop()
        if p in seen_pools:
            continue
        seen_pools.add(p)
        for loc in pools.get(p, []):
            reached.add(loc)
            for q in templates.get(loc, {}).get("pools", []):
                queue.append(q)
    s["templates_reached"] = len(reached)
    for loc in reached:
        for b in templates.get(loc, {}).get("bosses", []):
            report[b]["structures"].add(sid)
for sid, s in structures.items():
    for b in s["mentions"]:
        report[b]["structures"].add(sid + " (named in the structure)")
for a in adv_rows:
    for b in a["bosses"]:
        report[b]["advancements"].append(f"{a['id']} [{lang.get(a['title'], a['title'])}]")
for b, r in report.items():
    ns, path = b.split(":")
    r["name"] = lang.get(f"entity.{ns}.{path}", path)
    r["structures"] = sorted(r["structures"])
    r["code"] = sorted(r["code"])

for x in [i for i in lang if i.startswith("item.") and i.split(".")[1] in boss_mods]:
    if ITEM_WORDS.search(x) or ITEM_WORDS.search(str(lang[x])):
        items_of_note.append(f"{x} = {lang[x]}")

set_of = {}
for set_id, s in sets.items():
    for st in s["structures"]:
        pl = s["placement"] or {}
        set_of[st] = f"{set_id} ({pl.get('type')}, spacing {pl.get('spacing')}, separation {pl.get('separation')}; now {structure_sets.get(set_id)})"

lines = summary[:]
for b in sorted(report, key=lambda k: (k.split(':')[0], k)):
    r = report[b]
    lines.append(f"\n## {b} — {r['name']} (hp {r['health']}, {r['category']}, bar {r['bossBar']}, despawns {r['despawns']}, tags {r['tags']})")
    for s in r["structures"]:
        base = s.split(" ")[0]
        st = structures.get(base, {})
        lines.append(f"  structure: {s} type={st.get('type')} biomes={st.get('biomes')} set={set_of.get(base)}")
    for t in list(r["templates"])[:12]:
        lines.append(f"  template: {t}")
    if r["code"]:
        lines.append(f"  code: {r['code'][:12]}")
    for s in r["structureSpawns"][:8]:
        lines.append(f"  structure spawn: {s}")
    if r["biomeSpawns"]:
        lines.append(f"  biome spawns ({len(r['biomeSpawns'])}): {r['biomeSpawns'][:6]}")
    for a in r["advancements"][:8]:
        lines.append(f"  advancement: {a}")

lines.append("\n# Structures of boss mods (all)")
for sid in sorted(structures):
    if sid.split(":")[0] in boss_mods or any(sid in rr["structures"] for rr in report.values()):
        st = structures[sid]
        lines.append(f"  {sid}: type={st['type']} biomes={st['biomes']} set={set_of.get(sid)} reached={st.get('templates_reached')}")
lines.append("\n# Advancements naming a structure (boss mods)")
for a in adv_rows:
    if a["structures"] and a["id"].split(":")[0] in boss_mods | {"minecraft"}:
        lines.append(f"  {a['id']} [{lang.get(a['title'], a['title'])}]: {a['structures']}")
lines.append("\n# Exploration maps in loot")
for mp in maps:
    lines.append(f"  {mp['table']} -> {mp['destinations']} ({mp['jar']})")
lines.append("\n# Structure tags of boss mods")
for t, v in sorted(struct_tags.items()):
    if t.split(":")[0] in boss_mods:
        lines.append(f"  {t}: {v[:12]}")
lines.append("\n# Items that look like finders or summons (boss mods)")
lines.extend("  " + i for i in sorted(items_of_note))
lines.append("\n# Templates holding a boss that no jigsaw structure reaches (code-built lairs)")
reached_any = set()
for sid, s in structures.items():
    pass
for t, v in sorted(templates.items()):
    if v.get("bosses"):
        lines.append(f"  {t}: {v['bosses']}")
lines.append("\n# Classes naming a boss in structure-ish code")
for c in code_rows[:300]:
    lines.append(f"  {c['class']}: {c['bosses']}")

open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
json.dump({b: {**r, "templates": sorted(r["templates"])} for b, r in report.items()}, open(os.path.join(OUT, "bosses.json"), "w"), indent=1)
json.dump({"structures": structures, "sets": sets, "struct_tags": struct_tags, "maps": maps, "advancements": adv_rows}, open(os.path.join(OUT, "world.json"), "w"), indent=1, default=list)
print("\n".join(lines[:400]))
