# What the look-changing files contain: Villager Guards (how a guard is made), Fresh Animations (which mobs, eyes),
# Better Animations Collection (which mobs it re-models), Fresh Animations' player extension.
import io, json, os, re, sys, zipfile
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
mrpack = zipfile.ZipFile(sys.argv[1])
index = json.loads(mrpack.read("modrinth.index.json"))
out = {}
def note(title, text):
    for i in range(0, len(text), 3800):
        print(f"::notice title={title}-{i//3800}::{esc(text[i:i+3800])}")
import urllib.request
def fetch(path):
    f = next(f for f in index["files"] if f["path"] == path)
    return urllib.request.urlopen(urllib.request.Request(f["downloads"][0], headers={"User-Agent": "bloom ci"}), timeout=60).read()
paths = {f["path"]: f for f in index["files"]}
def find(pattern):
    return next(p for p in paths if re.search(pattern, p, re.I))

# Villager Guards
z = zipfile.ZipFile(io.BytesIO(fetch(find(r"villager-guards"))))
names = z.namelist()
lines = []
lines.append("files: " + ", ".join(n for n in names if not n.endswith("/"))[:1500])
for n in names:
    if n.endswith(".mcfunction") or n.endswith(".json") and "/function" in n:
        text = z.read(n).decode("utf-8", "replace")
        for l in text.splitlines():
            if re.search(r"summon|mannequin|profile|skin|texture", l):
                lines.append(f"{n}: {l.strip()[:300]}")
note("guards", "\n".join(lines[:80]))

# Fresh Animations
z = zipfile.ZipFile(io.BytesIO(fetch(find(r"FreshAnimations"))))
names = z.namelist()
jems = sorted(n.split("/")[-1] for n in names if n.endswith(".jem"))
note("fa-models", f"{len(jems)} models: " + ", ".join(jems))
for mob in ("villager.jem", "sheep.jem"):
    n = next((x for x in names if x.endswith("/" + mob)), None)
    if n:
        text = z.read(n).decode("utf-8", "replace")
        hits = [l.strip()[:220] for l in text.splitlines() if re.search(r"eye|blink|lid", l, re.I)]
        note("fa-" + mob, "\n".join(hits[:60]) or "no eye/blink lines")
other = [n for n in names if re.search(r"blink|eye", n, re.I)][:40]
note("fa-eye-files", "\n".join(other) or "none")

# Better Animations Collection
z = zipfile.ZipFile(io.BytesIO(fetch(find(r"betteranimationscollection"))))
meta = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
classes = sorted(set(re.sub(r".*/", "", n)[:-6] for n in z.namelist() if n.endswith(".class") and re.search(r"(Model|Animation|Element)", n)))
note("bac", (meta.get("description") or "") + "\n" + ", ".join(classes)[:3000])

# Fresh Animations player extension
z = zipfile.ZipFile(io.BytesIO(fetch(find(r"FA\+Player|Player-v"))))
note("fa-player", "\n".join(n for n in z.namelist() if not n.endswith("/"))[:3000])
