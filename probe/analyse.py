import os, re, sys, glob, zipfile, subprocess
root = sys.argv[1]
out = []; p = out.append
zips = glob.glob(os.path.join(root, '**', '*server*.zip'), recursive=True)
with zipfile.ZipFile(zips[0]) as z:
    jar = [n for n in z.namelist() if 'block_factorys_bosses' in n and n.endswith('.jar')][0]
    z.extract(jar, 'mods')
j = os.path.join('mods', jar)
zf = zipfile.ZipFile(j)
names = zf.namelist()
p("yeti/ice/wall/frost/snow classes and files:")
for n in names:
    if re.search(r'yeti|ice_?wall|icewall|wall|frost|glacier|skor', n, re.I):
        p("  " + n)
def dec(n, limit=20000):
    cls = n[:-6].replace('/', '.')
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    src = '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())
    return src[:limit]
for n in names:
    if n.endswith('.class') and '/item/' in n and re.search(r'yeti|ice|frost|skor', n, re.I):
        p(f"==== {n}\n{dec(n)}")
# Entities or blocks the items name.
for n in names:
    if n.endswith('.class') and re.search(r'(ice|frost)(_)?(wall|spike|pillar|block)|wall', n, re.I) and '/item/' not in n:
        p(f"==== {n}\n{dec(n, 12000)}")
for n in names:
    if 'lang/en_us.json' in n:
        txt = zf.read(n).decode('utf8', 'replace')
        p("lang lines: " + '\n'.join(l for l in txt.splitlines() if re.search(r'yeti|ice|wall|frost|skor', l, re.I))[:6000])
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
