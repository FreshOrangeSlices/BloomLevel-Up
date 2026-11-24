"""Bosses'Rise: the Ashlord's (Infernal Dragon's) drops, and its wearable items with abilities."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (bosses rise wearables)"}


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
urls = jars["bossesrise"]
open("br.jar", "wb").write(get(urls if isinstance(urls, list) else [urls]))
z = zipfile.ZipFile("br.jar")
for n in z.namelist():
    if "loot_tables" in n and re.search(r"(?i)infernal|dragon|ashlord", n):
        p(f"loot {n}: " + " ".join(z.read(n).decode().split())[:1200])
lang = {}
for n in z.namelist():
    if n.endswith("lang/en_us.json"):
        lang.update(json.loads(z.read(n)))
for k, v in lang.items():
    if re.search(r"(?i)skull|helmet|crown|mask|head|goggle|hat|boots|chestplate|leggings|charm|amulet|ring|ashlord|tooltip|desc", k):
        p(f"lang {k} = {re.sub('§.', '', str(v))[:240]}")
for n in z.namelist():
    if n.endswith(".class") and re.search(r"(?i)(skull|helmet|crown|mask|armor)\w*Item", n) and "$" not in n:
        cls = n[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", "br.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        body = [" ".join(l.split()) for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
        p(f"---- {cls}\n   " + "\n   ".join(l[:200] for l in body[:70]))

os.makedirs("probe-out", exist_ok=True)
open("probe-out/bossesrise_wear.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
