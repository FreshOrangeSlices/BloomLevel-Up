"""Throwaway: newest Forge 1.20.1 file of each CurseForge-only mod (through cfwidget), with CDN URLs that answer."""
import json, time, urllib.request, urllib.error
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
def get(url, binary=False):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202:
                    time.sleep(8); continue
                data = r.read()
                return data if binary else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(4)
        except Exception:
            time.sleep(4)
    return None
lines = []
for slug in ["skarrier-mobs", "touch-and-motion", "novums-altitude"]:
    data = get(f"https://api.cfwidget.com/minecraft/mc-mods/{slug}")
    files = [f for f in (data or {}).get("files", []) if "1.20.1" in f.get("versions", []) and any(v.lower() == "forge" for v in f.get("versions", []))]
    if not files:
        lines.append(f"{slug}: no Forge 1.20.1 file"); continue
    f = max(files, key=lambda f: f.get("uploaded_at", ""))
    fid, name = f["id"], f["name"]
    urls = [f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{name}", f"https://edge.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{name}"]
    ok = []
    for u in urls:
        d = get(u, binary=True)
        ok.append(f"{u} -> {'OK ' + str(len(d)) + ' bytes' if d and d[:2] == b'PK' else 'FAIL'}")
    lines.append(f"{slug}: '{data.get('title')}' file {fid} {name} ({f.get('uploaded_at','')[:10]}, {f.get('type')}) license={data.get('license')} page={data.get('urls',{}).get('curseforge')}\n  " + "\n  ".join(ok))
text = "\n".join(lines); print(text)
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
print(f"::notice title=cf-files::{esc(text)}")
