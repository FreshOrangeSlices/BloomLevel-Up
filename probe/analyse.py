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
def jar(slug):
    urls = jars[slug]
    for u in (urls if isinstance(urls, list) else [urls]):
        d = get(u)
        if d:
            return d
for slug, path in (("curios", "cu.jar"), ("l_enders-cataclysm", "ca.jar"), ("cf:oddities-1.0.1-forge-1.20.1.jar", "od.jar"), ("bossesrise", "br.jar"),
                   ("borninchaos", "bic.jar"), ("cf:skarrier_mobs-1.1.2-forge-1.20.1.jar", "sk.jar")):
    open(path, "wb").write(jar(slug))
def full(path, cls, limit=4000):
    t = decompile(path, cls)
    p(f"\n######## {cls} ({len(t)} chars)")
    p(t[:limit])
full("cu.jar", "top.theillusivec4.curios.common.data.CuriosSlotManager", 9000)
for n in zipfile.ZipFile("cu.jar").namelist():
    if re.search(r"(?i)config/CuriosConfig\.class$|CuriosConfig\.class$", n):
        full("cu.jar", n[:-6].replace("/", "."), 6000)
full("cu.jar", "top.theillusivec4.curios.common.slottype.SlotType$Builder", 4000)
full("ca.jar", "com.github.L_Ender.cataclysm.items.CuriosItem.CuriosItem", 5000)
z = zipfile.ZipFile("ca.jar")
for n in z.namelist():
    if re.search(r"(?i)belt_of|Belt", n) and n.endswith(".class") and "render" not in n.lower():
        full("ca.jar", n[:-6].replace("/", "."), 2500)
full("ca.jar", "com.github.L_Ender.cataclysm.items.Monstrous_Helm", 5000)
for path, rx in (("od.jar", r"(?i)goggle"), ("br.jar", r"(?i)dragonskull|DragonSkull"), ("bic.jar", r"(?i)lordpumpkinheadshat|LordPumpkinheadsHat"), ("sk.jar", r"(?i)infernalcrown|InfernalCrown")):
    z = zipfile.ZipFile(path)
    for n in z.namelist():
        if re.search(rx, n.rsplit("/", 1)[-1]) and n.endswith(".class") and "$" not in n and "render" not in n.lower() and "model" not in n.lower():
            full(path, n[:-6].replace("/", "."), 3500)
    # procedures/events that read the head slot for these items
    for n in z.namelist():
        if n.endswith(".class") and re.search(r"(?i)procedure|event|handler", n):
            b = z.read(n)
            if re.search(rb"(?i)goggle|DRAGON_SKULL|LORD_PUMPKINHEADS_HAT|INFERNAL_CROWN", b):
                full(path, n[:-6].replace("/", "."), 2500)

os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk3b.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
