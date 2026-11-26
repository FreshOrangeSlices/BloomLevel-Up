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
for slug in ("spell-power", "spell-engine", "irons-spells-n-spellbooks", "paladins-and-priests", "curios"):
    urls = jars[slug]
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    paths[slug] = slug + ".jar"
    open(paths[slug], "wb").write(data)
for slug in ("wizards", "jewelry", "relics-rpg"):
    ver, data, deps = modrinth(slug)
    paths[slug] = slug + ".jar"
    open(paths[slug], "wb").write(data)
    p(f"{slug} {ver} needs {deps}")


def strings_with(path, word_re, limit=40):
    z = zipfile.ZipFile(path)
    hits = []
    for n in z.namelist():
        if n.endswith(".class") and re.search(word_re.encode(), z.read(n)):
            hits.append(n)
    return hits[:limit]


# Spell Power: attribute definitions and the damage formula.
p("\n==== Spell Power: attributes")
z = zipfile.ZipFile(paths["spell-power"])
for n in z.namelist():
    if n.endswith(".class") and re.search(r"(?i)(SpellSchools|SpellPower|SpellAttribute|SpellPowerMechanics|SpellSchool)\.class$", n):
        text = decompile(paths["spell-power"], n[:-6].replace("/", "."))
        for m in re.finditer(r"(?i)(new \w*Attribute\w*\(|RangedAttribute|createAttribute|baseValue|defaultValue|public static \w+ getSpellPower|double value|randomValue|criticalChance|return .*\* )", text):
            p(f"  {n.rsplit('/', 1)[-1]}: " + " ".join(text[max(0, m.start() - 120):m.start() + 260].split()))
lang_names = [n for n in z.namelist() if n.endswith("lang/en_us.json")]
for ln in lang_names:
    for k, v in json.loads(z.read(ln)).items():
        if k.startswith("attribute"):
            p(f"  lang {k} = {v}")

# Iron's Spells: attribute registry.
p("\n==== Iron's Spells: attributes")
text = decompile(paths["irons-spells-n-spellbooks"], "io.redspace.ironsspellbooks.api.registry.AttributeRegistry")
for m in re.finditer(r"register\(\"([a-z_]+)\"[^;]*;", text):
    p("  " + " ".join(m.group(0).split())[:260])
if not text.strip():
    p("  (AttributeRegistry not found at that name)")

# Iron's armor: the attributes each set gives.
p("\n==== Iron's armor")
z = zipfile.ZipFile(paths["irons-spells-n-spellbooks"])
for n in sorted(z.namelist()):
    if n.endswith(".class") and "/item/armor/" in n and "$" not in n and re.search(r"(?i)Materials?\.class$|ArmorMaterial", n):
        text = decompile(paths["irons-spells-n-spellbooks"], n[:-6].replace("/", "."))
        for line in text.splitlines():
            if re.search(r"(?i)SPELL_POWER|MAX_MANA|MANA_REGEN|COOLDOWN|CAST_TIME|SPELL_RESIST|AttributeModifier", line):
                p(f"  {n.rsplit('/', 1)[-1][:-6]}: " + " ".join(line.split())[:240])

# Wizards, Jewelry, Paladins: their gear's attributes (data files first, then code).
for slug in ("wizards", "jewelry", "relics-rpg", "paladins-and-priests"):
    p(f"\n==== {slug}: gear attributes")
    z = zipfile.ZipFile(paths[slug])
    shown = 0
    for n in sorted(z.namelist()):
        if n.endswith(".json") and not n.startswith("assets/"):
            b = z.read(n)
            if b"spell_power:" in b or b"attribute" in b.lower():
                p(f"  {n}: " + " ".join(b.decode("utf-8", "replace").split())[:900])
                shown += 1
                if shown > 25:
                    break
    for n in sorted(z.namelist()):
        if n.endswith(".class") and re.search(r"(?i)(Armors|Weapons|Group|Items|Jewel|Gems|Config)\w*\.class$", n) and "$" not in n:
            b = z.read(n)
            if b"spell_power" not in b and b"SpellSchools" not in b and b"EntityAttributes" not in b and b"Attributes" not in b:
                continue
            text = decompile(paths[slug], n[:-6].replace("/", "."))
            lines = [" ".join(l.split()) for l in text.splitlines() if re.search(r"(?i)spell_power|SpellSchools|AttributeModifier|new ItemConfig\.Attribute|Attribute\.bonus|multiply|0\.\d+[fF]?\)", l)]
            if lines:
                p(f"  -- {n.rsplit('/', 1)[-1][:-6]}")
                for l in lines[:40]:
                    p("     " + l[:230])


