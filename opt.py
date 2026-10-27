import json, urllib.parse, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(path, **p):
    q = "&".join(f"{k}={urllib.parse.quote(v if isinstance(v, str) else json.dumps(v))}" for k, v in p.items())
    return json.load(urllib.request.urlopen(urllib.request.Request(f"https://api.modrinth.com/v2{path}?{q}", headers=UA), timeout=60))
out = []
for h in get("/search", query="let me despawn", limit="5")["hits"]:
    out.append(f"search: {h['slug']} | {h['title']} | {h['downloads']:,} | {[c for c in h['categories'] if c in ('forge','neoforge','fabric')]} | {h['versions'][-5:]}")
for slug in ["immediatelyfast", "entityculling", "ai-improvements", "chunky", "spark"]:
    for v in get(f"/project/{slug}/version", game_versions=["1.20.1"], loaders=["forge"])[:6]:
        out.append(f"{slug}: {v['version_number']} [{v['version_type']}] {v['date_published'][:10]} games={v['game_versions']} loaders={v['loaders']}")
text = "\n".join(out); print(text)
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=out-{n:02d}::" + text[i:i+3900].replace("%", "%25").replace("\n", "%0A"))
