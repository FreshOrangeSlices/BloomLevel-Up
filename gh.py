import json, re, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(u):
    return json.load(urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60))
out = {}
for slug in ["gods-heroes-rpg-classes", "moraks-medieval-races", "gods-and-heroes", "apothic-attributes", "too-many-bows"]:
    try:
        p = get(f"https://api.modrinth.com/v2/project/{slug}")
        vs = get(f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.20.1%22%5D")
        body = re.sub(r"\s+", " ", re.sub(r"!\[[^\]]*\]\([^)]*\)|<[^>]+>", " ", p.get("body", "")))
        names = {}
        vinfo = []
        for v in vs[:4]:
            deps = []
            for d in v["dependencies"]:
                if d.get("project_id"):
                    if d["project_id"] not in names:
                        try: names[d["project_id"]] = get(f"https://api.modrinth.com/v2/project/{d['project_id']}")["slug"]
                        except Exception: names[d["project_id"]] = d["project_id"]
                    deps.append((names[d["project_id"]], d["dependency_type"]))
            vinfo.append({"v": v["version_number"], "date": v["date_published"][:10], "loaders": v["loaders"], "deps": deps,
                          "files": [(f["filename"], f["size"]) for f in v["files"]], "changelog": (v.get("changelog") or "")[:400]})
        out[slug] = {"title": p["title"], "downloads": p["downloads"], "updated": p["updated"][:10], "body": body[:6000], "versions": vinfo}
    except Exception as e:
        out[slug] = {"error": str(e)}
json.dump(out, open("gods-heroes.json", "w"), indent=1)
