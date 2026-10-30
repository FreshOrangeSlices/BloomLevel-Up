"""Throwaway: chunk 1's settings. Usage: reports5.py <server.zip> <shots dir> <out dir>
 xaero/   the test client's Xaero's Minimap config folder, whole
 combatroll, craftingtweaks, bossesrise, monsterexpansion, ipn: config classes (javap), config registration, key names
"""
import io, json, os, re, shutil, subprocess, sys, urllib.request, zipfile
server = zipfile.ZipFile(sys.argv[1]); SHOTS = sys.argv[2]; OUT = sys.argv[3]; TMP = "/tmp/r5"; lines = []
def say(*p):
    s = " ".join(str(x) for x in p); lines.append(s); print(s)
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"}), timeout=120) as r:
        d = r.read()
    return d if raw else json.loads(d)
def write(rel, text):
    p = os.path.join(OUT, rel); os.makedirs(os.path.dirname(p), exist_ok=True); open(p, "w").write(text)
def unpack(label, data):
    d = os.path.join(TMP, label); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
    z = zipfile.ZipFile(io.BytesIO(data)); z.extractall(d); return d, z
def javap(d, rel):
    r = subprocess.run(["javap", "-p", "-c", "-constants", "-cp", d, rel[:-6].replace("/", ".")], capture_output=True, text=True)
    return r.stdout or r.stderr
def server_jar(prefix):
    for n in server.namelist():
        b = n.split("/")[-1]
        if n.endswith(".jar") and "/mods/" in "/" + n and b.lower().startswith(prefix):
            return b, server.read(n)
    return None, None
def modrinth_version(vid):
    v = get(f"https://api.modrinth.com/v2/version/{vid}")
    return v["files"][0]["filename"], get(v["files"][0]["url"], raw=True)
def dump(label, data, pattern, also_lang=True):
    d, z = unpack(label, data)
    for n in z.namelist():
        if n.endswith("mods.toml"):
            write(f"{label}/mods.toml", z.read(n).decode("utf-8", "replace"))
        if also_lang and n.endswith("lang/en_us.json"):
            write(f"{label}/en_us.json", z.read(n).decode("utf-8", "replace"))
        if n.endswith(".json") and "mixins" in n and "/" not in n:
            write(f"{label}/{n}", z.read(n).decode("utf-8", "replace"))
    picked = []
    for n in z.namelist():
        if not n.endswith(".class"):
            continue
        raw = z.read(n)
        if re.search(pattern, n) or b"registerConfig" in raw or b"KeyMapping" in raw and b"<init>" in raw and len(raw) < 20000:
            picked.append(n)
    for n in picked:
        write(f"{label}/javap/{n.replace('/', '.')[:-6]}.txt", javap(d, n))
    say(label, len(picked), "classes:", [n.rsplit("/", 1)[-1] for n in picked][:60])
    return z

# xaero: the client's whole minimap config
src = os.path.join(SHOTS, "game", "config", "xaero")
for root, _, files in os.walk(src):
    for f in files:
        p = os.path.join(root, f); rel = os.path.relpath(p, src)
        write("xaero/" + rel, open(p, encoding="utf-8", errors="replace").read())
        say("xaero file", rel, os.path.getsize(p))

# combat roll: newest Forge 1.20.1 build
vs = get("https://api.modrinth.com/v2/project/wGKYL7st/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
say("combatroll versions:", [(v["version_number"], v["date_published"][:10], v["version_type"], v["id"], v["files"][0]["filename"],
                              [(x.get("project_id"), x.get("dependency_type")) for x in v.get("dependencies", [])]) for v in vs[:6]])
if vs:
    v = vs[0]
    z = dump("combatroll", get(v["files"][0]["url"], raw=True), r"(?i)config|keybind|key|hud|Enchant|attribute")
name, data = server_jar("craftingtweaks")
dump("craftingtweaks", data, r"(?i)config|Mode")
name, data = server_jar("block_factorys_bosses")
dump("bossesrise", data, r"(?i)configuration/|BlockFactorysBosses\.class|BossesRise\.class|BossesriseMod")
name, data = server_jar("monsterexpansion")
dump("monsterexpansion", data, r"(?i)config/|MonsterExpansion\.class")
fn, data = modrinth_version("CrtAI3P9")
say("ipn:", fn)
dump("ipn", data, r"ipnext/config/(Configs|ModSettings|GuiSettings|AutoRefill|EditProfiles|Hotkeys|Debugs|Tweaks|ConfigScreen)[^/]*\.class$|ipnext/config/[A-Z][A-Za-z]*\.class$")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
