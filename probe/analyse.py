"""Iron's Spells' upgrade orbs: where their bonuses are applied (attribute event, curio event, NBT), and each orb's bonus."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (upgrade orbs)"}


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
urls = jars["irons-spells-n-spellbooks"]
open("iss.jar", "wb").write(get(urls if isinstance(urls, list) else [urls]))
z = zipfile.ZipFile("iss.jar")
for n in sorted(z.namelist()):
    if not n.endswith(".class"):
        continue
    b = z.read(n)
    if b"UpgradeData" in b and (b"ItemAttributeModifierEvent" in b or b"CurioAttributeModifierEvent" in b or b"getAttributeModifiers" in b or b"m_7167_" in b):
        cls = n[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", "iss.jar", "--jarfilter", "^" + re.escape(cls.split("$")[0]) + "$"], capture_output=True, text=True)
        text = r.stdout
        p(f"---- {cls}")
        for m in re.finditer(r"ItemAttributeModifierEvent|CurioAttributeModifierEvent|UpgradeData\.|EventPriority", text):
            start = text.rfind("\n", 0, m.start())
            p("   " + " ".join(text[start:start + 400].split())[:400])
for n in sorted(z.namelist()):
    if n.endswith(".class") and re.search(r"(?i)UpgradeOrbType|UpgradeType|UpgradeTypes", n):
        cls = n[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", "iss.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        lines = [" ".join(l.split()) for l in r.stdout.splitlines() if re.search(r"(?i)register|new UpgradeOrbType|AttributeRegistry|0\.\d+|ModifierOperation|Operation", l)]
        p(f"---- {cls}")
        p("\n".join("   " + l[:260] for l in lines[:40]))
for n in z.namelist():
    if n.startswith("data/irons_spellbooks/") and "upgrade" in n and n.endswith(".json"):
        p(f"file {n}: " + " ".join(z.read(n).decode().split())[:300])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/orbs.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
