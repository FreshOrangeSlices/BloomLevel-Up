#!/bin/bash
# Throwaway: base attributes (health, damage, armor, speed) of the companion mods' entities. Pushed to ci/data (companions/attrs.txt).
set -u
mkdir -p data-out jars
python3 - <<'PY'
import json, urllib.request
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
for name, pid in [("zonis", "sZtlm7ZN"), ("jeff", "8E1z5d7y")]:
    v = get(f"https://api.modrinth.com/v2/project/{pid}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")[0]
    with urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers={"User-Agent": "bloom-ci"})) as r:
        open(f"jars/{name}.jar", "wb").write(r.read())
PY
declare -A A=([f_22276_]=health [f_22281_]=damage [f_22279_]=speed [f_22284_]=armor [f_22285_]=toughness [f_22277_]=follow [f_22278_]=kb_resist [f_22283_]=attack_speed [f_22282_]=attack_kb [f_22280_]=fly_speed)
for name in zonis jeff; do
  dir="x-$name"; mkdir -p "$dir" && (cd "$dir" && unzip -q -o "../jars/$name.jar")
  for c in $(cd "$dir" && grep -l -r --include='*.class' -a 'f_22276_' . | sort); do
    cls="$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')"
    line=$(javap -c -p -constants -cp "$dir" "$cls" 2>/dev/null | grep -E 'Attributes\.f_222[0-9]{2}_|ldc2_w|dconst|bipush|sipush' | sed -E 's/.*Attributes\.(f_[0-9]+_).*/\1/; s/.*ldc2_w.*\/\/ double ([0-9.E-]+)d?/=\1/; s/.*dconst_([0-9]).*/=\1/' | tr '\n' ' ')
    for k in "${!A[@]}"; do line="${line//$k/${A[$k]}}"; done
    echo "$name ${cls##*.}: $line"
  done
done > data-out/attrs.txt 2>&1
cat data-out/attrs.txt | head -80
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/companions && cp data-out/attrs.txt data/companions/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "companion attributes" && git push -q origin ci/data && echo pushed
