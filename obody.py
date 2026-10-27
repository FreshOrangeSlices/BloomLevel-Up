import json, re, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
out = {}
for slug in ["origins-forge", "rpg-origins", "simply-fantastical-origins", "fantasy-origin-pack-origin-addon", "runic-races", "origins-plus-plus",
             "medieval-origins-revival", "dungeons-and-dragons-origins", "runeblood-origin", "origins-classes-forge", "gods-heroes-rpg-classes",
             "altorigingui", "magic-origins", "mythic-origins", "pehkui", "caelus", "icarus"]:
    try:
        p = json.load(urllib.request.urlopen(urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}", headers=UA), timeout=60))
        body = re.sub(r"!\[[^\]]*\]\([^)]*\)|<[^>]+>", " ", p.get("body", ""))
        body = re.sub(r"\s+", " ", body)
        out[slug] = {"title": p["title"], "downloads": p["downloads"], "updated": p["updated"][:10], "body": body[:2200],
                     "client": p["client_side"], "server": p["server_side"], "source": p.get("source_url"), "issues": p.get("issues_url")}
    except Exception as e:
        out[slug] = {"error": str(e)}
json.dump(out, open("origins-bodies.json", "w"), indent=1)
