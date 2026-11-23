import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    jars = json.load(open('probe/jars.json')); jars2 = json.load(open('probe/jars2.json'))
    open('gy.jar', 'wb').write(get(jars['the-graveyard-forge'])); z = zipfile.ZipFile('gy.jar')
    p("==== graveyard recipe\n" + z.read('data/graveyard/recipes/bone_dagger.json').decode())
    for n in z.namelist():
        if n.startswith('data/') and n.endswith('.json') and b'bone_dagger' in z.read(n):
            p(f"names bone_dagger: {n}")
    p("==== AcolyteEntity (equipment)\n" + "\n".join(l for l in dec('gy.jar', 'com.lion.graveyard.entities.AcolyteEntity', 60000).splitlines() if re.search(r'BONE_DAGGER|DropChance|setItemSlot|m_8061_|m_21409_|populateDefault|m_213945_', l))[:2500])
    open('ek.jar', 'wb').write(get(jars['epic-knights-shields-armor-and-weapons'])); z = zipfile.ZipFile('ek.jar')
    for n in z.namelist():
        if n.endswith('.class'):
            b = z.read(n)
            if re.search(rb'(?i)rusted', b) and re.search(rb'(?i)spawn|equip|FinalizeSpawn|EntityJoin|setItemSlot|m_8061_', b):
                p(f"ek class {n}")
    for n in z.namelist():
        if n.startswith('data/') and ('loot_modifiers' in n or 'global_loot' in n or ('loot_tables/entities' in n)):
            p(f"ek data {n}: {z.read(n).decode('utf8','replace')[:300].replace(chr(10),' ')}")
    lang = json.loads(z.read('assets/magistuarmory/lang/en_us.json'))
    p("ek config lang: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if re.search(r'(?i)mob|spawn|equip|zombie|skeleton|chance', k + v) and 'autoconfig' in k)[:3000])
    open('se.jar', 'wb').write(get(jars2['spell-engine'])); z = zipfile.ZipFile('se.jar')
    p("==== FallbackConfig\n" + dec('se.jar', 'net.spell_engine.config.FallbackConfig', 5000))
    p("==== FallbackConfig$CompatGroup\n" + dec('se.jar', 'net.spell_engine.config.FallbackConfig$CompatGroup', 3000))
    for t in ['sword', 'claymore', 'double_axe', 'dagger']:
        n = f'data/rpg_series/tags/items/weapon_type/{t}.json'
        if n in z.namelist(): p(f"tag {t}: {z.read(n).decode()[:500]}")
    p("==== WeaponSkills\n" + dec('se.jar', 'net.spell_engine.rpg_series.datagen.WeaponSkills', 6000))
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
