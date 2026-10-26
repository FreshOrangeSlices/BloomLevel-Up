"""Throwaway: Forge 1.20.1 promotions; the 1.20.1 builds of a few Modrinth projects (files, loaders); CurseForge Myths & Legends."""
import json, time, urllib.request, urllib.parse, urllib.error
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
def get(url):
    for _ in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202: time.sleep(8); continue
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(4)
        except Exception: time.sleep(4)
lines = []
promos = get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json") or {}
lines.append("forge promos: " + ", ".join(f"{k}={v}" for k, v in promos.get("promos", {}).items() if k.startswith("1.20.1")))
for slug in ["azurelib", "book-of-dragons", "myths-and-legends", "jei", "easy-mob-spawn-control", "polymorph"]:
    p = get(f"https://api.modrinth.com/v2/project/{slug}")
    q = urllib.parse.urlencode({"game_versions": json.dumps(["1.20.1"])})
    vs = get(f"https://api.modrinth.com/v2/project/{slug}/version?{q}") or []
    lines.append(f"== {slug} ({p['title'] if p else '?'}, team {p['team'] if p else '?'}): {len(vs)} 1.20.1 versions")
    for v in vs[:6]:
        files = "; ".join(f"{f['filename']}{'*' if f.get('primary') else ''}" for f in v["files"])
        lines.append(f"  {v['version_number']} [{v['version_type']}] {v['date_published'][:10]} loaders={v['loaders']} games={v['game_versions'][:4]} files={files}")
d = get("https://api.cfwidget.com/minecraft/mc-mods/myths-n-legends")
if d:
    fs = [f for f in d.get("files", []) if "1.20.1" in f.get("versions", [])]
    lines.append(f"== CF myths-n-legends '{d.get('title')}' by {[m.get('username') for m in d.get('members', [])]}: " + "; ".join(f"{f['id']} {f['name']} {f.get('uploaded_at','')[:10]} {f.get('versions')}" for f in sorted(fs, key=lambda f: f.get('uploaded_at',''), reverse=True)[:4]))
text = "\n".join(lines); print(text)
esc = lambda t: t.replace("%", "%25").replace("\n", "%0A")
for n, i in enumerate(range(0, len(text), 3800)):
    print(f"::notice title=find2-{n:02d}::{esc(text[i:i+3800])}")
