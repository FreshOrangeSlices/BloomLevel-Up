"""Wizards' spells: cast time, channel, cooldown, rune and hunger, read from the right fields; and Elemental Wizards' too."""
import io, json, os, re, urllib.parse, urllib.request, zipfile

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
for slug, ns in (("wizards", "wizards"), ("elemental-wizards-rpg", None)):
    res = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=" + urllib.parse.quote('["forge"]') + "&game_versions=" + urllib.parse.quote('["1.20.1"]'))
    v = json.loads(res)[0]
    f = next(x for x in v["files"] if x["primary"])
    wz = zipfile.ZipFile(io.BytesIO(get(f["url"])))
    langs = {}
    for n in wz.namelist():
        if n.endswith("lang/en_us.json"):
            try:
                langs.update(json.loads(wz.read(n)))
            except Exception:
                pass
    spells = sorted(n for n in wz.namelist() if re.match(r"data/[^/]+/spells?/.*\.json$", n))
    p(f"==== {slug} {v['version_number']}: {len(spells)} spells")
    for n in spells:
        sp = json.loads(wz.read(n))
        namespace = n.split("/")[1]
        sid = n.rsplit("/", 1)[-1][:-5]
        cast = sp.get("active", {}).get("cast", {})
        cost = sp.get("cost", {})
        cd = cost.get("cooldown", {})
        item = cost.get("item", {})
        name = langs.get(f"spell.{namespace}.{sid}.name", sid)
        p(f"  {name}: tier {sp.get('tier')}, {sp.get('school', '').split(':')[-1]}, cast {cast.get('duration', 0)}s"
          f"{' channel x' + str(cast.get('channel_ticks')) if cast.get('channel_ticks') else ''}, cooldown {cd.get('duration', 0) if isinstance(cd, dict) else cd}s, "
          f"rune {item.get('id', '-') if isinstance(item, dict) else item}, hunger {cost.get('exhaust', 0)}, group {sp.get('group', '-')}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/magic4.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
