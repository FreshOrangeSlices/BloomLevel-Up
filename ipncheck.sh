#!/bin/bash
# Throwaway: Better Combat 1.9.0 (Forge 1.20.1) API for Batter Up!'s swings: javap of the classes Bloom would touch.
set -u
mkdir -p bc data-out && cd bc
curl -sSfL -o bc.jar "https://cdn.modrinth.com/data/5sy6g3kz/versions/rnhiaw3t/bettercombat-forge-1.9.0%2B1.20.1.jar" || curl -sSfL -o bc.jar "https://cdn.modrinth.com/data/5sy6g3kz/versions/rnhiaw3t/bettercombat-forge-1.9.0+1.20.1.jar"
{
  for c in net.bettercombat.api.WeaponAttributes 'net.bettercombat.api.WeaponAttributes$Attack' net.bettercombat.logic.WeaponRegistry net.bettercombat.logic.PlayerAttackHelper net.bettercombat.api.AttackHand 'net.bettercombat.logic.PlayerAttackHelper$AttackSelection'; do
    echo "== $c"; javap -p -cp bc.jar "$c"
  done
  echo "== getCurrentAttack bytecode"; javap -c -p -cp bc.jar net.bettercombat.logic.PlayerAttackHelper | sed -n '/getCurrentAttack/,/^$/p' | head -120
  echo "== getAttributes(ItemStack) bytecode"; javap -c -p -cp bc.jar net.bettercombat.logic.WeaponRegistry | sed -n '/getAttributes(net.minecraft.world.item.ItemStack)/,/^$/p' | head -60
  echo "== preset files"; unzip -l bc.jar | grep weapon_attributes | head -60
  echo "== sword.json"; unzip -p bc.jar data/bettercombat/weapon_attributes/sword.json | head -80
  echo "== who calls getCurrentAttack"; for c in $(unzip -l bc.jar | awk '/\.class$/{print $4}'); do n=${c%.class}; n=${n//\//.}; javap -c -p -cp bc.jar "$n" 2>/dev/null | grep -q "getCurrentAttack" && echo "$n"; done
} > ../data-out/bc.txt 2>&1
cd ..
echo "::notice title=bc::$(wc -l < data-out/bc.txt) lines"
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/bc && cp data-out/bc.txt data/bc/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "bc api" && git push -q origin ci/data && echo pushed
