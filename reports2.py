"""Throwaway: the report round, second pass. Usage: reports2.py <server.zip> <out dir>
 dragons: saved-data keys per class (CompoundTag calls by descriptor, SRG names), 1.31 against 1.4; AzureLib 3.2.0 for
          Forge 1.20.1; the Liber Animalium library
 dodge:   Bosses'Rise's config definitions with defaults, and any class that draws the player during a roll
 craft:   Crafting Tweaks' options (the crafting-grid buttons)
"""
import io, json, os, re, shutil, subprocess, sys, urllib.parse, urllib.request, zipfile

server = zipfile.ZipFile(sys.argv[1])
OUT = sys.argv[2]
TMP = "/tmp/reports2-work"
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


# ---------- dragons: saved-data keys ----------
CALL = re.compile(r"invoke\w+\s+#\d+\s+// Method net/minecraft/nbt/CompoundTag\.(\w+):\((Ljava/lang/String;)([^)]*)\)(\S+)")
LDC = re.compile(r"ldc(?:_w)?\s+#\d+\s+// String (.+)$")


def nbt_keys(label, data):
    d, z = unpack(label, data)
    out = {}
    for rel in classes(z):
        raw = z.read(rel)
        if b"net/minecraft/nbt/CompoundTag" not in raw:
            continue
        found = {"put": set(), "get": set()}
        last, age = None, 99
        for line in javap(d, rel).splitlines():
            m = LDC.search(line)
            if m:
                last, age = m.group(1), 0
                continue
            age += 1
            c = CALL.search(line)
            if c and last is not None and age <= 12:
                rest = c.group(3)
                kind = "get" if rest in ("", "I") else "put"
                found[kind].add(last)
                last = None
        if found["put"] or found["get"]:
            out[rel[:-6]] = {k: sorted(v) for k, v in found.items()}
    return out, z


old_name, old = server_jar("bookofdragons")
new = get("https://mediafilez.forgecdn.net/files/9339/901/bookofdragons-1.4-1.20.1.jar", raw=True)
old_keys, _ = nbt_keys("bod-old", old)
new_keys, newz = nbt_keys("bod-new", new)
write("dragons/nbt-old.json", json.dumps(old_keys, indent=1, sort_keys=True))
write("dragons/nbt-new.json", json.dumps(new_keys, indent=1, sort_keys=True))
say("dragons:", old_name, len(old_keys), "classes save data;", "1.4:", len(new_keys))
lines = []
for cls in sorted(set(old_keys) | set(new_keys)):
    o, n = old_keys.get(cls), new_keys.get(cls)
    if not o:
        lines.append(f"NEW {cls}: put={n['put']} get={n['get']}")
    elif not n:
        lines.append(f"GONE {cls}: put={o['put']} get={o['get']}")
    else:
        lost = sorted(set(o["put"]) - set(n["get"]))
        added = sorted(set(n["get"]) - set(o["put"]))
        if lost or added:
            lines.append(f"CHANGED {cls}: saved by 1.31, not read by 1.4={lost} | read by 1.4, never saved by 1.31={added}")
write("dragons/nbt-diff.txt", "\n".join(lines))
for line in lines:
    if "Dragon" in line or "Entity" in line or "Egg" in line or "Whistle" in line or "Data" in line:
        say("  ", line[:600])
# The base dragon class's saved keys, both versions, for the report.
for cls in sorted(old_keys):
    if cls.endswith("/DragonBase") or cls.endswith("/DragonFlyingRideableBase"):
        say("  1.31", cls, old_keys[cls])
for cls in sorted(new_keys):
    if cls.endswith("/DragonBase") or cls.endswith("/DragonFlyingRideableBase"):
        say("  1.4 ", cls, new_keys[cls])
# Entity class names, both versions (did any dragon move package or change class?).
def entity_classes(z):
    return sorted(n[:-6] for n in classes(z) if re.search(r"(?i)entity/.*(Entity|Base)\.class$", n) and "$" not in n)
_, oldz = unpack("bod-old2", old)
say("  dragon classes only in 1.31:", sorted(set(entity_classes(oldz)) - set(entity_classes(newz)))[:40])
say("  dragon classes only in 1.4:", sorted(set(entity_classes(newz)) - set(entity_classes(oldz)))[:40])

