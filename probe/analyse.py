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
NAMES = ["Mantis Goggles", "Ashlord", "Pumpkinhead's Hat", "Pumpkinhead Hat", "Monstrous Helm", "Infernal Crown", "Missionary Hat",
         "Spiritual Guide", "Nightmare Mantle", "Spiny Shell", "Quake Boots", "Crushing Pauldron", "Orchid Mantis Boots"]
chests = {}
found = []
for mod, urls in jars.items():
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    if not data:
        continue
    path = "j.jar"
    open(path, "wb").write(data)
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    names = z.namelist()
    cs = [n for n in names if re.match(r"data/[^/]+/loot_tables/chests/.+\.json$", n)]
    if cs:
        chests[mod] = cs
    hits = []
    for n in names:
        if n.endswith("lang/en_us.json"):
            try:
                lang = json.loads(z.read(n).decode("utf-8", "replace"))
            except Exception:
                continue
            for k, v in lang.items():
                if isinstance(v, str) and any(w.lower() in v.lower() for w in NAMES) and k.startswith("item."):
                    hits.append((k, v))
    if hits:
        p(f"######## {mod}: " + "; ".join(f"{k}={v}" for k, v in hits))
        keys = {k.split(".")[-1] for k, _ in hits}
        # classes that name those items or look like their item classes
        cls_hits = []
        for n in names:
            if not n.endswith(".class") or "$" in n:
                continue
            b = z.read(n)
            base = n.rsplit("/", 1)[-1][:-6].lower()
            if any(k.encode() in b for k in keys) and re.search(r"(?i)event|handler|item|armor|helmet|hat|goggle|skull|crown", n) \
                    or re.search(r"(?i)(goggle|ashlord|dragonskull|pumpkinheadhat|monstrous|infernalcrown|crown)", base):
                cls_hits.append(n[:-6].replace("/", "."))
        for cls in cls_hits[:14]:
            t = decompile(path, cls)
            got = [" ".join(l.split()) for l in t.splitlines() if re.search(r"(?i)getItemBySlot|EquipmentSlot\.HEAD|addEffect|MobEffect|Attribute|onArmorTick|inventoryTick|setSecondsOnFire|LivingHurtEvent|LivingAttackEvent|getAttackStrength|isOnFire|isInLava|getHealth\(\) <|knockback|REACH|attack_speed|AttributeModifier\(", l)]
            if got:
                p(f"   -- {cls} ({len(got)})")
                for l in got[:40]:
                    p("      " + l[:300])
    if mod == "curios":
        for n in names:
            if n.endswith(".class") and re.search(r"(?i)(SlotTypeManager|CuriosSlotManager|SlotTypeBuilder|CuriosEntityManager)\.class$", n):
                t = decompile(path, n[:-6].replace("/", "."))
                got = [" ".join(l.split()) for l in t.splitlines() if re.search(r'(?i)"size"|"replace"|"operation"|Math\.max|SET|ADD|REMOVE|sizeMod|this\.size', l)]
                p(f"   -- curios {n} ({len(got)})")
                for l in got[:50]:
                    p("      " + l[:300])
    if mod == "l_enders-cataclysm":
        for n in names:
            if n.endswith(".class") and re.search(r"(?i)(belt|curio)", n.rsplit("/", 1)[-1]) and "$" not in n:
                t = decompile(path, n[:-6].replace("/", "."))
                got = [" ".join(l.split()) for l in t.splitlines() if re.search(r'(?i)talisman|waist|addSlotModifier|identifier|slotContext', l)]
                if got:
                    p(f"   -- cataclysm {n} ({len(got)})")
                    for l in got[:20]:
                        p("      " + l[:300])
art_v = modrinth("artifacts")
open("a.jar", "wb").write(art_v[1])
p("\n==== Artifacts game rule names")
t = decompile("a.jar", "artifacts.registry.ModGameRules")
m = re.search(r"createName\([^)]*\)\s*\{[^}]*\}", t)
p("   " + (" ".join(m.group(0).split()) if m else "(createName not found)"))
for l in t.splitlines():
    if re.search(r"POWER_GLOVE|CRYSTAL_HEART|VAMPIRIC|CROSS_NECKLACE|BUNNY|register\(|GameRules\.register|m_46189_", l):
        p("   " + " ".join(l.split())[:300])
z = zipfile.ZipFile("a.jar")
for n in z.namelist():
    if "loot_tables/artifact" in n or n.endswith("loot_tables/artifact.json") or "/loot_tables/" in n and n.split("/")[-1] in ("artifact.json", "all.json"):
        p(f"   {n}: " + " ".join(z.read(n).decode().split())[:400])
p("\n==== Modded chest loot tables")
for mod, cs in sorted(chests.items()):
    p(f"   {mod} ({len(cs)}): " + ", ".join(c.split("/loot_tables/")[0].split("/")[1] + ":" + c.split("/loot_tables/")[1][:-5] for c in cs)[:1500])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/chunk3.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
