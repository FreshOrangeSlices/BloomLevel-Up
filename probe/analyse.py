"""Which mods change spawn rules through Forge's SpawnPlacementRegisterEvent, for which mobs, with which operation?"""
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
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    for name in z.namelist():
        if not name.endswith(".class") or name.startswith("net/minecraft"):
            continue
        b = z.read(name)
        if b"SpawnPlacementRegisterEvent" not in b:
            continue
        vanilla = re.findall(rb"f_20(?:458|524|501|562|481|526|530|568|468|497|550|455|479|513|549)_", b)
        ops = sorted(set(m.decode() for m in re.findall(rb"(REPLACE|AND|OR)", b)))
        p(f"{slug} | {name} | operations named: {ops} | vanilla EntityType fields: {sorted(set(v.decode() for v in vanilla))}")
        if b"REPLACE" in b or b"$Operation" in b and (b"OR" in b or vanilla):
            cls = name[:-6].replace("/", ".").split("$")[0]
            r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
            text = r.stdout
            for m in re.finditer(r"Operation\.(REPLACE|OR)", text):
                line_start = text.rfind(";", 0, m.start())
                p("    " + " ".join(text[line_start + 1:m.end() + 40].split())[:500])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/placements.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
