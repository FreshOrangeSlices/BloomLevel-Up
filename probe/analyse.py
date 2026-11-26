"""Chunk 3 facts, round 4: Artifacts' and Relics' slots and Artifacts' rule names.

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
for slug in ("artifacts", "relics-rpg"):
    try:
        ver, data, deps = modrinth(slug)
    except Exception as e:
        p(f"==== {slug}: not found ({e})")
        continue
    open("a.jar", "wb").write(data)
    z = zipfile.ZipFile("a.jar")
    p(f"==== {slug} {ver} (deps {deps})")
    for n in sorted(z.namelist()):
        if re.search(r"data/[^/]+/(curios/|tags/items/)", n) and n.endswith(".json"):
            body = " ".join(z.read(n).decode(errors="replace").split())
            p(f"   {n}: {body[:900]}")
    if slug == "artifacts":
        t = decompile("a.jar", "artifacts.registry.ModGameRules")
        for l in t.splitlines():
            if re.search(r"(integer|boolean|double|float)Value\(|Value\(Mod|ModItems\.", l):
                p("   rule: " + " ".join(l.split())[:300])
    mods = [n for n in z.namelist() if n.endswith("mods.toml")]
    for n in mods:
        p("   " + n + ": " + " ".join(re.findall(r'modId\s*=\s*"([^"]+)"', z.read(n).decode())))
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk3d.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
