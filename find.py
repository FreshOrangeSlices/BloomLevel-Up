"""Throwaway: where MrCrayfish's Framework lives (Modrinth search, then CurseForge through cfwidget)."""
import json, time, urllib.request, urllib.parse, urllib.error
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
def get(url, binary=False):
    for _ in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202: time.sleep(8); continue
                d = r.read(); return d if binary else json.loads(d)
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(4)
        except Exception: time.sleep(4)
lines = []
q = urllib.parse.urlencode({"query": "framework", "limit": 15, "facets": json.dumps([["categories:forge"], ["versions:1.20.1"]])})
for h in (get(f"https://api.modrinth.com/v2/search?{q}") or {}).get("hits", []):
    lines.append(f"MR hit: {h['title']} | slug {h['slug']} | by {h['author']} | {h['project_type']}")
for slug in ["framework", "mrcrayfish-framework", "framework-mrcrayfish"]:
    p = get(f"https://api.modrinth.com/v2/project/{slug}")
    lines.append(f"MR {slug}: {p['title'] + ' by team ' + p['team'] if p else 'none'}")
data = get("https://api.cfwidget.com/minecraft/mc-mods/framework")
if data:
    files = [f for f in data.get("files", []) if "1.20.1" in f.get("versions", []) and any(v.lower() == "forge" for v in f.get("versions", []))]
    f = max(files, key=lambda f: f.get("uploaded_at", "")) if files else None
    if f:
        fid, name = f["id"], f["name"]
        url = f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{urllib.parse.quote(name)}"
        d = get(url, binary=True)
        lines.append(f"CF '{data.get('title')}' by {data.get('members', [{}])[0].get('username') if data.get('members') else '?'}: {fid} {name} ({f.get('uploaded_at','')[:10]}) {url} -> {'OK' if d and d[:2]==b'PK' else 'FAIL'}")
    else:
        lines.append(f"CF '{data.get('title')}': no Forge 1.20.1 file")
text = "\n".join(lines); print(text)
print("::notice title=find::" + text.replace("%", "%25").replace("\n", "%0A"))
