#!/bin/bash
# Throwaway: does BOP obey the pack's Nether settings? Boot the pack's server and /locate each BOP Nether biome.
set -u
python3 -c "import zipfile; zipfile.ZipFile('dummy.jar','w').writestr('x.txt','x')"
python3 bloom/pack/build_pack.py dummy.jar bop out > build.txt 2>&1 || { tail -20 build.txt; exit 1; }
python3 bloom/pack/boot_server.py "$(ls out/*server.zip | head -1)" server > boot.txt 2>&1
cd server
python3 - <<'PY'
import re, subprocess, threading, time
p = subprocess.Popen(["sh", "start.sh"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
lines = []
threading.Thread(target=lambda: [lines.append(l.rstrip()) for l in p.stdout], daemon=True).start()
end = time.time() + 400
while time.time() < end and not any("Done (" in l for l in lines):
    time.sleep(2)
out = []
for biome in ["crystalline_chasm", "undergrowth", "erupting_inferno", "visceral_heap", "withered_abyss"]:
    start = len(lines)
    p.stdin.write(f"execute in minecraft:the_nether run locate biome biomesoplenty:{biome}\n"); p.stdin.flush()
    time.sleep(25)
    said = [l for l in lines[start:] if "biomesoplenty" in l or "Could not find" in l or "nearest" in l.lower()]
    out.append(f"{biome}: " + (" / ".join(said)[-300:] if said else "no answer"))
p.stdin.write("stop\n"); p.stdin.flush(); time.sleep(15)
text = "\n".join(out)
print(text)
print("::notice title=bop-nether::" + text.replace("%", "%25").replace("\n", "%0A"))
toggles = open("config/biomesoplenty/biome_toggles.json").read()
print("::notice title=bop-files::" + ("visceral off" if '"visceral_heap_enabled": false' in toggles else "visceral ON") + ", " +
      ("weight 5" if "bop_nether_region_weight = 5" in open("config/biomesoplenty/generation.toml").read() else "weight not 5"))
PY
