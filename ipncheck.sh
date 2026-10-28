#!/bin/bash
# Throwaway: Inventory Profiles Next's integration hints (format, bundled examples, where user hints go) and the Spell
# Binding Table's screen class in Spell Engine.
set -u
mkdir -p ipn data-out && cd ipn
curl -sSfL -o ipn.jar "https://cdn.modrinth.com/data/O7RBXm3n/versions/CrtAI3P9/InventoryProfilesNext-forge-1.20-1.10.20.jar"
curl -sSfL -o se.jar "https://cdn.modrinth.com/data/XvoWJaA2/versions/jZqwFTXe/spell_engine-forge-1.10.10%2B1.20.1-forge.jar"
unzip -q -o ipn.jar -d ipn; unzip -q -o se.jar -d se
{
  echo "== ipn files with hint"; (cd ipn && find . -iname '*hint*' | sort | head -80)
  for f in $(cd ipn && find . -iname '*hint*' -name '*.json' | sort | head -6); do echo "-- $f"; head -c 1500 "ipn/$f"; echo; done
  echo "== ipn strings about hints"; for c in $(cd ipn && grep -rl --include=*.class -i 'integrationHints\|ModIntegrationHints\|hintsExport' . | head -20); do echo "-- $c"; strings -n 4 "ipn/$c" | grep -iE 'hint|ignore|playerSideOnly|hide|buttonHints|config/|\.json|force' | sort -u | head -40; done
  echo "== spell engine screens"; (cd se && find . -name '*.class' | grep -iE 'screen|binding' | sort)
} > ../data-out/ipn.txt 2>&1
cd ..
echo "::notice title=ipn::$(wc -l < data-out/ipn.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/ipn && cp data-out/ipn.txt data/ipn/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "ipn hints" && git push -q origin ci/data && echo pushed
