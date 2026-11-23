import os, re, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(cls, jar='j.jar'):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ')]
out = []; p = out.append
jars = json.load(open('probe/jars.json'))
for key in ['the-graveyard-forge', 'undead-unleashed', 'borninchaos', 'monster-expansion', 'mutant-monsters', 'myths-and-legends',
            'mariums-soulslike-weaponry', 'legendary-monsters', 'l_enders-cataclysm', 'Skarrier Mobs', 'Dungeon Denizens']:
    try:
        open('j.jar', 'wb').write(get(jars[key])); z = zipfile.ZipFile('j.jar'); names = z.namelist()
        ids = set()
        for n in names:
            m = re.match(r'assets/([^/]+)/lang/en_us\.json$', n)
            if m:
                try:
                    lang = json.loads(z.read(n).decode('utf-8', 'replace'))
                except Exception:
                    continue
                for k, v in lang.items():
                    mm = re.match(r'entity\.([a-z0-9_]+)\.([a-z0-9_]+)$', k)
                    if mm:
                        ids.add(f"{mm.group(1)}:{mm.group(2)}={v}")
        p(f"==== {key}: {len(ids)}\n" + " | ".join(sorted(ids)))
        if key == 'Skarrier Mobs':
            for n in names:
                if n.endswith('.class') and re.search(r'(?i)spawn|wither|condition', n) and '$' not in n:
                    src = dec(n[:-6].replace('/', '.'))
                    hits = [l for l in src if re.search(r'(?i)wither|advancement|killed|defeat|beaten|savedData|SavedData', l)]
                    if hits:
                        p(f"---- {n}\n" + "\n".join(hits[:25])[:2500])
    except Exception:
        p(f"==== {key} failed\n" + traceback.format_exc()[-800:])
os.makedirs('probe-out', exist_ok=True)
open('probe-out/entities.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
