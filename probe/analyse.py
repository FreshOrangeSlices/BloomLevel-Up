"""Only Hammers And Excavators: each material's numbers (mining level, speed, durability, damage, enchantability), and recipes."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (hammers)"}


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
urls = jars["only-hammers-and-excavators"]
open("oh.jar", "wb").write(get(urls if isinstance(urls, list) else [urls]))
z = zipfile.ZipFile("oh.jar")
classes = [n for n in z.namelist() if n.endswith(".class")]
p("classes: " + ", ".join(n.rsplit("/", 1)[-1][:-6] for n in classes if "$" not in n))
for n in classes:
    b = z.read(n)
    if re.search(rb"(?i)tier|material|config", n.encode()) or b"Tier" in b and (b"m_6609_" in b or b"getUses" in b):
        cls = n[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", "oh.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        body = [" ".join(l.split()) for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
        p(f"---- {cls}")
        p("\n".join(l[:300] for l in body[:120]))
for n in z.namelist():
    if n.endswith(".json") and ("/recipes/" in n) and "hammer" in n:
        p(f"recipe {n.rsplit('/', 1)[-1]}: " + " ".join(z.read(n).decode().split())[:400])
    if n.endswith(".toml") or (n.endswith(".json") and "config" in n):
        p(f"file {n}: " + " ".join(z.read(n).decode("utf-8", "replace").split())[:1500])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/hammers.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
