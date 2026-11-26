"""Accessory mods for the empty slots: Artifacts, Jewelry, Archers, Relics, Wizards.

For each: the Curios slots it defines and gives players, the items each slot tag takes (Curios' own and Spell Engine's).
For Artifacts also: every item's tooltip text, its game rules (the knobs and their defaults), where it is found
(loot modifiers and its own loot tables) and the mobs that wear one.
"""
import io, json, os, re, subprocess, urllib.parse, urllib.request, zipfile
from collections import defaultdict

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (accessory mods)"}


def get(url):
    for _ in range(3):
        try:
            return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
        except Exception:
            pass
    return None


def versions(slug):
    res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]')
              + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
    try:
        return json.loads(res) if res else []
    except Exception:
        return []


def find(slugs, query):
    for slug in slugs:
        v = versions(slug)
        if v:
            return slug, v[0]
    res = get("https://api.modrinth.com/v2/search?limit=10&query=" + urllib.parse.quote(query) + "&facets="
              + urllib.parse.quote('[["versions:1.20.1"],["categories:forge"]]'))
    hits = json.loads(res)["hits"] if res else []
    for h in hits:
        v = versions(h["slug"])
        if v:
            return h["slug"] + " (search: " + h["title"] + " by " + h["author"] + ")", v[0]
    return None, None


def flat(text):
    return " ".join(str(text).split())


out = []
p = out.append
mods = [
    (["artifacts"], "artifacts ochotonida"),
    (["jewelry"], "jewelry rpg series"),
    (["archers"], "archers rpg series"),
    (["relics-rpg", "relics", "rpg-series-relics"], "relics rpg series"),
    (["wizards"], "wizards rpg series"),
]
for slugs, query in mods:
    try:
        slug, v = find(slugs, query)
    except Exception as e:
        p(f"######## {slugs[0]}: lookup failed {e}")
        continue
    p(f"######## {slugs[0]}: {slug} {v['version_number'] if v else 'NOT FOUND'}")
    if not v:
        continue
    f = next(x for x in v["files"] if x["primary"])
    p(f"   file {f['filename']}; required: {[d.get('project_id') for d in v['dependencies'] if d['dependency_type'] == 'required']}")
    data = get(f["url"])
    open("m.jar", "wb").write(data)
    z = zipfile.ZipFile("m.jar")
    names = z.namelist()
    for n in sorted(names):
        if re.match(r"data/[^/]+/curios/(slots|entities)/.+\.json$", n):
            p(f"   {n}: {flat(z.read(n).decode('utf-8', 'replace'))[:400]}")
    for n in sorted(names):
        m = re.match(r"data/([^/]+)/tags/items/(.+)\.json$", n)
        if m and (m.group(1) in ("curios", "spell_engine", "trinkets") or "trinket" in m.group(2) or "quiver" in m.group(2)):
            try:
                vals = [x if isinstance(x, str) else x.get("id", str(x)) for x in json.loads(z.read(n)).get("values", [])]
            except Exception as e:
                vals = [f"<{e}>"]
            p(f"   tag #{m.group(1)}:{m.group(2)} ({len(vals)}): {', '.join(vals)}")
    slotnames = set()
    for n in names:
        if n.endswith(".class"):
            b = z.read(n)
            for s in re.findall(rb"spell_(?:trinket|quiver|book)|curios:[a-z_]+", b):
                slotnames.add(s.decode())
    if slotnames:
        p("   slot names in code: " + ", ".join(sorted(slotnames)))
    if slugs[0] != "artifacts":
        langs = [n for n in names if n.endswith("lang/en_us.json")]
        for n in langs[:1]:
            try:
                lang = json.loads(z.read(n).decode("utf-8", "replace"))
            except Exception:
                continue
            items = [f"{k.split('.')[-1]}={val}" for k, val in lang.items() if k.startswith("item.") and k.count(".") == 2]
            p(f"   items ({len(items)}): " + "; ".join(items)[:3000])
        continue
    # ---- Artifacts in depth
    lang = {}
    for n in names:
        if n.endswith("lang/en_us.json") and n.startswith("assets/artifacts/"):
            lang = json.loads(z.read(n).decode("utf-8", "replace"))
    items = sorted({k.split(".")[2] for k in lang if k.startswith("item.artifacts.") and k.count(".") == 2})
    p(f"   ---- {len(items)} items, with their tooltips")
    for it in items:
        tips = [flat(val) for k, val in lang.items() if (it in k) and ("tooltip" in k)]
        p(f"   {it} = {lang.get('item.artifacts.' + it)} :: " + " | ".join(tips)[:500])
    other = [f"{k}={flat(val)}" for k, val in lang.items() if "tooltip" not in k and not k.startswith("item.artifacts.")]
    p("   ---- other lang lines: " + " || ".join(other)[:3000])
    p("   ---- game rules and config (decompiled)")
    for n in sorted(names):
        if n.endswith(".class") and re.search(r"(?i)gamerule|config", n) and "$" not in n:
            cls = n[:-6].replace("/", ".")
            try:
                r = subprocess.run(["java", "-jar", "cfr.jar", "m.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True, timeout=300)
            except Exception:
                continue
            lines = [flat(l) for l in r.stdout.splitlines() if re.search(r'"[a-z][A-Za-z0-9_.]+"\s*,\s*-?[0-9.]+|create(Boolean|Integer|Double|Float)|Value\(|define\(|\.comment\(', l)]
            if lines:
                p(f"   -- {cls} ({len(lines)} lines)")
                for l in lines[:400]:
                    p("      " + l[:240])
    p("   ---- loot (modifiers, tables)")
    for n in sorted(names):
        if n.startswith("data/") and ("loot_modifiers" in n or "global_loot" in n or "/loot_tables/" in n) and n.endswith(".json"):
            p(f"   {n}: {flat(z.read(n).decode('utf-8', 'replace'))[:700]}")
    for n in sorted(names):
        if n.startswith("data/") and "/tags/" in n and re.search(r"(?i)entity|wear|equip|archaeology|suspicious", n) and n.endswith(".json"):
            p(f"   {n}: {flat(z.read(n).decode('utf-8', 'replace'))[:500]}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/accessories.txt", "w").write("\n".join(out))
open("report.txt", "w").write("\n".join(out))
