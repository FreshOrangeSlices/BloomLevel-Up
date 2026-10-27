#!/bin/bash
# Throwaway: does Better Mob Combat break on its own with Migamigos and Cataclysm mobs, and does Better Mob Combat Fix help?
set -u
variant=$1   # plain | fix
mkdir -p t-$variant && cd t-$variant
cp ../bloom/pack/build_pack.py ../bloom/pack/boot_server.py .
python3 - "$variant" <<'PY'
import json, sys
mods = ["better-combat", "playeranimator", "cloth-config", "better-mob-combat", "migamigos", "l_enders-cataclysm"]
if sys.argv[1] == "fix":
    mods.append("better-mob-combat-fix")
real = json.load(open("../bloom/pack/pack.json"))
framework = next(e for layer in real["layers"] for e in layer.get("external", []) if e["name"] == "Framework")
json.dump({"name": "BMCTest", "summary": "x", "minecraft": "1.20.1", "forge": "47.4.28",
           "layers": [{"name": "only", "modrinth": mods, "external": [framework]}],
           "env_overrides": {"playeranimator": {"server": "required"}, "cloth-config": {"server": "required"}},
           "client_presets": {}}, open("pack.json", "w"), indent=1)
PY
python3 -c "import zipfile; zipfile.ZipFile('dummy.jar','w').writestr('x.txt','x')"
python3 build_pack.py dummy.jar t out > build.txt 2>&1; grep -o "title=pack-[a-z]*::.\{0,300\}" build.txt | head -5
python3 boot_server.py "$(ls out/*server.zip | head -1)" server > boot.txt 2>&1; grep -o "title=server-[a-z]*::.\{0,400\}" boot.txt | head -3
cd server
rm -rf world
sed -i 's/^level-type=.*//; s/^generate-structures=.*//' server.properties
printf 'level-type=minecraft\\:flat\ngenerate-structures=false\nonline-mode=false\n' >> server.properties
python3 - <<'PY'
import re, subprocess, threading, time
p = subprocess.Popen(["sh", "start.sh"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
lines = []
threading.Thread(target=lambda: [lines.append(l.rstrip()) for l in p.stdout], daemon=True).start()
end = time.time() + 300
while time.time() < end and not any("Done (" in l for l in lines) and p.poll() is None:
    time.sleep(1)
print("up:", any("Done (" in l for l in lines))
for cmd in ["summon minecraft:zombie 0 -60 0", "summon minecraft:pillager 2 -60 0", "summon migamigos:cocogoat 4 -60 0",
            "summon cataclysm:amethyst_crab 8 -60 0", "summon cataclysm:koboleton 12 -60 0"]:
    if p.poll() is not None:
        break
    start = len(lines)
    p.stdin.write(cmd + "\n"); p.stdin.flush(); time.sleep(6)
    new = lines[start:]
    bad = [l for l in new if re.search(r"AbstractMethodError|Exception|crash", l)]
    print(f"{cmd}: {'server died' if p.poll() is not None else 'ok'}; " + (" / ".join(bad[:3])[:500] if bad else "no errors") + " | " + " / ".join(l for l in new if "Summoned" in l)[:120])
if p.poll() is None:
    p.stdin.write("stop\n"); p.stdin.flush(); time.sleep(10)
mix = [l for l in lines if re.search(r"mobplayeranimator|bettermobcombat|bmcfix|better_mob_combat_fix", l, re.I) and re.search(r"ERROR|WARN|apply|Mixin", l)]
print("mixin notes:", " / ".join(mix[:8])[:1500])
PY
