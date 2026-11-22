import os, re, sys, glob, zipfile, subprocess
root = sys.argv[1]
out = []; p = out.append
zips = glob.glob(os.path.join(root, '**', '*server*.zip'), recursive=True)
with zipfile.ZipFile(zips[0]) as z:
    jar = [n for n in z.namelist() if 'block_factorys_bosses' in n and n.endswith('.jar')][0]
    z.extract(jar, 'mods')
j = os.path.join('mods', jar)
names = zipfile.ZipFile(j).namelist()
want = ['OwnableByAllEntity', 'PirateRookEntity', 'CrossbowPirateEntity']
for w in want:
    for n in names:
        if n.endswith('/' + w + '.class'):
            cls = n[:-6].replace('/', '.')
            r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
            src = '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())
            p(f"==== {n} ({len(src)} chars)\n{src[:30000]}")
# Who else calls the owner goals or spawns these with an owner.
for n in names:
    if n.endswith('.class') and 'kraken' in n.lower() and ('Item' in n or 'Spawner' in n):
        p(f"class: {n}")
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
