"""Chunk 4 facts, round 2: where the altar and staff pieces sit, the champion's config defaults.

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
import gzip
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
p("==== Structures holding the altar or a staff fragment (block names in each .nbt's palette)")
for n in sorted(names):
    if n.endswith(".nbt") and "/structures/" in n:
        try:
            raw = gzip.decompress(z.read(n))
        except Exception:
            raw = z.read(n)
        found = sorted(set(m.decode() for m in re.findall(rb"graveyard:(?:altar[a-z_]*|lower_bone_staff|middle_bone_staff|upper_bone_staff)", raw)))
        if found:
            p(f"   {n}: {found}")
p("\n==== Config defaults (the champion's entries)")
classes = [n[:-6].replace("/", ".") for n in names if n.endswith(".class") and re.search(r"(Config|ConfigEntries|ChampionConfig)", n.split("/")[-1])]
p("   classes: " + ", ".join(classes)[:1200])
for c in classes:
    t = decompile("g.jar", c)
    for l in t.splitlines():
        if re.search(r"corrupted_champion|BloodCollectable|BossSummonable|StaffFragments|villager|wandering|new CorruptedChampion|healthIn", l, re.I):
            p(f"   {c.split('.')[-1]}: " + " ".join(l.split())[:400])
p("\n==== Structure files with the prison, altar or ruins in their name")
for n in sorted(names):
    if re.search(r"(lich_prison|altar|ruins)", n) and (n.endswith(".json") or n.endswith(".nbt")):
        p(f"   {n}")
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk4b.txt", "w").write("\n".join(out)[:300000])
open("report.txt", "w").write("done")