# ---- more for the magic ceiling
IR = paths["irons-spells-n-spellbooks"]
zi = zipfile.ZipFile(IR)
p("\n==== Iron's: item registry lines with attributes (spellbooks, curios, staffs)")
text = decompile(IR, "io.redspace.ironsspellbooks.registries.ItemRegistry")
for l in text.splitlines():
    if re.search(r"(?i)AttributeRegistry|Attributes\.|SpellBook|Curio|Ring|Amulet|Necklace|Staff|Talisman", l):
        p("  " + " ".join(l.split())[:300])
p("\n==== Iron's: curio and spellbook item classes (attribute lines)")
for n in sorted(zi.namelist()):
    if n.endswith(".class") and "$" not in n and re.search(r"/item/(curios|spell_books?|weapons|SpellBook|UniqueSpellBook|SimpleAttribute)", n):
        t = decompile(IR, n[:-6].replace("/", "."))
        lines = [" ".join(l.split()) for l in t.splitlines() if re.search(r"(?i)AttributeModifier|AttributeRegistry\.|Attributes\.|put\(", l)]
        if lines:
            p(f"  -- {n.rsplit('/', 1)[-1][:-6]}")
            for l in lines[:20]:
                p("     " + l[:240])
p("\n==== Iron's: server config (upgrades, damage)")
for cls in ("io.redspace.ironsspellbooks.config.ServerConfigs",):
    t = decompile(IR, cls)
    for l in t.splitlines():
        if re.search(r"(?i)upgrade|define|comment", l):
            p("  " + " ".join(l.split())[:260])
p("\n==== Iron's: upgrade orb types")
for n in sorted(zi.namelist()):
    if n.endswith(".class") and re.search(r"(?i)UpgradeType|UpgradeTypes|UpgradeOrb", n) and "$" not in n:
        t = decompile(IR, n[:-6].replace("/", "."))
        for l in t.splitlines():
            if re.search(r"(?i)new |0\.\d|AttributeRegistry|Operation", l):
                p(f"  {n.rsplit('/', 1)[-1][:-6]}: " + " ".join(l.split())[:240])
p("\n==== Iron's: every spell's numbers (base spell power, per level, max level, cooldown, cast time, mana) and its damage line")
rows = []
for n in sorted(zi.namelist()):
    if n.endswith("Spell.class") and "/spells/" in n and "$" not in n and "Abstract" not in n:
        t = decompile(IR, n[:-6].replace("/", "."))
        def grab(rx):
            m = re.search(rx, t)
            return m.group(1) if m else "?"
        dmg = [" ".join(l.split()) for l in t.splitlines() if re.search(r"(?i)getDamage|getSpellPower\(|damage\s*=|DamageSources\.applyDamage", l)]
        rows.append(f"  {n.rsplit('/', 1)[-1][:-6]}: base {grab(r'baseSpellPower\s*=\s*([0-9.]+)')} +{grab(r'spellPowerPerLevel\s*=\s*([0-9.]+)')}/lvl, max lvl {grab(r'setMaxLevel\((\d+)')}, cd {grab(r'setCooldownSeconds\(([0-9.]+)')} s, cast {grab(r'castTime\s*=\s*(\d+)')} t, mana {grab(r'baseManaCost\s*=\s*(\d+)')}+{grab(r'manaCostPerLevel\s*=\s*(\d+)')}/lvl, school {grab(r'SchoolRegistry\.([A-Z_]+)')} :: " + " || ".join(dmg)[:500])
for r in rows:
    p(r)
p("\n==== Spell Power: critical, haste and school attribute classes")
SP = paths["spell-power"]
zs = zipfile.ZipFile(SP)
for n in sorted(zs.namelist()):
    if n.endswith(".class") and re.search(r"(?i)(SpellPowerMechanics|SpellSchools|SpellPower|SpellStatusEffect|Attributes?)\.class$", n):
        t = decompile(SP, n[:-6].replace("/", "."))
        for l in t.splitlines():
            if re.search(r"(?i)critical|haste|new .*Attribute|defaultValue|baseValue|clamp|Math\.|return .*\*", l):
                p(f"  {n.rsplit('/', 1)[-1][:-6]}: " + " ".join(l.split())[:260])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/spellmax.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
