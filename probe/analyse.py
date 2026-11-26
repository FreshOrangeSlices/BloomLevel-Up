"""Chunk 4 facts: The Graveyard's altar, staff, vial and dagger (the Corrupted Champion).

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
urls = jars["the-graveyard-forge"]
data = None
for u in (urls if isinstance(urls, list) else [urls]):
    data = get(u)
    if data:
        break
open("g.jar", "wb").write(data)
z = zipfile.ZipFile("g.jar")
names = z.namelist()
p("==== The Graveyard: lang lines about the altar, staff, vial, dagger, lich")
for n in names:
    if n.endswith("lang/en_us.json"):
        lang = json.loads(z.read(n))
        for k, v in lang.items():
            if re.search(r"altar|staff|vial|dagger|lich|blood|champion|ominous|fragment|sacrific|corrupt", k + v, re.I):
                p(f"   {k}: {v}")
p("\n==== Files naming the staff fragments, the vial or the dagger")
for n in sorted(names):
    if n.endswith(".json") and n.startswith("data/"):
        body = z.read(n).decode(errors="replace")
        if re.search(r"bone_staff|vial_of_blood|bone_dagger|ossuary|lich", body):
            p(f"   {n}: " + " ".join(body.split())[:700])
p("\n==== Structures and where they go")
for n in sorted(names):
    if re.search(r"data/graveyard/(worldgen/structure/|tags/worldgen/biome/has_structure/)", n):
        p(f"   {n}: " + " ".join(z.read(n).decode(errors="replace").split())[:400])
classes = [n[:-6].replace("/", ".") for n in names if n.endswith(".class") and re.search(r"(Altar|BoneStaff|Vial|Dagger|Ossuary|LichEntity|Sacrific|Summon)", n.split("/")[-1])]
p("\n   classes: " + ", ".join(classes)[:1500])
for c in classes:
    t = decompile("g.jar", c)
    p(f"\n==== {c}")
    for l in t.splitlines():
        ls = l.strip()
        if ls and not ls.startswith(("import", "*", "/*", "package")) and len(ls) > 3:
            p("   " + " ".join(ls.split())[:240])
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk4a.txt", "w").write("\n".join(out)[:600000])
open("report.txt", "w").write("done")
