#!/bin/bash
# Throwaway: which AzureLib has the AzBoneCache.setActiveModel Book of Dragons 1.31 calls.
set -u
mkdir -p j && cd j
curl -sSL -o bod.jar "https://cdn.modrinth.com/data/fJee0CPJ/versions/FMHtYIBA/bookofdragons-1.31-1.20.1.jar"
python3 - <<'PY'
import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
vs = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/azurelib/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))
for v in vs[:6]:
    f = v["files"][0]
    open(f"az-{v['version_number']}.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA)).read())
    print("azurelib", v["version_number"], v["date_published"][:10], f["filename"])
PY
mkdir -p bod && (cd bod && unzip -q -o ../bod.jar)
echo "== BoD mods.toml azurelib dep"; grep -A4 'modId *= *"azurelib"' bod/META-INF/mods.toml
echo "== BoD calls"; grep -rl "setActiveModel" --include=*.class bod | head -3
c=$(grep -rl "setActiveModel" --include=*.class bod | head -1); [ -n "$c" ] && javap -c -p "$c" | grep "setActiveModel" | head -3
for a in az-*.jar; do mkdir -p x-$a && (cd x-$a && unzip -q -o ../$a); k=$(find x-$a -name 'AzBoneCache.class' | head -1); echo "== $a: $(javap -p "$k" 2>/dev/null | grep -i 'setActiveModel' | tr '\n' ' ')"; done
