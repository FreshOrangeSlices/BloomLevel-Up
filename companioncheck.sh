#!/bin/bash
# Throwaway: what Zoni's Companions and Jeff the Landshark add (wild spawns, structures, loot, recipes, config, what the
# Advanced Summon Device refuses), for FreshOrangeSlices' question. Results pushed to ci/data (companions/).
set -u
mkdir -p data-out jars
python3 - <<'PY' > data-out/fetch.txt 2>&1
import json, urllib.request
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
API = "https://api.modrinth.com/v2"
for name, pid in [("zonis", "sZtlm7ZN"), ("jeff", "8E1z5d7y")]:
    vs = get(f"{API}/project/{pid}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    v = vs[0]
    f = v["files"][0]
    print(name, v["version_number"], f["filename"], [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]])
    with urllib.request.urlopen(urllib.request.Request(f["url"], headers={"User-Agent": "bloom-ci"})) as r:
        open(f"jars/{name}.jar", "wb").write(r.read())
    print("  changelog:", (v.get("changelog") or "")[:1500].replace("\n", " | "))
    body = get(f"{API}/project/{pid}")["body"] or ""
    print("  body:", body[:4000].replace("\n", " | "))
for pid in ["nU0bVIaL"]:
    p = get(f"{API}/project/{pid}")
    print("dep", pid, p["title"], p["slug"], p["downloads"], p["client_side"], p["server_side"])
PY
cat data-out/fetch.txt

for name in zonis jeff; do
  out="data-out/$name.txt"
  dir="x-$name"
  mkdir -p "$dir" && (cd "$dir" && unzip -q -o "../jars/$name.jar")
  {
    echo "== files outside classes and textures"
    (cd "$dir" && find . -type f ! -name '*.class' ! -name '*.png' ! -path './assets/*/geo/*' ! -path './assets/*/animations/*' | sort)
    echo
    echo "== data json (spawns, worldgen, loot, tags, advancements' triggers excluded)"
    for f in $(cd "$dir" && find ./data -type f -name '*.json' | grep -v -E '/advancements/' | sort); do
      echo "--- $f"; head -c 1500 "$dir/$f"; echo
    done
    echo
    echo "== lang (entities, items)"
    for f in $(cd "$dir" && find ./assets -name 'en_us.json'); do head -c 6000 "$dir/$f"; echo; done
    echo
    echo "== class strings of interest"
    for c in $(cd "$dir" && find . -name '*.class' | sort); do
      s=$(strings -n 4 "$dir/$c" | grep -E -i 'SpawnPlacement|addSpawn|BiomeModifier|MobSpawnSettings|SpawnPlacementRegister|FinalizeSpawn|EntityJoinLevel|VillagerTrades|WandererTrades|LootTableLoad|ForgeConfigSpec|isBoss|boss|BOSS|canChangeDimensions|blacklist|Blacklist|SpawnEgg|naturalSpawn|checkSpawn|ServerBossEvent|addSpawnBiome|spawnCost' | sort -u | tr '\n' ' ')
      [ -n "$s" ] && echo "$c: $s"
    done
    echo
    echo "== config keys (ldc strings in classes named *Config*)"
    for c in $(cd "$dir" && find . -iname '*config*.class'); do
      echo "--- $c"; javap -c -p -cp "$dir" "$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')" 2>/dev/null | grep -E 'ldc|String ' | grep -o '// String.*' | sort -u | head -80
    done
    echo
    echo "== summon / capture / device classes"
    for c in $(cd "$dir" && find . -name '*.class' | grep -i -E 'summon|device|capture|catch|pendant|advanced|jeff|tame|trust' | sort); do
      cls="$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')"
      echo "--- $cls"
      javap -c -p -cp "$dir" "$cls" 2>/dev/null | grep -E '^  [a-z].*\(|invoke|instanceof|ldc|getstatic|checkcast' | grep -v -E 'java/lang/(StringBuilder|Object\.<init>)' | head -220
    done
  } > "$out" 2>&1
  echo "$name: $(wc -c < "$out") bytes"
done

echo "::notice title=companions::$(head -c 3000 data-out/fetch.txt | tr '\n' ' ' | sed 's/%/%25/g')"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/companions && cp data-out/*.txt data/companions/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "companion mods" && git push -q origin ci/data && echo pushed
