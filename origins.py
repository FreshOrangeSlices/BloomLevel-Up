"""Throwaway: Origins for Forge 1.20.1 and its add-ons (Modrinth search, plus CurseForge's Forge port)."""
import json, time, urllib.parse, urllib.request, urllib.error
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
def mr(path, **p):
    q = "&".join(f"{k}={urllib.parse.quote(v if isinstance(v, str) else json.dumps(v))}" for k, v in p.items())
    return get(f"https://api.modrinth.com/v2{path}" + (f"?{q}" if q else ""))
names = {}
def slug_of(pid):
    if pid not in names:
        p = mr(f"/project/{pid}")
        names[pid] = p["slug"] if p else pid
    return names[pid]
seen, out = set(), []
queries = ["origins", "origin", "origins addon", "origins add-on", "origins classes", "classes origins", "medieval origins",
           "fantasy origins", "mythic origins", "origins races", "origins expansion", "origins plus", "more origins", "origins forge"]
for q in queries:
    for loaders in (["forge"], ["datapack"]):
        r = mr("/search", query=q, facets=[["versions:1.20.1"], [f"categories:{loaders[0]}"]], limit="40") or {"hits": []}
        for h in r["hits"]:
            text = (h["title"] + " " + h["description"]).lower()
            if "origin" not in text or h["slug"] in seen:
                continue
            seen.add(h["slug"])
            vs = mr(f"/project/{h['slug']}/version", game_versions=["1.20.1"]) or []
            forge = [v for v in vs if "forge" in v["loaders"] or "datapack" in v["loaders"]]
            v = forge[0] if forge else None
            deps = [(slug_of(d["project_id"]), d["dependency_type"]) for d in (v or {}).get("dependencies", []) if d.get("project_id")]
            out.append({"slug": h["slug"], "title": h["title"], "type": h["project_type"], "downloads": h["downloads"],
                        "updated": h["date_modified"][:10], "desc": h["description"][:220],
                        "v": v and v["version_number"], "vdate": v and v["date_published"][:10], "loaders": v and v["loaders"],
                        "deps": deps, "allloaders": sorted({l for x in vs for l in x["loaders"]})})
# CurseForge: the Forge port and the best-known add-ons there
cf = {}
for slug in ["origins-forge", "medieval-origins-revival", "medieval-origins", "origins-classes", "origins-plus-plus", "extra-origins",
             "mythic-origins", "origins-legacy", "origins-forge-legacy", "fantasy-origins", "rpg-origins", "the-origins-of-fantasy"]:
    d = get(f"https://api.cfwidget.com/minecraft/mc-mods/{slug}")
    if d and "files" in d:
        f20 = [f for f in d["files"] if "1.20.1" in f.get("versions", [])]
        newest = max(f20, key=lambda f: f.get("uploaded_at", "")) if f20 else None
        cf[slug] = {"title": d.get("title"), "downloads": d.get("downloads", {}).get("total"), "summary": d.get("summary", "")[:200],
                    "newest_1201": newest and (newest["name"], newest.get("uploaded_at", "")[:10], [x for x in newest["versions"] if x in ("Forge", "NeoForge", "Fabric", "Quilt")])}
    else:
        cf[slug] = None
json.dump({"modrinth": sorted(out, key=lambda x: -x["downloads"]), "curseforge": cf}, open("origins.json", "w"), indent=1)
print(len(out), "modrinth projects")
