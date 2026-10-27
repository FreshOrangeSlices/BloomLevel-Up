#!/bin/bash
set -u
mkdir -p j && cd j
python3 - <<'PY'
import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
v = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/oculus/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))[0]
open("oc.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers=UA)).read())
PY
mkdir -p oc && (cd oc && unzip -q -o ../oc.jar)
I=$(find oc -path '*irisshaders/iris/Iris.class' | head -1); C=$(find oc -name 'IrisConfig.class' | head -1)
echo "== Iris methods"; javap -p "$I" | grep -i "init\|load\|config\|static void\|public Iris" | head -30
echo "== Iris: who calls IrisConfig and properties files"; javap -c -p "$I" | grep -n "oculus.properties\|iris.properties\|IrisConfig\.\|Method .*\(initialize\|save\|setShaderPackName\|setShadersEnabled\)\|^  [a-z].*(" | head -60
echo "== IrisConfig"; javap -c -p "$C" | grep -n "^  [a-z].*(\|Method\|ldc" | head -60
echo "== who calls onEarlyInitialize / onInitialize"; grep -rl "onEarlyInitialize\|onInitializeClient\|onInitialize" --include=*.class oc | head; for c in $(grep -rl "onEarlyInitialize" --include=*.class oc | grep -v '/Iris.class' | head -4); do echo "-- $c"; javap -c -p "$c" | grep -n "onEarlyInitialize\|^  [a-z].*(\|@" | head -12; done
echo "== mixin config plugin"; ls oc/*.json; python3 -c "import json,glob; [print(f, json.load(open(f)).get('plugin')) for f in glob.glob('oc/*.mixins.json') + glob.glob('oc/mixins*.json')]"
