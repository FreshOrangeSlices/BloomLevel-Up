import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(j, cls):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip()]
def method(lines, names, limit=60):
    out = []
    for i, l in enumerate(lines):
        if any(re.search(r'\b' + n + r'\(', l) for n in names) and ('public' in l or 'protected' in l or 'private' in l):
            depth = 0; started = False
            for k in range(i, min(len(lines), i + 400)):
                out.append(lines[k]); depth += lines[k].count('{') - lines[k].count('}')
                if '{' in lines[k]: started = True
                if started and depth <= 0: break
                if len(out) > limit: break
    return '\n'.join(out)
jars = json.load(open('probe/jars3.json'))
os.makedirs('probe-out', exist_ok=True)
out = []; p = out.append
def jar(name):
    open('j.jar', 'wb').write(get(jars[name])); return zipfile.ZipFile('j.jar')
try:
    z = jar('monster-expansion')
    for cls in ['net.saksolm.monsterexpansion.entity.custom.KobekoEntity', 'net.saksolm.monsterexpansion.entity.custom.ArborixEntity']:
        p(f"==== {cls} mobInteract\n" + method(dec('j.jar', cls), ['m_6071_', 'mobInteract'], 90))
except Exception: p(traceback.format_exc())
try:
    z = jar('Skarrier Mobs'); names = z.namelist()
    for n in names:
        if re.search(r'recipes?/.*(blank_totem|ancient_remains|heart_of_tranquility|resisteel_block|deep_alloy|resisteel_ingot)', n) and n.endswith('.json'):
            p(f"recipe {n.split('/')[-1]}: {re.sub(chr(10)+'| +', ' ', z.read(n).decode())[:500]}")
    for n in names:
        if n.endswith('.json') and n.startswith('data/') and re.search(rb'ancient_remains|heart_of_tranquility|resisteel_block|blank_totem|wrong_deepslate_ore_invulnerite|deep_alloy_ingot', z.read(n)) and 'recipe' not in n and 'advancement' not in n:
            p(f"names: {n}: {re.sub(chr(10)+'| +', ' ', z.read(n).decode())[:300]}")
except Exception: p(traceback.format_exc())
try:
    z = jar('borninchaos'); names = z.namelist()
    p("dark metal deposit loot: " + re.sub(r'\s+', ' ', z.read('data/born_in_chaos_v1/loot_tables/blocks/dark_metal_deposit.json').decode())[:500])
    for n in names:
        if n.endswith('.json') and n.startswith('data/') and re.search(rb'pieceofdarkmetal|dark_metal_deposit|monster_skin|nightmare_stalker_skull|thornshell', z.read(n)) and '/recipes/' not in n and 'advancement' not in n:
            p(f"names: {n}: {re.sub(chr(10)+'| +', ' ', z.read(n).decode())[:260]}")
except Exception: p(traceback.format_exc())
try:
    z = jar('myths-and-legends'); names = z.namelist()
    p("==== BloodMoonSavedData\n" + '\n'.join(l for l in dec('j.jar', 'com.ovinter.mythsandlegends.api.data.BloodMoonSavedData') if re.search(r'INTERVAL|VARIANCE|nextInt|random|nextBloodMoon\s*=', l)))
    res = [n for n in names if re.search(r'data/mythsandlegends/(research|researches)', n)]
    p(f"research files ({len(res)}): " + ", ".join(res[:60]))
    for n in res[:8]: p(f"{n}: {re.sub(chr(10)+'| +', ' ', z.read(n).decode())[:500]}")
    lang = json.loads(z.read('assets/mythsandlegends/lang/en_us.json'))
    p("lang research/chronicler: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if re.search(r'(?i)research|chronicler|notes|sanctum', k + str(v)))[:4000])
    for n in names:
        if '/loot_tables/entities/' in n: p(f"drops {n.split('/')[-1]}: {', '.join(sorted(set(re.findall(r'mythsandlegends:[a-z_]+', z.read(n).decode()))))}")
    for n in names:
        if re.search(r'recipes?/.*(ethereal_flame|refined_tenebrite|tainted)', n): p(f"recipe {n}: {re.sub(chr(10)+'| +', ' ', z.read(n).decode())[:400]}")
except Exception: p(traceback.format_exc())
try:
    z = jar('mutant-monsters'); names = z.namelist()
    for n in names:
        if n.endswith('.class') and re.search(rb'chemical_x|CHEMICAL_X', z.read(n)) and re.search(rb'(?i)brew|potionbrewing|Mix', z.read(n)):
            p(f"brewing class {n}")
            p('\n'.join(l for l in dec('j.jar', n[:-6].replace('/', '.')) if re.search(r'(?i)brew|addMix|CHEMICAL|Items\.', l))[:1500])
except Exception: p(traceback.format_exc())
try:
    z = jar('illager-invasion'); names = z.namelist()
    for n in names:
        if n.endswith('.class') and 'Imbuing' in n and '$' not in n and 'Menu' in n:
            p(f"==== {n}\n" + '\n'.join(l for l in dec('j.jar', n[:-6].replace('/', '.')) if re.search(r'(?i)level|max|gem|HALLOWED|cost|book|ENCHANTED|getMaxLevel|\+ 1', l))[:3000])
    lang = json.loads(z.read([n for n in names if n.endswith('en_us.json')][0]))
    p("imbue lang: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if re.search(r'(?i)imbu|candle|horn|gem|platinum', k)))
except Exception: p(traceback.format_exc())
open('probe-out/facts2.txt', 'w').write('\n'.join(out))
open('report.txt', 'w').write('done')
