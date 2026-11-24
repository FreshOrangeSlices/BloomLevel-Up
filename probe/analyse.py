"""Legendary Monsters' spawn placement handler, and every other jar's listeners that could say ALLOW to a spawn check."""
import json, os, re, subprocess, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

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
open("lm.jar", "wb").write(get(jars["legendary-monsters"]))
r = subprocess.run(["java", "-jar", "cfr.jar", "lm.jar", "--jarfilter", "^net\\.miauczel\\.legendary_monsters\\.event\\.ForgeEvents$"], capture_output=True, text=True)
text = r.stdout
at = text.find("SpawnPlacementCheck")
p("---- legendary_monsters ForgeEvents around SpawnPlacementCheck")
for m in re.finditer(r"SpawnPlacementCheck", text):
    start = text.rfind("\n    @", 0, m.start())
    p(text[start:start + 2500])
    p("....")

# Any class anywhere in the pack that both names a spawn check event and sets Result.ALLOW (Event$Result.ALLOW).
os.makedirs("jars", exist_ok=True)


def fetch(item):
    slug, urls = item
    path = os.path.join("jars", re.sub(r"[^A-Za-z0-9._-]", "_", slug) + ".jar")
    data = get(urls)
    if data:
        open(path, "wb").write(data)
        return slug, path
    return slug, None


with ThreadPoolExecutor(8) as pool:
    fetched = list(pool.map(fetch, jars.items()))
p("\n---- classes naming a spawn check together with ALLOW")
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    for name in z.namelist():
        if not name.endswith(".class") or name.startswith("net/minecraft/"):
            continue
        b = z.read(name)
        if (b"SpawnPlacementCheck" in b or b"PositionCheck" in b or b"CheckSpawn" in b or b"m_217074_" in b) and b"ALLOW" in b:
            p(f"{slug} | {name}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/allow.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
