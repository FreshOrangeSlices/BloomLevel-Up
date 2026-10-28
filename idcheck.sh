#!/bin/bash
# Throwaway: the ids a playtest needs (structures, biomes, spells, entity names) and the server's /help, pushed to ci/data.
set -u
python3 -c "import zipfile; zipfile.ZipFile('dummy.jar','w').writestr('x.txt','x')"
python3 bloom/pack/build_pack.py dummy.jar ids out > build.txt 2>&1 || { tail -20 build.txt; exit 1; }
python3 bloom/pack/boot_server.py "$(ls out/*server.zip | head -1)" server > boot.txt 2>&1
mkdir -p data-out
python3 check/ids.py server/mods > data-out/ids.json
cd server
python3 - <<'PY'
import subprocess, threading, time
p = subprocess.Popen(["sh", "start.sh"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
lines = []
threading.Thread(target=lambda: [lines.append(l.rstrip()) for l in p.stdout], daemon=True).start()
end = time.time() + 400
while time.time() < end and not any("Done (" in l for l in lines):
    time.sleep(2)
start = len(lines)
for cmd in ["help", "help spark", "help easy_mob_spawn_control", "help chunky", "help waystones", "help structurify"]:
    p.stdin.write(cmd + "\n"); p.stdin.flush(); time.sleep(6)
open("../data-out/help.txt", "w").write("\n".join(lines[start:]))
p.stdin.write("stop\n"); p.stdin.flush(); time.sleep(20)
PY
cd ..
echo "::notice title=ids::$(python3 -c "import json;d=json.load(open('data-out/ids.json'));print(len(d['structures']), 'structure mods,', len(d['biomes']), 'biome mods,', len(d['spells']), 'spells')") help $(wc -l < data-out/help.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
rm -rf data/ids && cp -r data-out data/ids && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "playtest ids" && git push -q origin ci/data && echo pushed
