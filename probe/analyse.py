"""Armor with special effects (quirks), across the pack: armor classes that do something while worn (onArmorTick,
inventoryTick) and armor pieces whose tooltips describe an ability."""
import json, os, re, subprocess, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (armor quirks)"}
ARMOR_WORDS = r"helmet|helm|goggles|skull|mask|crown|hat|hood|cap|circlet|chestplate|tunic|robe|leggings|pants|boots|greaves|shoes|cloak|armor|visor|headgear|horns|head"


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
    data = get(urls if isinstance(urls, list) else [urls])
    if data:
        open(path, "wb").write(data)
        return slug, path
    return slug, None


with ThreadPoolExecutor(8) as pool:
    fetched = list(pool.map(fetch, jars.items()))

out = []
p = out.append
for slug, path in fetched:
    if not path:
        continue
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        continue
    lines = []
    # 1. Armor classes with a worn tick.
    for n in z.namelist():
        if not n.endswith(".class") or n.startswith(("net/minecraft/", "com/mojang/")):
            continue
        b = z.read(n)
        if not (b"net/minecraft/world/item/ArmorItem" in b or b"GeoArmorItem" in b or b"ArmorItem" in n.encode()):
            continue
        ticks = [w for w, pat in (("onArmorTick", b"onArmorTick"), ("inventoryTick", b"m_6883_"), ("tooltip", b"m_7373_")) if pat in b]
        if "onArmorTick" in ticks or "inventoryTick" in ticks:
            cls = n[:-6].replace("/", ".")
            r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
            text = r.stdout
            body = ""
            for meth in ("onArmorTick", "m_6883_"):
                at = text.find(" " + meth + "(")
                if at >= 0:
                    body += " ".join(text[at:at + 900].split()) + " | "
            effects = sorted(set(re.findall(r"MobEffects\.(f_\w+)|(\w+Effects?\.\w+)\.get\(\)|m_7292_|setNoGravity|m_20256_|fallDistance|f_19789_", text)))
            lines.append(f"  CLASS {cls.rsplit('.', 1)[-1]} [{', '.join(ticks)}]: {body[:700]}")
    # 2. Armor pieces whose lang has a description.
    for n in z.namelist():
        if not n.endswith("lang/en_us.json"):
            continue
        try:
            lang = json.loads(z.read(n).decode("utf-8", "replace"))
        except Exception:
            continue
        names = {k: v for k, v in lang.items() if re.match(r"^item\.[a-z0-9_]+\.[a-z0-9_]+$", k)}
        for k, v in lang.items():
            if not re.search(r"(?i)tooltip|desc|lore|ability|effect|info", k):
                continue
            m = re.search(r"([a-z0-9_]*(?:" + ARMOR_WORDS + r")[a-z0-9_]*)", k)
            if not m:
                continue
            text = re.sub(r"§.", "", str(v))
            if len(text) < 12:
                continue
            lines.append(f"  LANG {k} = {text[:220]}")
    if lines:
        p(f"==== {slug}")
        out.extend(lines[:60])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/quirks.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
