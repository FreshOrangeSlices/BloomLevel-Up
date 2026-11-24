"""The Graveyard's horde spawner and its spawn checks: does it spawn in light, every night, near players?"""
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
open("tg.jar", "wb").write(get(jars["the-graveyard-forge"]))
z = zipfile.ZipFile("tg.jar")
for cls in ("com.lion.graveyard.entities.horde.GraveyardHordeSpawner", "com.lion.graveyard.forge.GraveyardForge",
            "com.lion.graveyard.entities.HostileGraveyardEntity"):
    r = subprocess.run(["java", "-jar", "cfr.jar", "tg.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    body = [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
    p(f"---- {cls} ({len(body)} lines)")
    p("\n".join(l[:230] for l in body)[:9000])
for n in z.namelist():
    if re.search(r"(?i)horde", n):
        p(f"file {n}")
for n in z.namelist():
    if re.search(r"(?i)config", n) and n.endswith(".class") and "$" not in n:
        r = subprocess.run(["java", "-jar", "cfr.jar", "tg.jar", "--jarfilter", "^" + re.escape(n[:-6].replace("/", ".")) + "$"], capture_output=True, text=True)
        text = r.stdout
        for m in re.finditer(r"(?i)[^\n]*horde[^\n]*", text):
            p(f"  {n.rsplit('/', 1)[-1]}: {m.group(0).strip()[:230]}")
for n in z.namelist():
    if n.endswith(".json") and re.search(r"(?i)config|horde", n):
        p(f"file {n}: " + z.read(n).decode("utf-8", "replace")[:1500].replace("\n", " "))
lang = json.loads(z.read("assets/graveyard/lang/en_us.json").decode("utf-8", "replace"))
for k, v in lang.items():
    if re.search(r"(?i)horde", k + v):
        p(f"lang {k} = {v}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/graveyard.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
