#!/bin/bash
# Throwaway: KeySet 2.0.0 (Forge 1.20.1): what its auto-switch rules match on, Forge key modifier handling, changelog.
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re, subprocess, os
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
vs = get("https://api.modrinth.com/v2/project/ku4IcLJb/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
v = vs[0]
print("changelog:", (v.get("changelog") or "")[:1800].replace("\n", " / "))
z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], raw=True)))
os.makedirs("x", exist_ok=True); z.extractall("x")
for n in z.namelist():
    if n.endswith("en_us.json"):
        l = json.loads(z.read(n))
        print("auto lang:", {k: v for k, v in l.items() if "auto" in k.lower() or "rule" in k.lower() or "held" in k.lower() or "item" in k.lower()})
mod = [n for n in z.namelist() if n.endswith(".class") and b"KeyModifier" in z.read(n)]
print("classes using Forge KeyModifier:", [m.rsplit("/", 1)[-1] for m in mod][:10])
for n in z.namelist():
    if "AutoSwitch" in n and n.endswith(".class"):
        cls = n[:-6].replace("/", ".")
        t = subprocess.run(["javap", "-p", "-constants", "-c", "-cp", "x", cls], capture_output=True, text=True).stdout
        fields = re.findall(r"^\s+(?:private|public|protected|final|static|\s)*[\w.<>,$\[\] ]+ (\w+);$", t, re.M)
        strings = sorted(set(re.findall(r"// String (.+)$", t, re.M)))
        print(cls.rsplit(".", 1)[-1], "fields:", fields[:20], "strings:", strings[:30])
PY
cat out.txt
echo "::notice title=keyset::$(head -c 3900 out.txt | tr '\n' '|' | sed 's/%/%25/g')"
