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
echo "== mixin configs"; for j in better-mob-combat mob-player-animator; do find $j -maxdepth 1 -name '*mixins.json' | while read f; do echo "-- $f"; python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print({k: v for k, v in d.items() if k in ('plugin','package')}, 'mixins:', [m for m in d.get('mixins', []) if 'Mob' in m or 'Living' in m])" "$f"; done; done
m=$(find better-mob-combat -name 'MobMixin_AttackAnimation.class'); echo "== javap $m"; javap -p -c -constants "$m" | grep -v "^ *[0-9]*: \(aload\|areturn\|return\|dup\|pop\|iload\|astore\|ldc\|getfield\|putfield\)" | head -60
p=$(find mob-player-animator -name '*MixinPlugin*.class' -o -name '*Plugin.class' | head -3); for c in $p; do echo "== javap $c"; javap -p -c -constants "$c" | grep -i "ldc\|invoke" | head -30; done
for c in $(find better-mob-combat mob-player-animator -name '*.class' | xargs grep -l "blacklist\|Blacklist" | head -5); do echo "== blacklist in $c"; javap -p -constants "$c" | head -20; strings "$c" | grep -i "black\|gecko\|azure" | head; done
echo "== configs/defaults"; find better-mob-combat -iname '*config*' | head
for c in $(grep -rl "getAnimationStack" --include=*.class better-mob-combat mob-player-animator | head -6); do echo "== $c"; javap -p "$c" | grep -i "getAnimationStack\|interface\|class " | head -6; done
echo "== knight"; k=$(find legendary-monsters -name 'BeheadedKnightEntity.class'); javap -p "$k" | head -40
echo "== knight getAnimationStack refs"; grep -rl "getAnimationStack\|playerAnim" --include=*.class legendary-monsters | head
for c in $(grep -rl "getAnimationStack" --include=*.class legendary-monsters | head -3); do echo "== javap $c"; javap -p "$c" | grep -i "anim\|interface\|class " | head -20; done
