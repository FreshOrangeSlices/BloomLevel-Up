"""Raw Wizards spell files (a few) and Fabled Weaponry's CommonEvents where it names tomes."""
import io, json, os, re, subprocess, urllib.parse, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (spell engine check)"}


def get(url):
    for _ in range(3):
        try:
            return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
        except Exception:
            pass
    return None


out = []
p = out.append
jars = json.load(open("probe/alljars.json"))
url = jars["cf:fabledweaponry-1.20.1-1.0.7.5.jar"]
data = None
for u in (url if isinstance(url, list) else [url]):
    data = get(u)
    if data:
        break
open("fw.jar", "wb").write(data)
for cls in ("net.mindoth.fabledweaponry.event.CommonEvents",):
    r = subprocess.run(["java", "-jar", "cfr.jar", "fw.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    text = r.stdout
    p(f"==== {cls}: {len(text)} chars")
    seen = set()
    for m in re.finditer(r"(?i)tome", text):
        start = text.rfind("\n    public", 0, m.start())
        if start in seen:
            continue
        seen.add(start)
        end = text.find("\n    }\n", m.start())
        p(text[start:min(end + 7, start + 4000)])

res = get("https://api.modrinth.com/v2/project/wizards/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
v = json.loads(res)[0]
f = next(x for x in v["files"] if x["primary"])
wz = zipfile.ZipFile(io.BytesIO(get(f["url"])))
for sid in ("arcane_bolt", "fireball", "frostbolt", "fire_meteor", "frost_nova", "arcane_blink"):
    for n in wz.namelist():
        if re.search(r"data/wizards/spells?/" + sid + r"\.json$", n):
            p(f"==== {n}\n" + " ".join(wz.read(n).decode().split())[:1500])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/magic3.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
