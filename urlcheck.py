"""Throwaway: is a Modrinth file still served, and the project's Forge 1.20.1 versions. Usage: urlcheck.py <out dir>"""
import json, os, sys, urllib.request, urllib.error
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
lines = []
UA = {"User-Agent": "warionex-relay"}
url = "https://cdn.modrinth.com/data/O7RBXm3n/versions/CrtAI3P9/InventoryProfilesNext-forge-1.20-1.10.20.jar"
for attempt in range(3):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
            lines.append(f"cdn: {r.status} {len(r.read())} bytes")
            break
    except urllib.error.HTTPError as e:
        lines.append(f"cdn: HTTP {e.code} {e.reason}")
    except Exception as e:
        lines.append(f"cdn: {type(e).__name__} {e}")
try:
    data = json.loads(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/O7RBXm3n/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA), timeout=60).read())
    for v in data[:6]:
        f = next(x for x in v["files"] if x["primary"])
        lines.append(f"{v['version_number']} {v['id']} {v['date_published'][:10]} {v['version_type']} {f['url']}")
except Exception as e:
    lines.append(f"api: {e}")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(lines))
print("\n".join(lines))
