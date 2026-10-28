#!/bin/bash
# Throwaway: boot LegionWareX plus Biomes O' Plenty, Decorative Blocks and Handcrafted once, and bring back BOP's and TerraBlender's config files.
set -u
python3 - <<'PY'
import json
p = "bloom/pack/pack.json"
spec = json.load(open(p))
spec["layers"].append({"name": "world-and-decor", "modrinth": ["biomes-o-plenty", "decorative-blocks", "handcrafted"]})
json.dump(spec, open(p, "w"), indent=1)
PY
python3 -c "import zipfile; zipfile.ZipFile('dummy.jar','w').writestr('x.txt','x')"
python3 bloom/pack/build_pack.py dummy.jar bop out > build.txt 2>&1; grep -o "title=pack-[a-z-]*::.\{0,600\}" build.txt | head -6
python3 bloom/pack/boot_server.py "$(ls out/*server.zip | head -1)" server > boot.txt 2>&1; grep -o "title=server-[a-z-]*::.\{0,500\}" boot.txt | head -4
mkdir -p data-out/config
cp build.txt boot.txt data-out/ 2>/dev/null
(cd server/config && find . -iname '*biomesoplenty*' -o -iname '*terrablender*' -o -iname '*handcrafted*' -o -iname '*decorative*' | while read f; do mkdir -p "../../data-out/config/$(dirname "$f")"; cp -r "$f" "../../data-out/config/$f"; done)
ls -R data-out/config | head -40
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
rm -rf data/bop && cp -r data-out data/bop && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "BOP configs" && git push -q origin ci/data && echo pushed
