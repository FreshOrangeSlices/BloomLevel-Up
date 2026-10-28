#!/bin/bash
# Throwaway: Keybind Atlas 2.0.0's key names and defaults.
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
vs = get("https://api.modrinth.com/v2/project/fokFjwwh/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
v = next(x for x in vs if x["version_number"] == "2.0.0")
z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], raw=True)))
for n in z.namelist():
    if n.endswith("en_us.json"):
        print("lang keys:", [k for k in json.loads(z.read(n)) if k.startswith("key.")])
    if n.endswith(".class"):
        b = z.read(n)
        if b"KeyMapping" in b or b"m_90837_" in b:
            print(n, sorted(set(m.decode() for m in re.findall(rb"key\.[a-z_.]+", b))))
PY
cat out.txt
echo "::notice title=ka::$(tr '\n' '|' < out.txt)"
