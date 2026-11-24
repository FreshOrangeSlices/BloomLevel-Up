"""Easy Mob Spawn Control: what its spawn rules and spawn events do (zombies and skeletons in a lit base every night)."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"}


def get(urls):
    for url in urls:
        for _ in range(3):
            try:
                return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
            except Exception:
                pass
    return None


jars = json.load(open("probe/alljars.json"))
out = []
p = out.append
data = get(jars["easy-mob-spawn-control"])
open("emsc.jar", "wb").write(data)
z = zipfile.ZipFile("emsc.jar")
names = [n for n in z.namelist() if n.endswith(".class")]
p(f"{len(names)} classes: " + ", ".join(n.rsplit('/', 1)[-1][:-6] for n in names if "$" not in n))
r = subprocess.run(["java", "-jar", "cfr.jar", "emsc.jar"], capture_output=True, text=True)
text = r.stdout
# Keep the classes about spawn rules, the spawn events and the stored settings, whole.
for block in re.split(r"(?=^/\*\n \* Decompiled with CFR)", text, flags=re.M):
    m = re.search(r"^(?:public |final |abstract )*(?:class|record|enum|interface) (\w+)", block, re.M)
    if not m:
        continue
    name = m.group(1)
    if re.search(r"(?i)rule|event|storage|spawn|light|config", name):
        body = [l for l in block.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
        p(f"---- {name} ({len(body)} lines)")
        p("\n".join(l[:200] for l in body)[:9000])
# The rule matching and checking, whole: each method or class body that names them.
for word in ("getMatchingSpawnRule", "checkSpawn", "appliesToBiome", "class SpawnRule", "record SpawnRule", "spawnRuleAssignments",
             "monsterSpawnInterval"):
    for m in re.finditer(re.escape(word), text):
        start = text.rfind("\n    ", 0, m.start())
        p(f"==== {word} @{m.start()}\n" + text[start:start + 2600])
for n in z.namelist():
    if n.endswith((".json", ".toml")) and "mixin" not in n:
        p(f"file {n}: " + z.read(n).decode("utf-8", "replace")[:600].replace("\n", " "))

os.makedirs("probe-out", exist_ok=True)
open("probe-out/emsc2.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
