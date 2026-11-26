"""The magic ceiling, and spell power on both sides: how Spell Power and Iron's Spells define their stats, and what the gear gives.

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
for slug in ("spell-power", "spell-engine", "irons-spells-n-spellbooks", "paladins-and-priests", "rogues-and-warriors"):
    urls = jars.get(slug)
    if not urls:
        p(f"no jar for {slug}")
        continue
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    paths[slug] = slug + ".jar"
    open(paths[slug], "wb").write(data)
for slug in ("wizards", "relics-rpg"):
    ver, data, deps = modrinth(slug)
    paths[slug] = slug + ".jar"
    open(paths[slug], "wb").write(data)
    p(f"{slug} {ver}")


def full(path, cls, rx, label=None, limit=200):
    t = decompile(path, cls)
    if not t.strip():
        p(f"  ({cls} not found)")
        return t
    lines = [" ".join(l.split()) for l in t.splitlines() if re.search(rx, l)]
    p(f"-- {label or cls} ({len(lines)} lines)")
    for l in lines[:limit]:
        p("   " + l)
    return t


def method(path, cls, name, label=None):
    t = decompile(path, cls)
    m = re.search(r"\n([^\n]*\b" + re.escape(name) + r"\([^\n]*\{)", t)
    if not m:
        p(f"  ({cls}.{name} not found)")
        return
    i = m.start(1)
    depth, j = 0, t.index("{", i)
    k = j
    while k < len(t):
        if t[k] == "{":
            depth += 1
        elif t[k] == "}":
            depth -= 1
            if depth == 0:
                break
        k += 1
    p(f"-- {label or cls + '.' + name}: " + " ".join(t[i:k + 1].split())[:1500])


IR = paths["irons-spells-n-spellbooks"]
p("\n==== Iron's: how power reaches damage")
method(IR, "io.redspace.ironsspellbooks.api.util.Utils", "softCapFormula")
method(IR, "io.redspace.ironsspellbooks.api.spells.AbstractSpell", "getSpellPower")
method(IR, "io.redspace.ironsspellbooks.api.spells.AbstractSpell", "getEntityPowerMultiplier")
method(IR, "io.redspace.ironsspellbooks.api.spells.AbstractSpell", "getSpellDamage")
p("\n==== Iron's: armor sets (full lines)")
full(IR, "io.redspace.ironsspellbooks.item.armor.ExtendedArmorMaterials", r"^\s*[A-Z_]+\(\"|schoolArmorMap|makeArmorMap\(", limit=60)
p("\n==== Iron's: items with attributes (full lines)")
full(IR, "io.redspace.ironsspellbooks.registries.ItemRegistry", r"withSpellbookAttributes|withAttributes|AttributeContainer|SPELL_POWER|StaffItem|Staff\(", limit=120)
p("\n==== Iron's: upgrade orb data and the upgrade limit")
z = zipfile.ZipFile(IR)
for n in sorted(z.namelist()):
    if "upgrade_orb_type" in n and n.endswith(".json"):
        p("   " + n + ": " + " ".join(z.read(n).decode().split()))
full(IR, "io.redspace.ironsspellbooks.config.ServerConfigs", r"MAX_UPGRADES\s*=|define\(\"maxUpgrades|maxUpgrades|defineInRange\(\"max", limit=10)
for cls in ("io.redspace.ironsspellbooks.registries.UpgradeOrbTypeRegistry", "io.redspace.ironsspellbooks.item.UpgradeOrbItem"):
    full(IR, cls, r"0\.\d|AttributeRegistry\.|new UpgradeOrbType", limit=40)
p("\n==== Spell Power: base values and how a spell's number is made")
SP = paths["spell-power"]
zs = zipfile.ZipFile(SP)
for n in sorted(zs.namelist()):
    if n.endswith(".json") and ("config" in n or "attributes" in n):
        p("   " + n + ": " + " ".join(zs.read(n).decode("utf-8", "replace").split())[:1500])
for n in sorted(zs.namelist()):
    if n.endswith(".class") and re.search(r"(AttributesConfig|SpellPowerMechanics)\.class$", n):
        full(SP, n[:-6].replace("/", "."), r"=|entry\(|base_spell_power|float ", limit=60)
SE = paths["spell-engine"]
ze = zipfile.ZipFile(SE)
for n in sorted(ze.namelist()):
    if n.endswith(".class") and re.search(r"(SpellHelper|SpellPowerHelper|SpellDamage)\.class$", n):
        t = decompile(SE, n[:-6].replace("/", "."))
        for l in t.splitlines():
            if re.search(r"(?i)coefficient|spell_power_coefficient|randomValue|getSpellPower|amount\s*\*|\* power", l):
                p(f"   {n.rsplit('/', 1)[-1]}: " + " ".join(l.split())[:400])
p("\n==== Wizards: armor and weapons (full lines with numbers)")
WZ = paths["wizards"]
zw = zipfile.ZipFile(WZ)
for n in sorted(zw.namelist()):
    if n.endswith(".class") and "$" not in n and re.search(r"(Armors|Weapons|Group)\w*\.class$", n):
        full(WZ, n[:-6].replace("/", "."), r"\d\.\d|bonus|multiply|SpellSchools|Attribute\(", label=n.rsplit('/', 1)[-1], limit=120)
p("\n==== Relics: every relic's attributes (full lines)")
RL = paths["relics-rpg"]
full(RL, "net.relics_rpgs.item.RelicItems", r"new Entry\(|withAttributes|AttributeModifier\(|_multiplier|0\.\d+f", limit=200)
for slug in ("paladins-and-priests", "rogues-and-warriors"):
    if slug not in paths:
        continue
    p(f"\n==== {slug}: armor and weapons with spell power")
    zz = zipfile.ZipFile(paths[slug])
    for n in sorted(zz.namelist()):
        if n.endswith(".class") and "$" not in n and re.search(r"(Armors|Weapons|Group)\w*\.class$", n):
            full(paths[slug], n[:-6].replace("/", "."), r"SpellSchools|spell_power|bonus\(|multiply", label=n.rsplit('/', 1)[-1], limit=60)

os.makedirs("probe-out", exist_ok=True)
open("probe-out/spellmax2.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
