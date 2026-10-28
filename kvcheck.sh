#!/bin/bash
# Throwaway: what KeyVision (castmod 0.5.0) offers to get the normal controls list back: lang keys, config keys, class names.
set -u
mkdir -p kv data-out && cd kv
curl -sSfL -o kv.jar "https://cdn.modrinth.com/data/OirRngPn/versions/6lXXI9Zp/castmod-forge-1.20.1-0.5.0.jar"
unzip -q -o kv.jar -d x
{
  echo "== files"; (cd x && find . -type f | sort)
  echo "== lang"; find x -path '*lang/en_us.json' -exec cat {} \;
  echo "== mods.toml"; cat x/META-INF/mods.toml 2>/dev/null
  echo "== mixins"; find x -maxdepth 1 -name '*.json' -exec sh -c 'echo "-- $1"; cat "$1"' _ {} \;
  echo "== strings"; for c in $(find x -name '*.class'); do echo "-- $c"; strings -n 4 "$c" | grep -iE 'config|vanilla|legacy|classic|toggle|button|screen|KeyBinds|Controls|\.toml|\.json|define|comment' | sort -u | head -60; done
} > ../data-out/keyvision.txt 2>&1
cd ..
echo "::notice title=kv::$(wc -l < data-out/keyvision.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/kv && cp data-out/keyvision.txt data/kv/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "keyvision" && git push -q origin ci/data && echo pushed
