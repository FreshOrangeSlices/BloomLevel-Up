"""Chunk 4 facts, round 5: the Undying Tentacle, and Artifacts and Relics mixins.

Was round 4: Spell Engine's item attribute mixin.

Was round 3: how Wizards armor gets its spell bonuses.

Was round 2: where the altar and staff pieces sit, the champion's config defaults.

Was chunk 4 facts: The Graveyard's altar, staff, vial and dagger (the Corrupted Champion).

Was chunk 3 facts, round 6: Artifacts' loot modifier format.

Was round 5: where Artifacts are injected, and Jewelry's gems, recipes, ores and jeweler.

Was round 4: Artifacts' and Relics' slots and Artifacts' rule names.

Was chunk 3 facts: trophy pieces, Curios slot merging, Cataclysm belts, Artifacts game rule names, modded chests.

Was chunk 2 facts: attribute ids, damage types, the Curios attribute event, Fabled Weaponry tomes and maces.

Was: the magic ceiling, and spell power on both sides: how Spell Power and Iron's Spells define their stats, and what the gear gives.

Spell Power and Iron's Spells: attribute definitions (base, min, max) and how spell damage reads them. Gear: every
attribute modifier on Wizards', Jewelry's, Paladins' and Iron's armor, jewelry and weapons, from their own data or code.
"""
import io, json, os, re, subprocess, urllib.parse, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (spell power check)"}


def get(url):
    for _ in range(3):
        try:
            return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
        except Exception:
            pass
    return None


def modrinth(slug):
    res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
    v = json.loads(res)[0]
    f = next(x for x in v["files"] if x["primary"])
    return v["version_number"], get(f["url"]), [d.get("project_id") for d in v["dependencies"] if d["dependency_type"] == "required"]


def decompile(path, cls):
    r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    return r.stdout


out = []
p = out.append
jars = json.load(open("probe/alljars.json"))
key = next(k for k in jars if "bossesrise" in k or "bosses-rise" in k or "block_factorys" in k)
urls = jars[key]
data = None
for u in (urls if isinstance(urls, list) else [urls]):
    data = get(u)
    if data:
        break
open("b.jar", "wb").write(data)
z = zipfile.ZipFile("b.jar")
cls = [n[:-6].replace("/", ".") for n in z.namelist() if n.endswith(".class") and re.search(r"(UndyingTentacle|GhostTentacle)[^/]*\.class$", n)]
p(f"==== {key}: {cls}")
for c in cls:
    if "Item" in c or "Procedure" in c:
        t = decompile("b.jar", c)
        p(f"\n==== {c}")
        for l in t.splitlines():
            ls = l.strip()
            if ls and not ls.startswith(("import", "*", "/*", "package")):
                p("   " + " ".join(ls.split())[:260])
for n in z.namelist():
    if re.search(r"procedures/.*Tentacle.*\.class$", n, re.I):
        c = n[:-6].replace("/", ".")
        t = decompile("b.jar", c)
        p(f"\n==== {c}")
        for l in t.splitlines():
            ls = l.strip()
            if ls and not ls.startswith(("import", "*", "/*", "package")):
                p("   " + " ".join(ls.split())[:260])
for slug in ("artifacts", "relics-rpg"):
    ver, data, deps = modrinth(slug)
    open("a.jar", "wb").write(data)
    z2 = zipfile.ZipFile("a.jar")
    p(f"\n==== {slug} {ver} mixins")
    for n in z2.namelist():
        if n.endswith("mixins.json"):
            p(f"   {n}: " + " ".join(z2.read(n).decode(errors='replace').split())[:2500])
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk4e.txt", "w").write("\n".join(out)[:300000])
open("report.txt", "w").write("done")