# AzureLib: what versions exist for Forge 1.20.1 (1.4 wants exactly 3.2.0; the pack has azurelib-neo 3.1.23).
for slug in ("azurelib", "7zlUOZvb"):
    try:
        vs = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
        say("azurelib forge 1.20.1 versions (" + slug + "):", [(v["version_number"], v["date_published"][:10], v["files"][0]["filename"]) for v in vs[:12]])
        break
    except Exception as e:
        say("azurelib lookup failed", slug, e)
try:
    cf = get("https://api.cfwidget.com/minecraft/mc-mods/azurelib")
    files = [f for f in cf.get("files", []) if "1.20.1" in f.get("versions", []) and "Forge" in f.get("versions", [])]
    files.sort(key=lambda f: f.get("uploaded_at", ""), reverse=True)
    say("azurelib curseforge forge 1.20.1:", [(f["name"], f.get("uploaded_at", "")[:10]) for f in files[:10]])
except Exception as e:
    say("azurelib curseforge failed", e)
# Liber Animalium (modId liberanimalium), required by 1.4 but not listed as a dependency on Modrinth.
for query in ("liber animalium", "liberanimalium"):
    try:
        hits = get(f"https://api.modrinth.com/v2/search?query={urllib.parse.quote(query)}&limit=8")["hits"]
        say("liberanimalium search", repr(query) + ":", [(h["slug"], h["title"], h["author"], h["downloads"], h["versions"][-3:], h["project_id"]) for h in hits])
    except Exception as e:
        say("liberanimalium search failed", e)
for slug in ("liber-animalium", "liberanimalium"):
    try:
        cf = get(f"https://api.cfwidget.com/minecraft/mc-mods/{slug}")
        files = [f for f in cf.get("files", []) if "1.20.1" in f.get("versions", [])]
        say("liberanimalium curseforge", slug, cf.get("title"), cf.get("summary"), [(f["name"], f.get("uploaded_at", "")[:10], f.get("versions")) for f in files[:5]])
        break
    except Exception as e:
        say("liberanimalium curseforge failed", slug, e)
# Does 1.4 bundle it (jar-in-jar) or carry its classes?
say("  1.4 jarjar:", [n for n in newz.namelist() if n.startswith("META-INF/jarjar/")], "| liberanimalium classes inside:",
    len([n for n in newz.namelist() if "liberanimalium" in n.lower()]))

# ---------- dodge: Bosses'Rise config defaults, and who draws the rolling player ----------
name, data = server_jar("block_factorys_bosses")
d, z = unpack("bossesrise", data)
for rel in classes(z):
    if rel.endswith("configuration/ServerConfiguration.class") or rel.endswith("configuration/ClientConfiguration.class"):
        write("dodge/" + rel.rsplit("/", 1)[-1] + ".txt", javap(d, rel))
        say("dodge: dumped", rel)
for rel in classes(z):
    raw = z.read(rel)
    hits = [m for m in (b"RenderPlayerEvent", b"ComputeCameraAngles", b"RenderLivingEvent", b"HumanoidModel", b"PlayerModel", b"dev/kosmx/playerAnim")
            if m in raw]
    if hits and (b"Roll" in raw or b"roll" in raw):
        write("dodge/render." + rel.rsplit("/", 1)[-1] + ".txt", javap(d, rel))
        say("dodge: roll render", rel, [h.decode() for h in hits])

# ---------- craft: Crafting Tweaks ----------
name, data = server_jar("craftingtweaks")
if data:
    d, z = unpack("craftingtweaks", data)
    say("craft:", name)
    for n in z.namelist():
        if n.endswith("en_us.json"):
            lang = json.loads(z.read(n).decode("utf-8", "replace"))
            say("  lang:", {k: v for k, v in lang.items() if "config" in k or "tooltip" in k or "button" in k.lower()})
    for rel in classes(z):
        raw = z.read(rel)
        if b"ForgeConfigSpec" in raw or rel.lower().endswith("config.class") or b"ConfigData" in raw or b"Balm" in raw and b"Config" in rel.encode():
            text = javap(d, rel)
            fields = re.findall(r"^\s+public (?:static )?(?:final )?(\w[\w.<>$]*) (\w+);$", text, re.M)
            if fields:
                say("  config class", rel, fields[:40])
else:
    say("craft: craftingtweaks not in the server zip")

write("summary.txt", "\n".join(summary))
