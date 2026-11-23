import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    url = json.load(open('probe/jars2.json'))['spell-engine']
    p("spell engine: " + url.split('/')[-1])
    open('se.jar', 'wb').write(get(url)); z = zipfile.ZipFile('se.jar'); names = z.namelist()
    data = [n for n in names if n.startswith('data/') and not n.endswith('/')]
    p("data files (" + str(len(data)) + "): " + ", ".join(n for n in data if 'player_animations' not in n)[:4000])
    for n in data:
        if re.search(r'rpg_series/spell/(cleave|flurry|swift_strikes)\.json$', n):
            p(f"==== {n}\n{z.read(n).decode()[:1500]}")
    for n in data:
        if 'tags' in n and ('weapon' in n or 'skill' in n or 'rpg_series' in n):
            p(f"==== {n}\n{z.read(n).decode()[:800]}")
    for n in names:
        if n.endswith('.class') and re.search(r'(?i)fallback|WeaponSkill|SpellAssignment|ContainerSource|SpellContainerSource', n.split('/')[-1]):
            p(f"class {n}")
    for n in names:
        if n.endswith('.class') and re.search(r'(?i)FallbackCompatibility\.class$|/FallbackConfig\.class$', n):
            p(f"==== {n}\n{dec('se.jar', n[:-6].replace('/', '.'), 5000)}")
    for n in names:
        if n.endswith('.class'):
            b = z.read(n)
            if re.search(rb'swift_strikes|rpg_series:flurry|rpg_series:cleave|"flurry"|weapon_skills', b):
                p(f"refs {n}: " + str(sorted(set(m.group(0).decode() for m in re.finditer(rb'[a-z_:/]*(swift_strikes|flurry|cleave|weapon_skills)[a-z_:/]*', b)))))
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
