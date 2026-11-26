"""Chunk 3 facts: trophy pieces, Curios slot merging, Cataclysm belts, Artifacts game rule names, modded chests.

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
chests = {}
for mod, urls in jars.items():
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    if not data:
        continue
    open("j.jar", "wb").write(data)
    try:
        z = zipfile.ZipFile("j.jar")
    except Exception:
        continue
    cs = [n for n in z.namelist() if re.match(r"data/[^/]+/loot_tables/chests/.+\.json$", n)]
    if cs:
        chests[mod] = cs
art = modrinth("artifacts")
open("a.jar", "wb").write(art[1])
p("==== Artifacts game rule names")
t = decompile("a.jar", "artifacts.registry.ModGameRules")
for m in re.finditer(r"(private|public) static \w+ createName\([^)]*\)\s*\{.*?\n    \}", t, re.S):
    p("   " + " ".join(m.group(0).split())[:600])
for l in t.splitlines():
    if re.search(r"GameRules\.m_46189_|register|Key<|ResourceLocation|getPath|toString\(\)", l):
        p("   " + " ".join(l.split())[:300])
z = zipfile.ZipFile("a.jar")
for n in sorted(z.namelist()):
    if re.search(r"loot_tables/(artifact|all|inject/chests/village/village_armorer)\.json$", n) or n.endswith("/artifact.json"):
        p(f"   {n}: " + " ".join(z.read(n).decode().split())[:600])
p("\n==== Modded chest loot tables")
for mod, cs in sorted(chests.items()):
    p(f"   {mod} ({len(cs)}): " + ", ".join(c.split('/')[1] + ":" + c.split("/loot_tables/")[1][:-5] for c in cs)[:2500])
os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk3c.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
