#!/bin/bash
set -u
mkdir -p j && cd j
python3 - <<'PY'
import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
vs = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/myths-and-legends/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))
v = next(v for v in vs if v["version_number"] == "1.0.6")
open("ml.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers=UA)).read())
PY
mkdir -p ml && (cd ml && unzip -q -o ../ml.jar)
echo "== data files naming the cogwork construct"; grep -rl "cogwork" ml/data ml/assets/mythsandlegends/lang 2>/dev/null | head -20
echo "== structures (nbt) with it"; for f in $(find ml/data -name '*.nbt'); do python3 -c "import gzip,sys; d=gzip.open(sys.argv[1]).read(); print(sys.argv[1]) if b'cogwork' in d else None" "$f"; done | head
echo "== classes that create it"; grep -rl "COGWORK_CONSTRUCT\|CogworkConstructEntity" --include=*.class ml/com | grep -v "CogworkConstruct" | head -12
echo "== lang"; python3 -c "import json; d=json.load(open('ml/assets/mythsandlegends/lang/en_us.json')); [print(k, '=', v[:140]) for k, v in d.items() if 'cogwork' in k.lower() or 'cogwork' in str(v).lower()]" | head -12
echo "== config keys"; for c in $(grep -rl "cogwork\|Cogwork" --include=*.class ml/com | grep -i config | head -3); do strings $c | grep -i cog | head; done
