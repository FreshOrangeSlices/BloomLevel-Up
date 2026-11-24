"""1. Fabled Weaponry's tome: what it is and does. 2. Spell Engine content for Forge 1.20.1 on Modrinth, and Wizards' spells
(cast time, cooldown, cost) as its own files have them."""
import json, os, re, subprocess, urllib.parse, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (spell engine check)"}


def get(url):
    for _ in range(3):
        try:
            return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
        except Exception as e:
            err = e
    print("failed", url, err)
    return None


out = []
p = out.append

# 1. Fabled Weaponry
jars = json.load(open("probe/alljars.json"))
url = jars["cf:fabledweaponry-1.20.1-1.0.7.5.jar"]
data = None
for u in (url if isinstance(url, list) else [url]):
    data = get(u)
    if data:
        break
open("fw.jar", "wb").write(data)
z = zipfile.ZipFile("fw.jar")
names = z.namelist()
lang_name = [n for n in names if n.endswith("lang/en_us.json")]
lang = json.loads(z.read(lang_name[0]).decode("utf-8", "replace")) if lang_name else {}
p("==== Fabled Weaponry: lang lines about a tome or book")
for k, v in lang.items():
    if re.search(r"(?i)tome|book|grimoire|codex", k + " " + str(v)):
        p(f"  {k} = {v}")
classes = [n for n in names if n.endswith(".class") and re.search(r"(?i)tome|book", n.rsplit("/", 1)[-1])]
p(f"classes: {classes}")
for n in classes[:6]:
    cls = n[:-6].replace("/", ".")
    r = subprocess.run(["java", "-jar", "cfr.jar", "fw.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    body = [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
    p(f"---- {cls}\n" + "\n".join(l[:220] for l in body)[:6000])
for n in names:
    if re.search(r"(?i)tome|book", n) and n.endswith(".json") and "/recipes/" in n:
        p(f"recipe {n}: " + " ".join(z.read(n).decode().split())[:800])

# 2. Spell Engine content for Forge 1.20.1
p("\n==== Modrinth: Spell Engine content for Forge 1.20.1")
facets = json.dumps([["categories:forge"], ["versions:1.20.1"]])
for query in ("spell engine", "rpg series", "spell power"):
    res = get("https://api.modrinth.com/v2/search?limit=40&query=" + urllib.parse.quote(query) + "&facets=" + urllib.parse.quote(facets))
    if res:
        for hit in json.loads(res)["hits"]:
            p(f"  [{query}] {hit['slug']}: {hit['title']} ({hit['downloads']} downloads) - {hit['description'][:140]}")
for slug in ("wizards", "archers", "spell-engine", "elemental-wizards", "jewelry", "runes", "relics-rpgs", "arsenal"):
    res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
    versions = json.loads(res) if res else []
    p(f"  {slug}: {len(versions)} Forge 1.20.1 versions" + (f", newest {versions[0]['version_number']}, needs " + str([d.get('project_id') for d in versions[0]['dependencies'] if d['dependency_type'] == 'required']) if versions else ""))
    if slug == "wizards" and versions:
        f = next(x for x in versions[0]["files"] if x["primary"])
        wz = zipfile.ZipFile(__import__("io").BytesIO(get(f["url"])))
        p(f"  ---- Wizards {versions[0]['version_number']} spells")
        for n in sorted(wz.namelist()):
            if re.match(r"data/wizards/spells/.*\.json$", n):
                sp = json.loads(wz.read(n))
                cast = sp.get("cast", {})
                cost = sp.get("cost", {})
                p(f"    {n.rsplit('/', 1)[-1][:-5]}: school {sp.get('school')}, cast {cast.get('duration', 0)}s {'channel' if cast.get('channel_ticks') else ''}, cooldown {cost.get('cooldown_duration', 0)}s, rune {cost.get('item_id', '-')}, exhaust {cost.get('exhaust', 0)}")
        wl = [n for n in wz.namelist() if n.endswith("lang/en_us.json")]
        if wl:
            wlang = json.loads(wz.read(wl[0]))
            for k, v in wlang.items():
                if k.startswith("spell.wizards.") and k.count(".") == 2:
                    p(f"    name {k} = {v}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/magic.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
