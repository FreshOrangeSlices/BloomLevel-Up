#!/bin/bash
# Throwaway: where Iron's Autoloader Crossbow comes from (recipes, loot, trades), and Open Loader's 1.20.1 Forge
# build (its folders), to take it out of LegionWareX with a pack datapack.
set -u
mkdir -p w data-out && cd w
curl -sSfL -o irons.jar "https://cdn.modrinth.com/data/s4OWxYQQ/versions/hZaEegS9/irons_spellbooks-1.20.1-3.16.3.jar"
unzip -q -o irons.jar -d irons
curl -sSf "https://api.modrinth.com/v2/project/open-loader/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D" > ol.json
python3 - <<'PY' > ol.txt
import json
v = json.load(open('ol.json'))
for x in v[:5]:
    print(x['version_number'], x['version_type'], x['id'], x['files'][0]['filename'], x['files'][0]['url'], [d.get('project_id') for d in x['dependencies'] if d['dependency_type']=='required'])
PY
url=$(sed -n 1p ol.txt | awk '{print $5}')
curl -sSfL -o ol.jar "$url" && unzip -q -o ol.jar -d ol
{
  echo "== iron's files mentioning autoloader"; (cd irons && grep -rl "autoloader" data assets 2>/dev/null | sort)
  for f in $(cd irons && grep -rl "autoloader" data 2>/dev/null | sort); do echo "-- $f"; head -c 1200 "irons/$f"; echo; done
  echo "== iron's classes mentioning autoloader"; (cd irons && grep -rl --include=*.class "autoloader" . | sort | head -20)
  echo "== open loader versions"; cat ol.txt
  echo "== open loader strings"; for c in $(cd ol && find . -name '*.class'); do strings -n 4 "ol/$c" | grep -iE 'openloader|config|data|resource|packs|folder|dir' ; done | sort -u | head -60
  echo "== open loader files"; (cd ol && find . -type f | sort | head -60)
} > ../data-out/w.txt 2>&1
cd ..
echo "::notice title=w::$(wc -l < data-out/w.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/w && cp data-out/w.txt data/w/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "autoloader and open loader" && git push -q origin ci/data && echo pushed
