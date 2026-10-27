#!/bin/bash
set -u
mkdir -p j && cd j
python3 - <<'PY'
import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
vs = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/myths-and-legends/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))
for v in vs[:5]:
    print("M&L", v["version_number"], v["version_type"], v["date_published"][:10], (v.get("changelog") or "")[:300].replace("\n", " | "))
v = next(v for v in vs if v["version_number"] == "1.0.6")
open("ml.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers=UA)).read())
if vs[0]["version_number"] != "1.0.6":
    open("ml-new.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(vs[0]["files"][0]["url"], headers=UA)).read())
PY
for jar in ml ml-new; do [ -f $jar.jar ] || continue; mkdir -p $jar && (cd $jar && unzip -q -o ../$jar.jar); c=$(find $jar -name 'CogworkConstructRenderer.class'); echo "== $jar CogworkConstructRenderer"; javap -c -l -p "$c" | sed -n '/public void render\|m_7392_/,/LineNumberTable/p' | head -45; done
