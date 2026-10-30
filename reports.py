"""Throwaway: the playtest report round. Usage: reports.py <server.zip> <out dir>
 dodge/     the three dodge mods: their roll/dash classes, HUD overlays and config keys (javap), gui textures
 pirates/   Bosses'Rise's pirate classes and the goals they use (javap)
 dragons/   Book of Dragons 1.31 (in the pack) against 1.4: saved-data keys per class, entity ids, its new library
 copper/    Copper Age Backport (Forge 1.20.1): items, blocks, entities, config keys, mixins, tool/armor numbers
"""
import io, json, os, re, shutil, subprocess, sys, urllib.parse, urllib.request, zipfile

server = zipfile.ZipFile(sys.argv[1])
OUT = sys.argv[2]
TMP = "/tmp/reports-work"
summary = []


def say(*parts):
    line = " ".join(str(p) for p in parts)
    summary.append(line)
    print(line)


def get(url, raw=False):
    req = urllib.request.Request(url, headers={"User-Agent": "bloom-ci (github actions)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    return data if raw else json.loads(data)


def write(rel, text):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def server_jar(prefix):
    for name in server.namelist():
        base = name.split("/")[-1]
        if name.endswith(".jar") and "/mods/" in "/" + name and base.lower().startswith(prefix.lower()):
            return base, server.read(name)
    return None, None


def unpack(label, data):
    d = os.path.join(TMP, label)
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    z = zipfile.ZipFile(io.BytesIO(data))
    z.extractall(d)
    return d, z


def javap(classdir, rel):
    cls = rel[:-6].replace("/", ".")
    r = subprocess.run(["javap", "-p", "-c", "-constants", "-cp", classdir, cls], capture_output=True, text=True)
    return r.stdout or r.stderr


def classes(z):
    return [n for n in z.namelist() if n.endswith(".class")]


def flags(raw):
    marks = {
        "playerAnimator": b"dev/kosmx/playerAnim", "geckolib": b"software/bernie/geckolib", "azurelib": b"mod/azure/azurelib",
        "hudOverlay": b"RegisterGuiOverlaysEvent", "overlayRender": b"IGuiOverlay", "guiRender": b"RenderGuiOverlayEvent",
        "invulnerable": b"invulnerableTime", "attackEvent": b"LivingAttackEvent", "hurtEvent": b"LivingHurtEvent",
        "exhaustion": b"causeFoodExhaustion", "configSpec": b"ForgeConfigSpec", "keyMapping": b"KeyMapping",
        "network": b"SimpleChannel", "deltaMovement": b"setDeltaMovement",
    }
    return [k for k, v in marks.items() if v in raw]


# ---------- dodge ----------
DODGE = [
    ("bossesrise", "block_factorys_bosses", rb"dodge_roll|DodgeRoll|dodgeRoll|Dodge|Roll"),
    ("monsterexpansion", "monsterexpansion", rb"key\.monsterexpansion\.roll|Roll|Dodge"),
    ("skarrier", "skarrier_mobs", rb"semi_living_boots_dash|Dash|SemiLiving"),
]
for label, prefix, pattern in DODGE:
    name, data = server_jar(prefix)
    if not data:
        say("dodge", label, "jar not found")
        continue
    d, z = unpack(label, data)
    picked = []
    for rel in classes(z):
        raw = z.read(rel)
        simple = rel.rsplit("/", 1)[-1]
        if re.search(pattern, simple.encode()) or re.search(rb"dodge_roll|monsterexpansion\.roll|semi_living_boots_dash", raw) \
                or (b"IGuiOverlay" in raw or b"RegisterGuiOverlaysEvent" in raw or b"RenderGuiOverlayEvent" in raw):
            picked.append(rel)
    say("dodge", label, name, len(picked), "classes")
    for rel in picked:
        raw = z.read(rel)
        text = javap(d, rel)
        write(f"dodge/{label}/{rel.replace('/', '.')[:-6]}.txt", text)
        say("  ", rel, len(raw), "bytes", flags(raw))
    for n in z.namelist():
        low = n.lower()
        if low.startswith("assets/") and low.endswith(".png") and re.search(r"roll|dodge|dash|cooldown|stamina|charge", low):
            os.makedirs(os.path.join(OUT, "dodge", label, "textures"), exist_ok=True)
            with open(os.path.join(OUT, "dodge", label, "textures", n.replace("/", "_")), "wb") as f:
                f.write(z.read(n))
            say("   texture", n)
        if low.endswith("mods.toml"):
            write(f"dodge/{label}/mods.toml", z.read(n).decode("utf-8", "replace"))
    # Config keys: every string in classes that build a ForgeConfigSpec.
    keys = []
    for rel in classes(z):
        raw = z.read(rel)
        if b"ForgeConfigSpec$Builder" in raw:
            text = javap(d, rel)
            keys.append("## " + rel + "\n" + "\n".join(sorted(set(re.findall(r"// String (.+)$", text, re.M)))))
    write(f"dodge/{label}/config-strings.txt", "\n\n".join(keys))
    for n in server.namelist():
        if n.startswith("config/") and prefix.split("_")[0] in n.lower():
            write(f"dodge/{label}/server-{n.replace('/', '_')}", server.read(n).decode("utf-8", "replace"))
            say("   server config", n)

# ---------- pirates ----------
name, data = server_jar("block_factorys_bosses")
if data:
    d, z = unpack("pirates", data)
    pirate = [rel for rel in classes(z) if "pirate" in rel.lower() or "kraken" in rel.lower()]
    goals = set()
    for rel in pirate:
        text = javap(d, rel)
        write(f"pirates/{rel.replace('/', '.')[:-6]}.txt", text)
        for ref in re.findall(r"// class ([\w/$]+)", text):
            if ref.startswith(rel.split("/")[0]) and ("goal" in ref.lower() or "attack" in ref.lower() or "ai/" in ref.lower()):
                goals.add(ref + ".class")
    for rel in sorted(goals - set(pirate)):
        if rel in z.namelist():
            write(f"pirates/goal.{rel.replace('/', '.')[:-6]}.txt", javap(d, rel))
    say("pirates", name, len(pirate), "pirate/kraken classes,", len(goals - set(pirate)), "goal classes")
    for rel in pirate:
        say("  ", rel, flags(z.read(rel)))

# ---------- dragons ----------
NBT = re.compile(r"ldc\s+#\d+\s+// String ([^\n]+)\n(?:.*\n){0,3}?.*invokevirtual\s+#\d+\s+// Method net/minecraft/nbt/CompoundTag\.(put\w*|get\w*|contains)")


def nbt_keys(label, data):
    d, z = unpack(label, data)
    out = {}
    for rel in classes(z):
        raw = z.read(rel)
        if b"net/minecraft/nbt/CompoundTag" not in raw:
            continue
        text = javap(d, rel)
        found = {"put": set(), "get": set()}
        for key, op in NBT.findall(text):
            found["put" if op.startswith("put") else "get"].add(key)
        if found["put"] or found["get"]:
            out[rel[:-6].rsplit("/", 1)[-1]] = {k: sorted(v) for k, v in found.items()}
    strings = set()
    for rel in classes(z):
        raw = z.read(rel)
        if re.search(rb"(?i)legacy|migrat|datafix|upgrade|convert", raw):
            text = javap(d, rel)
            for s in re.findall(r"// String (.+)$", text, re.M):
                if re.search(r"(?i)legacy|migrat|datafix|upgrade|convert|old", s):
                    strings.add(rel[:-6].rsplit("/", 1)[-1] + ": " + s)
    lang = {}
    for n in z.namelist():
        if n.endswith("lang/en_us.json"):
            lang = json.loads(z.read(n).decode("utf-8", "replace"))
    entities = sorted({k.split(".")[2] for k in lang if k.startswith("entity.bookofdragons.")})
    return out, sorted(strings), entities, z


old_name, old = server_jar("bookofdragons")
try:
    versions = get("https://api.modrinth.com/v2/version/7ZKoc6fd")
    url = versions["files"][0]["url"]
    new = get(url, raw=True)
except Exception as e:  # Modrinth down: CurseForge copy found earlier
    say("dragons modrinth failed", e)
    new = get("https://mediafilez.forgecdn.net/files/9339/901/bookofdragons-1.4-1.20.1.jar", raw=True)
if old:
    old_keys, old_legacy, old_entities, _ = nbt_keys("bod-old", old)
    new_keys, new_legacy, new_entities, newz = nbt_keys("bod-new", new)
    write("dragons/nbt-old.json", json.dumps(old_keys, indent=1, sort_keys=True))
    write("dragons/nbt-new.json", json.dumps(new_keys, indent=1, sort_keys=True))
    lines = []
    for cls in sorted(set(old_keys) | set(new_keys)):
        o, n = old_keys.get(cls), new_keys.get(cls)
        if not o:
            lines.append(f"NEW CLASS {cls}: put={n['put']} get={n['get']}")
        elif not n:
            lines.append(f"GONE CLASS {cls}: put={o['put']}")
        else:
            lost = sorted(set(o["put"]) - set(n["get"]))
            added = sorted(set(n["get"]) - set(o["put"]))
            if lost or added:
                lines.append(f"CHANGED {cls}: old saves but new no longer reads={lost} | new reads but old never saved={added}")
    write("dragons/nbt-diff.txt", "\n".join(lines))
    say("dragons", old_name, "->", "1.4:", len(lines), "classes with saved-data differences")
    say("  entities only old:", sorted(set(old_entities) - set(new_entities)))
    say("  entities only new:", sorted(set(new_entities) - set(old_entities)))
    say("  1.4 legacy/migration strings:", new_legacy[:40])
    for n in newz.namelist():
        if n.endswith("mods.toml"):
            write("dragons/mods-1.4.toml", newz.read(n).decode("utf-8", "replace"))
        if n.lower().endswith(".md") or "changelog" in n.lower():
            write("dragons/" + n.replace("/", "_"), newz.read(n).decode("utf-8", "replace")[:20000])
try:
    lib = get("https://api.modrinth.com/v2/project/7zlUOZvb")
    libv = get("https://api.modrinth.com/v2/version/k6IOaA4J")
    say("dragons library:", lib.get("title"), "|", lib.get("description"), "| license", (lib.get("license") or {}).get("id"),
        "| version", libv.get("version_number"), libv.get("game_versions"), libv.get("loaders"),
        "| files", [(f["filename"], f["size"]) for f in libv["files"]], "| deps", [(x.get("project_id"), x.get("dependency_type")) for x in libv.get("dependencies", [])])
except Exception as e:
    say("dragons library lookup failed", e)
# Who in the pack needs AzureLib, and at which versions (1.4 pins exactly 3.2.0).
for n in server.namelist():
    if n.endswith(".jar") and "/mods/" in "/" + n:
        try:
            z = zipfile.ZipFile(io.BytesIO(server.read(n)))
            toml = z.read("META-INF/mods.toml").decode("utf-8", "replace")
        except Exception:
            continue
        if n.split("/")[-1].lower().startswith("azurelib"):
            say("  pack azurelib jar:", n.split("/")[-1])
        for block in re.split(r"\[\[dependencies", toml):
            if re.search(r'modId\s*=\s*"azurelib"', block):
                rng = re.search(r'versionRange\s*=\s*"([^"]*)"', block)
                say("  needs azurelib:", n.split("/")[-1], rng.group(1) if rng else "?")

# ---------- copper ----------
copper = None
try:
    q = urllib.parse.quote('[["versions:1.20.1"],["categories:forge"]]')
    hits = get(f"https://api.modrinth.com/v2/search?query=copper%20age%20backport&facets={q}&limit=8")["hits"]
    for h in hits:
        say("copper modrinth hit:", h["slug"], "|", h["title"], "|", h["downloads"], "downloads |", h["project_id"])
    pick = next((h for h in hits if "copper age" in h["title"].lower()), None)
    if pick:
        vs = get(f"https://api.modrinth.com/v2/project/{pick['project_id']}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
        if vs:
            say("copper modrinth version:", vs[0]["version_number"], vs[0]["date_published"], vs[0]["files"][0]["filename"],
                "license?", get(f"https://api.modrinth.com/v2/project/{pick['project_id']}").get("license", {}).get("id"))
            copper = get(vs[0]["files"][0]["url"], raw=True)
except Exception as e:
    say("copper modrinth failed", e)
if copper is None:
    try:
        cf = get("https://api.cfwidget.com/minecraft/mc-mods/copper-age-backport")
        files = [f for f in cf.get("files", []) if "1.20.1" in f.get("versions", []) and "Forge" in f.get("versions", [])]
        files.sort(key=lambda f: f.get("uploaded_at", ""), reverse=True)
        f = files[0]
        say("copper curseforge file:", f["id"], f["name"], f.get("uploaded_at"), f.get("versions"))
        copper = get(f"https://mediafilez.forgecdn.net/files/{f['id'] // 1000}/{f['id'] % 1000}/{urllib.parse.quote(f['name'])}", raw=True)
    except Exception as e:
        say("copper curseforge failed", e)
if copper:
    d, z = unpack("copper", copper)
    say("copper jar", len(copper), "bytes,", len(classes(z)), "classes")
    lang = {}
    for n in z.namelist():
        if n.endswith("en_us.json"):
            lang.update(json.loads(z.read(n).decode("utf-8", "replace")))
        if n.endswith("mods.toml"):
            write("copper/mods.toml", z.read(n).decode("utf-8", "replace"))
        if n.endswith(".json") and "mixins" in n and "/" not in n:
            write("copper/" + n, z.read(n).decode("utf-8", "replace"))
            say("  mixins", n, json.loads(z.read(n)).get("mixins"), json.loads(z.read(n)).get("client"))
    for kind in ("item", "block", "entity"):
        ids = sorted({".".join(k.split(".")[2:3]) for k in lang if k.startswith(kind + ".") and len(k.split(".")) >= 3})
        say(f"  {kind}s ({len(ids)}):", ids)
    say("  recipes:", len([n for n in z.namelist() if "/recipes/" in n]), "| loot tables:", len([n for n in z.namelist() if "/loot_tables/" in n]),
        "| worldgen:", [n for n in z.namelist() if "/worldgen/" in n or "biome_modifier" in n][:20])
    conf = []
    for rel in classes(z):
        raw = z.read(rel)
        if b"ForgeConfigSpec$Builder" in raw or re.search(rb"(?i)Tier|ArmorMaterial|Material", rel.encode()):
            conf.append(f"## {rel}\n" + javap(d, rel))
    write("copper/config-and-materials.txt", "\n\n".join(conf))
    say("  config/material classes:", len(conf))

write("summary.txt", "\n".join(summary))
