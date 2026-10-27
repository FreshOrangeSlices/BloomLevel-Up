#!/bin/bash
# Throwaway: Aquamirae's early config (file name, format, the themed_main_menu entry).
set -e
curl -sSL -o aq.jar https://cdn.modrinth.com/data/k23mNPhZ/versions/rRfT3ynS/aquamirae-forge-1.20.1-7.1.14.jar
mkdir -p aq && cd aq && unzip -q -o ../aq.jar 'dev/obscuria/aquamirae/config/early/*' 'dev/obscuria/aquamirae/mixin/client/MixinTitleScreen.class' 'dev/obscuria/aquamirae/mixin/client/MixinLogoRenderer.class'
out=$( (for c in dev/obscuria/aquamirae/config/early/*.class dev/obscuria/aquamirae/mixin/client/MixinTitleScreen.class dev/obscuria/aquamirae/mixin/client/MixinLogoRenderer.class; do echo "## $c"; javap -c -p "$c" 2>/dev/null | grep -E 'ldc|invoke(static|virtual|special|interface)|getstatic|putstatic|Method |^  [a-z].*\(' | grep -vE 'StringBuilder|Objects.requireNonNull' | sed 's/^ *//' | head -70; done) 2>&1 )
echo "$out"
python3 - "$out" <<'PY'
import sys
t=sys.argv[1].replace('%','%25').replace('\n','%0A')
for n,i in enumerate(range(0,len(t),3800)): print(f"::notice title=find6-{n:02d}::{t[i:i+3800]}")
PY
