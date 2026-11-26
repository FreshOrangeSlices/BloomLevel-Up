"""How Jewelry and Relics are got: their recipes, loot, trades and config files.

Was: accessory mods for the empty slots: Artifacts, Jewelry, Archers, Relics, Wizards.

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


def recipe_line(j):
    t = j.get("type", "")
    res = j.get("result", {})
    res = res if isinstance(res, str) else (res.get("item", "") + (" x" + str(res.get("count")) if res.get("count") else ""))
    if "pattern" in j:
        keys = {k: (v.get("item") or ("#" + v.get("tag", "")) if isinstance(v, dict) else str(v)) for k, v in j.get("key", {}).items()}
        return f"{t} {res} <= {' / '.join(j['pattern'])} where " + ", ".join(f"{k}={v}" for k, v in keys.items())
    ings = j.get("ingredients") or [j.get("ingredient"), j.get("base"), j.get("addition"), j.get("template")]
    flat_ings = []
    for i in ings:
        if isinstance(i, dict):
            flat_ings.append(i.get("item") or ("#" + i.get("tag", "")))
        elif isinstance(i, list):
            flat_ings.append("|".join((x.get("item") or "#" + x.get("tag", "")) for x in i if isinstance(x, dict)))
    return f"{t} {res} <= " + ", ".join(x for x in flat_ings if x)


for slugs, query in [(["jewelry"], "jewelry rpg series"), (["relics-rpg", "relics"], "relics rpg series")]:
    slug, v = find(slugs, query)
    p(f"######## {slug} {v['version_number'] if v else 'NOT FOUND'}")
    if not v:
        continue
    f = next(x for x in v["files"] if x["primary"])
    open("m.jar", "wb").write(get(f["url"]))
    z = zipfile.ZipFile("m.jar")
    names = sorted(z.namelist())
    p("   ---- recipes")
    for n in names:
        if re.match(r"data/[^/]+/recipes?/.+\.json$", n):
            try:
                p("   " + n.split("/")[-1][:-5] + ": " + recipe_line(json.loads(z.read(n))))
            except Exception as e:
                p(f"   {n}: <{e}> " + flat(z.read(n).decode("utf-8", "replace"))[:300])
    p("   ---- other data and config files")
    for n in names:
        if n.endswith(".json") and not re.search(r"/recipes?/|/models?/|/blockstates/|/lang/|/advancements?/|/textures?/|/sounds|/particles/|/tags/blocks/|/animations/", n):
            p(f"   {n}: {flat(z.read(n).decode('utf-8', 'replace'))[:900]}")
    p("   ---- code mentioning loot, trades or villagers (decompiled, matching lines)")
    hits = []
    for n in names:
        if n.endswith(".class"):
            b = z.read(n)
            if re.search(rb"(?i)loot|trade|villager|profession|chests/", b):
                hits.append(n[:-6].replace("/", "."))
    for cls in hits[:40]:
        try:
            r = subprocess.run(["java", "-jar", "cfr.jar", "m.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True, timeout=300)
        except Exception:
            continue
        lines = [flat(l) for l in r.stdout.splitlines() if re.search(r'(?i)chests/|minecraft:|weight|chance|tier|price|emerald|level|trade|offer', l)]
        if lines:
            p(f"   -- {cls}")
            for l in lines[:80]:
                p("      " + l[:260])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/accessory_sources.txt", "w").write("\n".join(out))
open("report.txt", "w").write("\n".join(out))
