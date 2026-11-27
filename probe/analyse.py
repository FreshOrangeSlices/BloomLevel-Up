"""JEI audit, round 1: every locked mod against its newest Forge 1.20.1 build on Modrinth.

Was chunk 4 facts, round 5: the Undying Tentacle, and Artifacts and Relics mixins.

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
lock = json.load(open("probe/lock.json"))["versions"]
pins = json.load(open("probe/pins.json"))
newer, same, missing = [], [], []
for slug, have in sorted(lock.items()):
    try:
        res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
        vs = json.loads(res)
    except Exception as e:
        missing.append(f"{slug} ({e.__class__.__name__})")
        continue
    if not vs:
        missing.append(f"{slug} (no Forge 1.20.1 build listed)")
        continue
    top = vs[0]
    names = [v["version_number"] for v in vs]
    if top["version_number"] == have:
        same.append(slug)
    else:
        behind = names.index(have) if have in names else "?"
        held = f" HELD at {pins[slug]}" if slug in pins else ""
        newer.append(f"{slug}: have {have}, newest {top['version_number']} ({top['date_published'][:10]}, {top['version_type']}), {behind} release(s) behind{held}")
    time.sleep(0.25)
p(f"==== {len(lock)} locked projects: {len(same)} on their newest Forge 1.20.1 build, {len(newer)} with a newer one, {len(missing)} not checked")
p("\n==== Newer builds")
for l in newer:
    p("   " + l)
p("\n==== Not checked")
for l in missing:
    p("   " + l)
os.makedirs("probe-out", exist_ok=True)
open("probe-out/jei1.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
