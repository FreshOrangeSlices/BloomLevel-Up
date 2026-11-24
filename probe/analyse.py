import os, re, json, zipfile, urllib.request, traceback
def get(url):
    url = url.replace(' ', '%20')
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
targets = json.load(open('probe/gear_targets.json'))
ids = set(targets["weapons"])
for s in targets["sets"]:
    ids.update(s["pieces"])
jars = {}
for f in ['probe/jars.json', 'probe/jars2.json', 'probe/jars3.json']:
    jars.update(json.load(open(f)))
KEYS = ['l_enders-cataclysm', 'legendary-monsters', 'Skarrier Mobs', 'borninchaos', 'monster-expansion', 'myths-and-legends',
        'irons-spells-n-spellbooks', 'aquamirae', 'Bosses Oddities', 'simply-swords', 'mariums-soulslike-weaponry', 'mutant-monsters']
out = []; p = out.append
recipes, loot, lang = [], {}, {}
for key in KEYS:
    try:
        z = zipfile.ZipFile(__import__('io').BytesIO(get(jars[key])))
    except Exception as e:
        p(f"!! {key}: {e}"); continue
    for n in z.namelist():
        try:
            if n.startswith('data/') and n.endswith('.json') and '/recipes/' in n:
                t = json.loads(z.read(n).decode('utf-8', 'replace'))
                res = t.get('result')
                rid = res if isinstance(res, str) else (res or {}).get('item') or (res or {}).get('id')
                if rid:
                    ings = sorted(set(re.findall(r'"(?:item|tag)"\s*:\s*"([^"]+)"', json.dumps({k: v for k, v in t.items() if k != 'result'}))))
                    recipes.append((rid, t.get('type'), ings, n))
            elif n.startswith('data/') and n.endswith('.json') and ('/loot_tables/' in n or '/loot_modifiers/' in n):
                text = z.read(n).decode('utf-8', 'replace')
                for i in re.findall(r'"([a-z0-9_]+:[a-z0-9_/]+)"', text):
                    loot.setdefault(i, set()).add(n.split('/data/')[-1] if '/data/' in n else n[5:])
            elif re.match(r'assets/[^/]+/lang/en_us\.json$', n):
                lang.update(json.loads(z.read(n).decode('utf-8', 'replace')))
        except Exception:
            pass
def recipe_lines(i):
    return [f"recipe {typ}: {', '.join(ings)} [{path.split('/recipes/')[-1]}]" for rid, typ, ings, path in recipes if rid == i]
def lang_lines(i):
    ns, path = i.split(':')
    return [f"lang {k} = {v}" for k, v in lang.items() if (path in k and ns in k) and ('desc' in k or 'tooltip' in k or 'lore' in k or 'ability' in k or 'bonus' in k or k.endswith(path))][:8]
def ingredients_report(i, seen):
    lines = []
    for rid, typ, ings, path in recipes:
        if rid != i:
            continue
        for g in ings:
            if g.startswith('minecraft:') or g.startswith('forge:') or g in seen:
                continue
            seen.add(g)
            src = recipe_lines(g)[:2] + [f"loot {x}" for x in sorted(loot.get(g, []))[:6]]
            lines.append(f"   <- {g} ({lang.get('item.' + g.replace(':', '.'), '')}): " + (" | ".join(src) or "no recipe or loot found"))
    return lines
p("==== WEAPONS")
for i, w in sorted(targets["weapons"].items(), key=lambda kv: -kv[1]["dmg"] * kv[1]["spd"]):
    p(f"## {i} {w['name']} {w['dmg']}x{w['spd']}={w['dmg']*w['spd']:.1f}")
    for l in recipe_lines(i) + [f"loot {x}" for x in sorted(loot.get(i, []))[:10]] + lang_lines(i) + ingredients_report(i, set()):
        p("  " + l[:400])
p("==== ARMOR")
for s in targets["sets"]:
    p(f"## {s['mod']} {s['material']} {s['def']} def {s['tough']} tough")
    seen = set()
    for i in s["pieces"]:
        for l in recipe_lines(i) + [f"loot {x}" for x in sorted(loot.get(i, []))[:6]] + lang_lines(i) + ingredients_report(i, seen):
            p(f"  {i.split(':')[1]}: " + l[:400])
    mat = s['material'].split(':')[-1].replace('_armor', '')
    for k, v in lang.items():
        if mat in k and ('set' in k or 'bonus' in k or 'full' in k or 'desc' in k):
            p(f"  setlang {k} = {v[:300]}")
os.makedirs('probe-out', exist_ok=True)
open('probe-out/gear.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
