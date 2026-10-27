#!/bin/bash
# Throwaway: Aquamirae's (newest 1.20.1 Forge) title-screen settings: strings and config classes in its jar.
set -e
url=$(python3 -c "
import json, urllib.request, urllib.parse
UA={'User-Agent':'FreshOrangeSlices/BloomLevel-Up modpack check (github actions)'}
q=urllib.parse.urlencode({'loaders':json.dumps(['forge']),'game_versions':json.dumps(['1.20.1'])})
vs=json.load(urllib.request.urlopen(urllib.request.Request(f'https://api.modrinth.com/v2/project/aquamirae/version?{q}',headers=UA)))
v=next(v for v in vs if v['version_type'] in ('release','beta'))
print(next(f for f in v['files'] if f.get('primary'))['url'])")
echo "$url"
curl -sSL -o aq.jar "$url"
mkdir -p aq && cd aq && unzip -q -o ../aq.jar
out=$( (echo "jar: $url"; grep -rlaiE 'panorama|mainmenu|main_menu|titlescreen|TitleScreen' --include='*.class' . | head -20; echo '---- strings'; for c in $(grep -rlaiE 'panorama|main.?menu|TitleScreen' --include='*.class' . | head -12); do echo "## $c"; javap -c -p "$c" 2>/dev/null | grep -E 'ldc' | grep -iE 'String' | sed 's/.*String //' | head -40; done; echo '---- config files'; find . -iname '*config*' | head -20; for c in $(find . -iname '*config*.class' | head -6); do echo "## $c"; javap -c -p "$c" 2>/dev/null | grep -E 'ldc.*String' | sed 's/.*String //' | head -60; done; echo '---- assets'; find . -ipath '*panorama*' | head; ls assets/aquamirae/textures/gui 2>/dev/null | head) 2>&1 )
echo "$out"
python3 - "$out" <<'PY'
import sys
t=sys.argv[1].replace('%','%25').replace('\n','%0A')
for n,i in enumerate(range(0,len(t),3800)): print(f"::notice title=find5-{n:02d}::{t[i:i+3800]}")
PY
