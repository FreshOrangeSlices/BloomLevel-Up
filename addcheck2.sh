#!/bin/bash
# Throwaway: JEI's hidden-items tag, Zoni's full item list and its uncraftable tools, Open Loader's folders. Pushed to ci/data (adding/).
set -u
mkdir -p data-out jars
python3 - <<'PY'
import json, urllib.request
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
for name, pid, ver in [("zonis", "sZtlm7ZN", None), ("jei", "u6dRKJwZ", "15.74.0.230"), ("open-loader", "KwWsINvD", None)]:
    vs = get(f"https://api.modrinth.com/v2/project/{pid}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    v = next((x for x in vs if ver is None or x["version_number"] == ver), vs[0])
    with urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers={"User-Agent": "bloom-ci"})) as r:
        open(f"jars/{name}.jar", "wb").write(r.read())
PY
for n in zonis jei open-loader; do mkdir -p "x-$n" && (cd "x-$n" && unzip -q -o "../jars/$n.jar"); done
{
  echo "== JEI classes naming the hidden tag"
  for c in $(cd x-jei && grep -l -r -a --include='*.class' -E 'hidden_from_recipe_viewers|HIDDEN_FROM_RECIPE_VIEWERS' . | sort); do
    cls="$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')"; echo "--- $cls"
    javap -c -p -constants -cp x-jei "$cls" | grep -E 'ldc|TagKey|ResourceLocation|Tags|HIDDEN' | head -20
  done
  echo "== Zoni's item registrations"
  javap -c -p -constants -cp x-zonis net.zoni.zoniscompanions.init.ZonisCompanionsModItems | grep -o -E 'String [a-z0-9_]+' | sort -u | tr '\n' ' '
  echo; echo "== Zoni's whip, ruler, repel and what uses them"
  for c in $(cd x-zonis && grep -l -r -a --include='*.class' -E 'WHIP|RULER|REPEL|WhipItem|RulerItem|RepelItem' . | sort); do
    cls="$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')"; echo "--- $cls"
    javap -c -p -cp x-zonis "$cls" | grep -E '^  [a-z].*\(|invoke|getstatic.*(ModItems|ModEntities|MobEffects|Effects)|ldc|instanceof' | grep -v 'StringBuilder\|Object."<init>"' | head -40
  done
  echo "== Open Loader files and strings"
  (cd x-open-loader && find . -type f ! -name '*.class' | sort)
  for c in $(cd x-open-loader && find . -name '*.class' | sort); do
    s=$(strings -n 4 "x-open-loader/$c" | grep -i -E 'openloader|data|resources|config|json|pack|enabled|load' | sort -u | head -30 | tr '\n' ' ')
    [ -n "$s" ] && echo "$c: $s"
  done
} > data-out/details.txt 2>&1
head -c 3000 data-out/details.txt
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/adding && cp data-out/details.txt data/adding/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "adding details" && git push -q origin ci/data && echo pushed
