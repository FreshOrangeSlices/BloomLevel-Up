#!/bin/bash
# Throwaway: how taming works in the pack's Book of Dragons (1.31): its lang file, tags, data and taming-related class strings.
set -u
mkdir -p bod data-out && cd bod
curl -sSfL -o bod.jar "https://cdn.modrinth.com/data/fJee0CPJ/versions/FMHtYIBA/bookofdragons-1.31-1.20.1.jar"
unzip -q -o bod.jar -d x
{
  echo "== data files"; (cd x && find data -type f | sort)
  echo "== lang"; cat x/assets/bookofdragons/lang/en_us.json
  echo "== tags"; for f in $(find x/data -path '*tags*' -name '*.json'); do echo "-- $f"; cat "$f"; echo; done
  echo "== class names"; (cd x && find . -name '*.class' | sed 's#^\./##' | sort)
  echo "== strings"; for c in $(find x -name '*.class' | grep -iE 'tam|dragon|affect|trust|feed|ride|saddle|goal'); do s=$(strings -n 4 "$c" | grep -iE 'tam|affection|trust|feed|food|fish|salmon|cod|ritual|accept|familiar|wary|saddle|sneak|crouch|chicken|meat|eel' | sort -u | head -40); [ -n "$s" ] && { echo "-- $c"; echo "$s"; }; done
} > ../data-out/bod.txt 2>&1
cd ..
echo "::notice title=bod::$(wc -l < data-out/bod.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/bod && cp data-out/bod.txt data/bod/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "book of dragons" && git push -q origin ci/data && echo pushed
