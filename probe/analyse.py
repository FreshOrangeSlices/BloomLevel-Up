"""Bosses'Rise: what wearing the Ashlord Skull and the Dragon Bones set does."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (ashlord skull)"}


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
for n in sorted(z.namelist()):
    if not n.endswith(".class"):
        continue
    b = z.read(n)
    if not (b"DRAGON_SKULL" in b or b"DRAGON_BONES" in b or b"dragon_skull" in b or b"dragon_bones" in b or b"DragonBones" in b or b"DragonSkull" in b):
        continue
    if re.search(r"(?i)renderer|model|registry|init/|Blocks|Items\.class|Tabs|Creative", n):
        p(f"  (skip {n})")
        continue
    cls = n[:-6].replace("/", ".")
    r = subprocess.run(["java", "-jar", "cfr.jar", "br.jar", "--jarfilter", "^" + re.escape(cls.split("$")[0]) + "$"], capture_output=True, text=True)
    text = r.stdout
    p(f"---- {cls}")
    for m in re.finditer(r"DRAGON_SKULL|DRAGON_BONES|DragonSkull|DragonBones", text):
        start = text.rfind("\n    ", 0, m.start())
        end = text.find("\n    }", m.start())
        p("   " + " ".join(text[start:min(end, start + 1800)].split())[:1800])
        break

os.makedirs("probe-out", exist_ok=True)
open("probe-out/ashlord.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
