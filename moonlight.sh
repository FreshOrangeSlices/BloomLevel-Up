#!/bin/bash
# Throwaway: Moonlight Lib's 1.20.1 Forge versions and their files (primary flags).
python3 - <<'PY'
import json, urllib.request
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
vs = get("https://api.modrinth.com/v2/project/twkfQtEc/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
out = []
for v in vs[:6]:
    out.append(f"{v['version_number']} {v['version_type']} {v['date_published'][:10]} " + " ".join(f"{f['filename']}{'*' if f.get('primary') else ''}({f['size']})" for f in v["files"]))
print("\n".join(out))
open("moon.txt", "w").write("\n".join(out))
PY
echo "::notice title=moonlight::$(tr '\n' '|' < moon.txt)"
