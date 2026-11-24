"""Which mod in the pack changes Sharpness's damage (Sharpness III shows 2.16, vanilla 1.20.1 gives 2.0)?

Downloads every jar in the pack and lists the classes and refmaps that name the enchantment damage code
(DamageEnchantment, getDamageBonus and their SRG names), then decompiles the matching classes.
"""
import io, json, os, re, subprocess, traceback, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"}
PATTERNS = [b"DamageEnchantment", b"getDamageBonus", b"m_7335_", b"m_44833_", b"m_44845_"]
SHARP = re.compile(rb"(?i)sharpness")


def get(urls):
    for url in urls:
        for _ in range(3):
            try:
                return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
            except Exception:
                pass
    return None


jars = json.load(open("probe/alljars.json"))
os.makedirs("jars", exist_ok=True)


def fetch(item):
    slug, urls = item
    path = os.path.join("jars", re.sub(r"[^A-Za-z0-9._-]", "_", slug) + ".jar")
    data = get(urls)
    if data:
        open(path, "wb").write(data)
        return slug, path
    return slug, None


out = []
p = out.append
with ThreadPoolExecutor(8) as pool:
    fetched = list(pool.map(fetch, jars.items()))
p(f"downloaded {sum(1 for _, x in fetched if x)} of {len(fetched)} jars; missing: {[s for s, x in fetched if not x]}")

to_decompile = []
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        p(f"{slug}: not a zip ({e})")
        continue
    for name in z.namelist():
        if not (name.endswith(".class") or name.endswith(".json") or name.endswith(".toml")):
            continue
        data = z.read(name)
        if name.startswith("data/") and name.endswith(".json") and b"sharpness" not in data:
            continue
        hits = [pat.decode() for pat in PATTERNS if pat in data]
        sharp = bool(SHARP.search(data))
        if not hits and not sharp:
            continue
        if name.endswith(".class") and name.startswith(("net/minecraft/", "com/mojang/")):
            continue
        p(f"{slug} | {name} | {', '.join(hits)}{' | says sharpness' if sharp else ''}")
        if name.endswith(".class") and hits and any(h in ("DamageEnchantment", "m_7335_") for h in hits):
            to_decompile.append((path, name[:-6].replace("/", ".")))
        if name.endswith(".json") and (hits or sharp) and len(data) < 6000:
            p("    " + re.sub(r"\s+", " ", data.decode("utf-8", "replace"))[:1500])

p(f"\n==== decompiling {len(to_decompile)} classes")
for path, cls in to_decompile[:40]:
    r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    lines = [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
    p(f"---- {os.path.basename(path)} {cls}")
    p("\n".join(l[:240] for l in lines)[:5000])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/sharpness.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
