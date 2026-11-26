"""Chunk 3 facts, round 6: Artifacts' loot modifier format.

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
ver, data, deps = modrinth("artifacts")
open("a.jar", "wb").write(data)
z = zipfile.ZipFile("a.jar")
p(f"==== artifacts {ver}")
for n in ["data/artifacts/loot_modifiers/inject/chests/abandoned_mineshaft.json", "data/artifacts/loot_tables/inject/chests/abandoned_mineshaft.json", "data/forge/loot_modifiers/global_loot_modifiers.json"]:
    try:
        p(f"   {n}: " + " ".join(z.read(n).decode().split())[:1500])
    except KeyError:
        p(f"   {n}: missing")
classes = [n[:-6].replace("/", ".") for n in z.namelist() if n.endswith(".class") and re.search(r"(LootModifier|Modifiers|LootConditions?|ArtifactRarity|Mimic|Campsite)", n.split("/")[-1])]
p("   classes: " + ", ".join(classes)[:1500])
for c in classes:
    t = decompile("a.jar", c)
    for l in t.splitlines():
        if re.search(r"fieldOf|register\(|\"[a-z_]+\"|Codec|CODEC", l) and len(l.strip()) > 8:
            p(f"   {c.split('.')[-1]}: " + " ".join(l.split())[:260])
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk3f.txt", "w").write("\n".join(out)[:200000])
open("report.txt", "w").write("done")
