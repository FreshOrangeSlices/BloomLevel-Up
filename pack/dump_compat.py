# How Villagers at Work's own compat pack (for Human Era, an EMF villager model pack) hooks its swing into a custom
# model, what Villagers at Work's client jar gives EMF, and Fresh Animations' terms.
import io, json, re, sys, zipfile, urllib.request
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
def note(title, text):
    for i in range(0, min(len(text), 3800 * 6), 3800):
        print(f"::notice title={title}-{i//3800}::{esc(text[i:i+3800])}")
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom ci"}), timeout=60).read()

for url in ("https://mediafilez.forgecdn.net/files/8939/569/vaw-human-era.zip", "https://edge.forgecdn.net/files/8939/569/vaw-human-era.zip"):
    try:
        data = get(url)
        break
    except Exception as e:
        data = None
        print("could not get", url, e)
if data:
    z = zipfile.ZipFile(io.BytesIO(data))
    if False: note("compat-files", "\n".join(f"{i.filename} {i.file_size}" for i in z.infolist()))
    for n in z.namelist():
        if n.endswith("villager.jem"):
            text = z.read(n).decode("utf-8", "replace")
            hits = []
            for m in re.finditer(r"vaw[^\"]*\"\s*:\s*\"[^\"]*\"|\"[^\"]*\"\s*:\s*\"[^\"]*vaw[^\"]*\"", text):
                hits.append(m.group(0))
            if n.endswith(".jem"):
                spots = [m.start() for m in re.finditer(r"vaw|attachments|animations", text)]
                body = "\n...\n".join(text[max(0, i - 200):i + 700] for i in spots[:8])
            else:
                body = text[:3000]
            note("compat-" + n.split("/")[-1][:30], f"{n} ({len(text)} chars)\n" + body)

mr = zipfile.ZipFile(sys.argv[1])
for n in mr.namelist():
    if re.search(r"overrides/mods/vaw-1", n):
        outer = zipfile.ZipFile(io.BytesIO(mr.read(n)))
        client = next(x for x in outer.namelist() if "vaw-client" in x and x.endswith(".jar"))
        c = zipfile.ZipFile(io.BytesIO(outer.read(client)))
        strings = set()
        for x in c.namelist():
            if x.endswith(".class"):
                raw = c.read(x)
                for s in re.findall(rb"[A-Za-z_.:/ ]{4,80}", raw):
                    s = s.decode()
                    if re.search(r"emf|variable|vaw_|vaw:|config|swing|tool|entity_model", s, re.I):
                        strings.add(s.strip())
            elif x.endswith(".json") or x.endswith(".properties") or x.endswith(".toml"):
                strings.add(x)
        note("vaw-client", "\n".join(sorted(strings))[:15000])

# Fresh Animations' terms
index = json.loads(mr.read("modrinth.index.json"))
fa = next(f for f in index["files"] if "FreshAnimations" in f["path"])
z = zipfile.ZipFile(io.BytesIO(get(fa["downloads"][0])))
note("fa-villager-files", "\n".join(n for n in z.namelist() if re.search(r"villager|wandering", n)))
v = next(n for n in z.namelist() if n.endswith("/villager.jem"))
text = z.read(v).decode("utf-8", "replace")
parts = re.findall(r'"(?:part|id)"\s*:\s*"([^"]+)"', text)
note("fa-villager-parts", ", ".join(parts))
anims = re.findall(r'"([a-z_0-9]+\.[a-z]{2,3})"\s*:\s*"', text)
note("fa-villager-anim-targets", ", ".join(sorted(set(anims)))[:3800])
