"""Throwaway: every LegionWareX mod's title, description and sides (Modrinth, and cfwidget for the CurseForge ones)."""
import json, time, urllib.request, urllib.error
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202:
                    time.sleep(6); continue
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(3)
        except Exception:
            time.sleep(3)
    return None
lock = json.load(open("bloom/pack/pack.lock.json"))
spec = json.load(open("bloom/pack/pack.json"))
listed = {slug: layer["name"] for layer in spec["layers"] for slug in layer.get("modrinth", [])}
out = []
for slug, version in sorted(lock["versions"].items()):
    p = get(f"https://api.modrinth.com/v2/project/{slug}")
    out.append({"slug": slug, "version": version, "layer": listed.get(slug, "dependency"), "title": p and p["title"],
                "description": p and p["description"], "type": p and p["project_type"], "client": p and p["client_side"],
                "server": p and p["server_side"], "categories": p and p["categories"], "url": f"https://modrinth.com/project/{slug}"})
for layer in spec["layers"]:
    for e in layer.get("external", []):
        cf = get("https://api.cfwidget.com/minecraft/mc-mods/" + e["page"].rstrip("/").split("/")[-1])
        out.append({"slug": e["name"], "version": e["file"], "layer": layer["name"], "title": e["name"],
                    "description": cf and cf.get("summary"), "type": "mod", "client": e["client"], "server": e["server"], "url": e["page"]})
json.dump(out, open("descriptions.json", "w"), indent=1)
print(len(out))
