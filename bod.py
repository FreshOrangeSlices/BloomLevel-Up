import io, json, re, sys, urllib.parse, urllib.request, zipfile
H = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60))
out = []
def deps_of(jar):
    z = zipfile.ZipFile(io.BytesIO(jar))
    names = [n for n in z.namelist() if n.endswith("mods.toml")]
    res = []
    for n in names:
        t = z.read(n).decode(errors="replace")
        ids = re.findall(r'^\s*modId\s*=\s*"([^"]+)"', t, re.M)
        res.append(f"{n}: modIds={ids[:3]}")
        for block in re.findall(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\n\[\[|\Z)", t, re.S):
            res.append("   " + " ".join(l.strip() for l in block.strip().splitlines() if l.strip() and not l.strip().startswith("#")))
    jij = [n for n in z.namelist() if n.startswith("META-INF/jarjar/") and n.endswith(".jar")]
    if jij: res.append(f"   jar-in-jar: {jij}")
    return res
vs = get("https://api.modrinth.com/v2/project/book-of-dragons/version?loaders=%5B%22forge%22%5D")
v = vs[0]
out.append(f"BoD {v['version_number']} changelog: {(v.get('changelog') or '')[:600]}")
f = v["files"][0]
out += deps_of(urllib.request.urlopen(urllib.request.Request(f["url"], headers=H), timeout=120).read())
for d in v["dependencies"]:
    p = get(f"https://api.modrinth.com/v2/project/{d['project_id']}")
    dv = get(f"https://api.modrinth.com/v2/version/{d['version_id']}") if d.get("version_id") else None
    out.append(f"dep {p['slug']} ({p['title']}, {p['downloads']} dl, {p['description'][:100]}) version {dv and dv['version_number']} games={dv and dv['game_versions']} loaders={dv and dv['loaders']}")
    if dv:
        out += deps_of(urllib.request.urlopen(urllib.request.Request(dv["files"][0]["url"], headers=H), timeout=120).read())
        out.append(f"   its deps: {[(x.get('project_id'), x['dependency_type']) for x in dv['dependencies']]}")
# azurelib versions and which locked pack mods need azurelib
az = get("https://api.modrinth.com/v2/project/azurelib")
avs = get("https://api.modrinth.com/v2/project/azurelib/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D")
out.append("azurelib forge 1.20.1: " + ", ".join(x["version_number"] for x in avs[:8]))
lock = json.load(open(sys.argv[1]))["versions"]
for slug, ver in sorted(lock.items()):
    try:
        cand = get(f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.20.1%22%5D")
        lv = next((c for c in cand if c["version_number"] == ver), None)
        if not lv:
            out.append(f"lock {slug} {ver}: NOT FOUND on Modrinth now"); continue
        if any(d.get("project_id") == az["id"] for d in lv["dependencies"]):
            out.append(f"lock {slug} {ver} depends on azurelib")
    except Exception as e:
        out.append(f"lock {slug}: error {e}")
text = "\n".join(out); print(text)
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=bod-{n:02d}::" + text[i:i+3900].replace("%", "%25").replace("\n", "%0A"))
