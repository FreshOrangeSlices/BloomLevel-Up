"""Chunk 2 facts: attribute ids, damage types, the Curios attribute event, Fabled Weaponry tomes and maces.

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
paths = {}
for slug in ("spell-power", "spell-engine", "irons-spells-n-spellbooks", "curios", "cf:fabledweaponry-1.20.1-1.0.7.5.jar", "paladins-and-priests"):
    urls = jars[slug]
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    paths[slug] = re.sub(r"[^a-z0-9]+", "_", slug) + ".jar"
    open(paths[slug], "wb").write(data)
for slug in ("wizards",):
    ver, data, deps = modrinth(slug)
    paths[slug] = slug + ".jar"
    open(paths[slug], "wb").write(data)
    p(f"{slug} {ver} needs {deps}")


def lines(path, cls, rx, limit=120):
    t = decompile(path, cls)
    if not t.strip():
        p(f"  ({cls} not found)")
        return t
    got = [" ".join(l.split()) for l in t.splitlines() if re.search(rx, l)]
    p(f"-- {cls} ({len(got)})")
    for l in got[:limit]:
        p("   " + l[:400])
    return t


def classes(path, rx):
    z = zipfile.ZipFile(path)
    return [n[:-6].replace("/", ".") for n in z.namelist() if n.endswith(".class") and re.search(rx, n)]


SP, SE, IR, CU, FW = paths["spell-power"], paths["spell-engine"], paths["irons-spells-n-spellbooks"], paths["curios"], paths["cf:fabledweaponry-1.20.1-1.0.7.5.jar"]
p("\n==== Spell Power: how school attributes and their ids are made")
for cls in classes(SP, r"(SpellSchools|SpellSchool|SpellPowerMechanics|SpellResistance|SpellPowerMod)\.class$"):
    lines(SP, cls, r'ResourceLocation\(|"spell_power"|createMagic\(|entry\("|resistance|register|DEFAULT_NAMESPACE|GENERIC', 60)
p("\n==== Spell Power and Spell Engine: damage types")
for path in (SP, SE):
    z = zipfile.ZipFile(path)
    for n in sorted(z.namelist()):
        if "damage_type" in n and n.endswith(".json"):
            p(f"   {n}: " + " ".join(z.read(n).decode().split())[:200])
for cls in classes(SP, r"(Damage|SpellDamage)\w*\.class$") + classes(SE, r"(SpellDamage|DamageSource|SpellHelper)\w*\.class$"):
    lines(SP if cls.startswith("net.spell_power") else SE, cls, r"damageSource|DamageSource|ResourceKey|DAMAGE_TYPE|damage_type|indirectMagic|magic\(", 40)
p("\n==== Iron's: attribute ids and damage types")
lines(IR, "io.redspace.ironsspellbooks.api.registry.AttributeRegistry", r'register|"[a-z_]+_spell_power"|newResistanceAttribute|newPowerAttribute', 60)
z = zipfile.ZipFile(IR)
for n in sorted(z.namelist()):
    if "damage_type" in n and n.endswith(".json") and "tags" not in n:
        p(f"   {n.split('/')[-1]}")
    if "/tags/damage_type/" in n:
        p(f"   tag {n}: " + " ".join(z.read(n).decode().split())[:400])
for cls in classes(IR, r"(ISSDamageTypes|DamageSources|SpellDamageSource)\.class$"):
    lines(IR, cls, r"ResourceKey|register|create|DamageSource|getEntity|getDirectEntity", 60)
p("\n==== Curios: the attribute event")
for cls in classes(CU, r"(CurioAttributeModifierEvent|CuriosApi|ICuriosItemHandler|SlotResult)\.class$"):
    lines(CU, cls, r"public .*\(|record |class ", 80)
p("\n==== Fabled Weaponry: tomes and maces")
z = zipfile.ZipFile(FW)
for n in sorted(z.namelist()):
    if n.endswith("lang/en_us.json"):
        lang = json.loads(z.read(n).decode("utf-8", "replace"))
        p("   lang: " + "; ".join(f"{k}={v}" for k, v in lang.items() if re.search(r"(?i)tome|mace", k))[:3000])
for cls in classes(FW, r"(?i)(tome|mace|ItemInit|ModItems|Items)\w*\.class$")[:25]:
    lines(FW, cls, r"(?i)magic|indirectMagic|damageSources|hurt\(|getOffhandItem|OFF_HAND|tome|register\(|\"[a-z_]+_tome\"|setLastHurtByPlayer|getAttackStrength", 50)
p("\n==== Paladins: one spell file (for the shape of a Spell Engine spell)")
z = zipfile.ZipFile(paths["paladins-and-priests"])
for n in sorted(z.namelist()):
    if "/spell/" in n and n.endswith(".json"):
        p("   " + n + ": " + " ".join(z.read(n).decode().split())[:700])
        break
p("\n==== Wizards: spell files' cost blocks")
z = zipfile.ZipFile(paths["wizards"])
for n in sorted(z.namelist()):
    if "/spell/" in n and n.endswith(".json"):
        j = json.loads(z.read(n))
        p(f"   {n.split('/')[-1]}: cost {json.dumps(j.get('cost'))[:200]} damage-ish {json.dumps([i.get('action', {}).get('damage') for i in j.get('impacts', [])])[:160]}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk2.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
