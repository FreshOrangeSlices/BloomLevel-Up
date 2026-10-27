#!/bin/bash
set -u
mkdir -p j && cd j
python3 - <<'PY'
import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
v = next(v for v in json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/legionary/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA))) if v["version_number"] == "1.2.2")
open("legionary.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers=UA)).read())
PY
mkdir -p x && (cd x && unzip -q -o ../legionary.jar)
c=$(find x -name 'FlamingSkull.class'); echo "== FlamingSkull.tick"; javap -c -l -p "$c" | sed -n '/m_8119_\|void tick/,/LocalVariableTable\|^  [a-z].*(/p' | head -80
echo "== who makes FlamingSkull"; grep -rl "FlamingSkull" --include=*.class x | grep -v "FlamingSkull" | head
echo "== other client classes referenced from common code"; for f in $(grep -rl "net/minecraft/client/" --include=*.class x | grep -v "/client/" | head -20); do echo "$f: $(javap -c -p "$f" | grep -o 'net/minecraft/client/[A-Za-z/]*' | sort -u | head -4 | tr '\n' ' ')"; done
