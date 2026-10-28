#!/bin/bash
# Throwaway: Modrinth facts for mods FreshOrangeSlices asked about (Forge 1.20.1 builds, dependencies, what Every Compat covers).
set -u
mkdir -p data-out
python3 - <<'PY' > data-out/asked.txt 2>&1
import json, urllib.request, urllib.parse
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
API = "https://api.modrinth.com/v2"
for q in ["keybind atlas", "zoni's companions", "jeff the landshark", "every compat"]:
    hits = get(f"{API}/search?query={urllib.parse.quote(q)}&limit=4")["hits"]
    print("== search", q, [(h["slug"], h["project_id"], h["title"], h["downloads"]) for h in hits])
for pid in ["fokFjwwh", "8E1z5d7y", "every-compat"] + [h["project_id"] for h in get(f"{API}/search?query=zoni%27s%20companions&limit=2")["hits"]]:
    p = get(f"{API}/project/{pid}")
    print("\n==", p["title"], p["slug"], p["id"], "downloads", p["downloads"], "updated", p["updated"], "client", p["client_side"], "server", p["server_side"])
    body = p.get("body") or ""
    for word in ["Handcrafted", "Decorative Blocks", "Biomes O", "Moonlight", "GeckoLib", "K key", "default key", "conflict", "Marvel", "catch", "boss"]:
        i = body.find(word)
        if i >= 0:
            print(f"  [{word}] ...{body[max(0,i-150):i+200].replace(chr(10),' ')}...")
    vs = get(f"{API}/project/{p['id']}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    for v in vs[:3]:
        deps = [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]]
        print("  version", v["version_number"], v["version_type"], v["date_published"][:10], v["files"][0]["filename"], "deps", deps)
    if not vs:
        print("  NO forge 1.20.1 version")
PY
echo "::notice title=asked::$(head -c 3500 data-out/asked.txt | tr '\n' ' ' | sed 's/%/%25/g')"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/asked && cp data-out/asked.txt data/asked/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "asked mods" && git push -q origin ci/data && echo pushed
