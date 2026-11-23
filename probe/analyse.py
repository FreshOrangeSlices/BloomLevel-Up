import os, re, sys, glob, zipfile, subprocess
root = sys.argv[1]
out = []; p = out.append
zips = glob.glob(os.path.join(root, '**', '*server*.zip'), recursive=True)
with zipfile.ZipFile(zips[0]) as z:
    jar = [n for n in z.namelist() if 'xaero' in n.lower() and 'unified' in n.lower() and n.endswith('.jar')][0]
    z.extract(jar, 'mods')
j = os.path.join('mods', jar)
zf = zipfile.ZipFile(j)
names = zf.namelist()
p("jar: " + jar)
for n in names:
    if n.endswith('.class') and re.search(r'command|render', n, re.I):
        p("  " + n)
def dec(n, limit=16000):
    cls = n[:-6].replace('/', '.')
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
for n in names:
    if n.endswith('.class') and re.search(r'command', n, re.I) and '$' not in n:
        p(f"==== {n}\n{dec(n)}")
for n in names:
    if n.endswith(('.md', '.txt', 'en_us.json')) or 'lang' in n:
        try:
            txt = zf.read(n).decode('utf8', 'replace')
            if re.search(r'render', txt, re.I):
                p(f"==== {n}\n{txt[:4000]}")
        except Exception:
            pass
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
