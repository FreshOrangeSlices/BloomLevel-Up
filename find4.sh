#!/bin/bash
# Throwaway: Ice and Fire's ClientConfig and how its config is registered (file name, sections), via javap.
set -e
url=$(python3 -c "
import json, urllib.request, urllib.parse
UA={'User-Agent':'FreshOrangeSlices/BloomLevel-Up modpack check (github actions)'}
q=urllib.parse.urlencode({'loaders':json.dumps(['forge']),'game_versions':json.dumps(['1.20.1'])})
v=json.load(urllib.request.urlopen(urllib.request.Request(f'https://api.modrinth.com/v2/project/ice-and-fire-dragons/version?{q}',headers=UA)))[0]
print(next(f for f in v['files'] if f.get('primary'))['url'])")
curl -sSL -o iaf.jar "$url"
mkdir -p x && cd x && unzip -q -o ../iaf.jar 'com/github/alexthe666/iceandfire/config/*' 'com/github/alexthe666/iceandfire/IceAndFire.class' || true
out=$( (javap -c -p com/github/alexthe666/iceandfire/config/ClientConfig.class | grep -E 'ldc|invoke' | grep -E 'String|push|pop|define|comment|translation' ; echo '---- registration'; javap -c -p com/github/alexthe666/iceandfire/IceAndFire.class | grep -E 'registerConfig|ModConfig|ldc' | head -40; echo '---- ConfigHolder'; javap -c -p com/github/alexthe666/iceandfire/config/ConfigHolder.class | grep -E 'ldc|invoke' | head -30) 2>&1 | sed 's/^ *//' )
echo "$out"
python3 - "$out" <<'PY'
import sys
t=sys.argv[1].replace('%','%25').replace('\n','%0A')
for n,i in enumerate(range(0,len(t),3800)): print(f"::notice title=find4-{n:02d}::{t[i:i+3800]}")
PY
