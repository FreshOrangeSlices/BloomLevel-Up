"""Throwaway: Easy Mob Spawn Control's config file names (Storage's static initializer), and Migamigos' items and
English text (anything about reviving or respawning companions). Usage: emsc2.py <server.zip> <out file>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
jars = {n.rsplit("/", 1)[-1]: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.endswith(".jar") and "/mods/" in "/" + n}
out = []
emsc = next(j for n, j in jars.items() if "easy_mob_spawn_control" in n)
with tempfile.TemporaryDirectory() as tmp:
    for cls in ("io/github/catomon/easymobspawncontrol/Storage.class", "io/github/catomon/easymobspawncontrol/DespawnStorage.class", "io/github/catomon/easymobspawncontrol/MobCapStorage.class"):
        path = os.path.join(tmp, cls.replace("/", "_"))
        open(path, "wb").write(emsc.read(cls))
        text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
        i = text.find("static {};")
        out.append(f"## {cls} static init\n{text[i:i + 12000]}")
mig = next(j for n, j in jars.items() if n.lower().startswith("migamigos"))
for entry in mig.namelist():
    if entry.endswith("lang/en_us.json"):
        out.append("## migamigos en_us\n" + mig.read(entry).decode("utf-8", "replace")[:20000])
items = sorted({e.split("/")[-1][:-5] for e in mig.namelist() if "/models/item/" in e})
out.append("## migamigos item models\n" + ", ".join(items))
strings = set()
for entry in mig.namelist():
    if entry.endswith(".class"):
        strings |= {m.decode() for m in re.findall(rb"[\x20-\x7e]*(?:revive|Revive|respawn|Respawn|resurrect|Resurrect|Totem|totem|soul|Soul|death|Death|recall|Recall)[\x20-\x7e]*", mig.read(entry)) if len(m) < 120}
out.append("## migamigos strings\n" + "\n".join(sorted(strings)[:300]))
open(sys.argv[2], "w").write("\n".join(out))
print(len(out), "sections")
