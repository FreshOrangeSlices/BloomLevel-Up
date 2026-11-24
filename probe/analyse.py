"""Who listens to Architectury's spawn check (EntityEvent.LIVING_CHECK_SPAWN) and what do they answer?"""
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
hits = []
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    for name in z.namelist():
        if name.endswith(".class") and b"LIVING_CHECK_SPAWN" in z.read(name):
            hits.append((slug, path, name))
            p(f"{slug} | {name}")
p("")
for slug, path, name in hits:
    cls = name[:-6].replace("/", ".")
    if cls.startswith("dev.architectury"):
        r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        text = r.stdout
        for m in re.finditer(r"LIVING_CHECK_SPAWN|SpawnPlacementCheck|PositionCheck", text):
            start = text.rfind("\n    ", 0, m.start())
            p(f"---- {slug} {cls}\n" + text[start:start + 1500])
            break
        continue
    r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls.split("$")[0]) + "$"], capture_output=True, text=True)
    text = r.stdout
    p(f"---- {slug} {cls}")
    for m in re.finditer(r"LIVING_CHECK_SPAWN", text):
        start = text.rfind("\n    ", 0, m.start())
        p(text[start:start + 2500])
    # The lambdas it registers often sit in the same class: print methods returning EventResult.
    for m in re.finditer(r"\n    (?:private |public |protected )?(?:static )?EventResult \w+\([^)]*\) \{", text):
        p(text[m.start():m.start() + 3000])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/archspawn.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
