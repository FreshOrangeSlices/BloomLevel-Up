#!/bin/bash
# Throwaway: Keybind Atlas's 1.20.1 Forge versions, and whether each jar is remapped for a real game (2.1.0 crashes:
# its reload listener lambda names onResourceManagerReload instead of m_6213_).
mkdir -p jars
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
vs = get("https://api.modrinth.com/v2/project/fokFjwwh/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
for v in vs[:8]:
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    data = get(f["url"], raw=True)
    z = zipfile.ZipFile(io.BytesIO(data))
    named = srg = 0
    for n in z.namelist():
        if n.endswith(".class"):
            b = z.read(n)
            named += len(re.findall(rb"onResourceManagerReload|getMainHandItem|setScreen\b|keyPressed\b", b))
            srg += len(re.findall(rb"m_[0-9]{4,6}_|f_[0-9]{4,6}_", b))
    print(v["version_number"], v["version_type"], v["date_published"][:10], f["filename"], "mojang-named refs:", named, "srg refs:", srg)
PY
cat out.txt
echo "::notice title=ka::$(tr '\n' '|' < out.txt)"
