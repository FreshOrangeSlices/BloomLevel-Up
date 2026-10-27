#!/bin/bash
# Throwaway: how Better Mob Combat (and its mob Player Animator) hooks Mob, and why Legendary Monsters' knight breaks it.
set -u
mkdir -p jars x && cd jars
get() { python3 - "$1" "$2" <<'PY'
import json, sys, urllib.request
slug, ver = sys.argv[1], sys.argv[2]
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
vs = json.load(urllib.request.urlopen(urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))
v = next(v for v in vs if v["version_number"] == ver)
f = next(f for f in v["files"] if f["primary"]) if any(f["primary"] for f in v["files"]) else v["files"][0]
open(f"{slug}.jar", "wb").write(urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA)).read())
print(slug, f["filename"])
PY
}
get better-mob-combat 1.3.0; get mob-player-animator 1.3.3; get legendary-monsters 2.2.4
for j in better-mob-combat mob-player-animator legendary-monsters; do mkdir -p ../x/$j && (cd ../x/$j && unzip -q -o ../../jars/$j.jar); done
cd ../x
echo "== mixin configs"; for j in better-mob-combat mob-player-animator; do find $j -maxdepth 1 -name '*.json' | while read f; do echo "-- $f"; cat "$f"; echo; done; done
echo "== configs/defaults"; find better-mob-combat -iname '*config*' | head
echo "== classes touching Mob"; grep -rl "net/minecraft/world/entity/Mob\b\|net/minecraft/world/entity/Mob;" --include=*.class better-mob-combat mob-player-animator | head -20
for c in $(grep -rl "getAnimationStack" --include=*.class better-mob-combat mob-player-animator | head -8); do echo "== javap $c"; javap -p -c -constants "$c" | grep -n -i "getAnimationStack\|interface\|class \|post_init\|blacklist\|instanceof\|invoke" | head -40; done
echo "== knight"; k=$(find legendary-monsters -name 'BeheadedKnightEntity.class'); javap -p "$k" | head -40
echo "== knight getAnimationStack refs"; grep -rl "getAnimationStack\|playerAnim" --include=*.class legendary-monsters | head
for c in $(grep -rl "getAnimationStack" --include=*.class legendary-monsters | head -3); do echo "== javap $c"; javap -p "$c" | grep -i "anim\|interface\|class " | head -20; done
