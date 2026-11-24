"""1. Where Fabled Weaponry uses its tomes. 2. Which Spell Engine add-ons have a Forge 1.20.1 build, and Wizards' spells."""
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
z = zipfile.ZipFile("fw.jar")
p("==== Fabled Weaponry: what names the tomes")
for n in z.namelist():
    b = z.read(n)
    if n.endswith(".json") and b"tome_" in b and "/recipes/tome_" not in n:
        p(f"  {n}: " + " ".join(b.decode("utf-8", "replace").split())[:500])
    if n.endswith(".class") and (b"TomeItem" in b or b"tome_" in b) and not n.endswith("TomeItem.class"):
        p(f"  class {n}")
        cls = n[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", "fw.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        text = r.stdout
        for m in re.finditer(r"TomeItem|tome_", text):
            start = text.rfind("\n    ", 0, m.start())
            p("    " + "\n    ".join(l[:200] for l in text[start:start + 1800].splitlines()))
            break
lang = json.loads(z.read([n for n in z.namelist() if n.endswith("lang/en_us.json")][0]))
for k, v in lang.items():
    if re.search(r"(?i)tooltip|desc|passive", k) and re.search(r"(?i)tome|book|spell|magic", str(v)):
        p(f"  lang {k} = {v}")

p("\n==== Forge 1.20.1 builds")
for slug in ("wizards", "archers", "jewelry", "runes", "gazebos", "elemental-wizards-rpg", "forcemaster-rpg-class", "berserker-rpg-class",
             "witcher-rpg-class", "bard-more-rpg-classes", "archers-expansion", "druids", "relics-rpg", "more-relics-rpg",
             "armory-rpg-series", "arsenal-rpg-series", "skill-tree", "lne-wizards", "more-rpg-library", "class-selection"):
    res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
    versions = json.loads(res) if res else []
    p(f"  {slug}: " + (f"yes, {versions[0]['version_number']}" if versions else "no Forge 1.20.1 build"))
    if slug == "wizards" and versions:
        f = next(x for x in versions[0]["files"] if x["primary"])
        wz = zipfile.ZipFile(io.BytesIO(get(f["url"])))
        wl = [n for n in wz.namelist() if n.endswith("lang/en_us.json")]
        wlang = json.loads(wz.read(wl[0])) if wl else {}
        spells = sorted(n for n in wz.namelist() if re.match(r"data/wizards/spells?/.*\.json$", n))
        p(f"  ---- Wizards spells ({len(spells)})")
        for n in spells:
            sp = json.loads(wz.read(n))
            sid = n.rsplit("/", 1)[-1][:-5]
            cast = sp.get("cast", {})
            cost = sp.get("cost", {})
            name = wlang.get(f"spell.wizards.{sid}.name", sid)
            p(f"    {name} ({sid}): school {sp.get('school')}, cast {cast.get('duration', 0)}s{' channelled' if cast.get('channel_ticks') else ''}, "
              f"cooldown {cost.get('cooldown_duration', 0)}s, rune {cost.get('item_id', cost.get('item', '-'))}, exhaust {cost.get('exhaust', 0)}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/magic2.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
