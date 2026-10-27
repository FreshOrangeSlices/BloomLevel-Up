#!/bin/bash
# Throwaway: LegionWareX's whole registry from a real server with Bloom's -Dbloomlevelup.dump, pushed to ci/data.
set -eu
run=$(cat check/run.txt)
gh run download "$run" -R "$GITHUB_REPOSITORY" -n bloomlevelup-jar -D jar
jar=$(ls jar/*.jar | grep -v -- '-slim' | head -1)
echo "bloom jar: $jar"
python3 bloom/pack/build_pack.py "$jar" dump out > build.txt 2>&1 || { tail -40 build.txt; exit 1; }
python3 bloom/pack/boot_server.py "$(ls out/*server.zip | head -1)" server > boot.txt 2>&1 || { grep -o 'title=[a-z-]*::.\{0,600\}' boot.txt; exit 1; }
cd server
echo "-Dbloomlevelup.dump=dump.jsonl" >> user_jvm_args.txt
python3 - <<'PY'
import os, subprocess, threading, time
p = subprocess.Popen(["sh", "start.sh"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
lines = []
threading.Thread(target=lambda: [lines.append(l.rstrip()) for l in p.stdout], daemon=True).start()
end = time.time() + 600
while time.time() < end and p.poll() is None and not any("Bloom registry dump" in l for l in lines):
    time.sleep(2)
print("\n".join(l for l in lines if "registry dump" in l))
time.sleep(3)
try:
    p.stdin.write("stop\n"); p.stdin.flush()
except Exception:
    pass
try:
    p.wait(timeout=120)
except subprocess.TimeoutExpired:
    os.killpg(p.pid, 9)
PY
test -s dump.jsonl
wc -l dump.jsonl
cd ..
mkdir data && cp server/dump.jsonl data/legionwarex-dump.jsonl && cp bloom/pack/pack.lock.json data/
cd data && git init -q && git checkout -q -b ci/data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "LegionWareX registry dump (from run $run)"
git push -q -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci/data
echo pushed
