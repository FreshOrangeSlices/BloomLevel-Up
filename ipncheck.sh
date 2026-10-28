#!/bin/bash
# Throwaway: the Spell Binding Table's screen class in Spell Engine (for an IPN hint).
set -u
mkdir -p se data-out && cd se
for u in "https://cdn.modrinth.com/data/XvoWJaA2/versions/jZqwFTXe/spell_engine-forge-1.10.10+1.20.1-forge.jar" "https://cdn.modrinth.com/data/XvoWJaA2/versions/jZqwFTXe/spell_engine-forge-1.10.10%2B1.20.1-forge.jar"; do
  curl -sSfL -o se.jar "$u" && break
done
unzip -q -o se.jar -d x
{ echo "== screens"; (cd x && find . -name '*.class' | grep -iE 'screen|binding' | sort); } > ../data-out/se.txt 2>&1
cd ..
echo "::notice title=se::$(head -c 3000 data-out/se.txt | tr '\n' ' ')"
