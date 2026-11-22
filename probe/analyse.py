import json, os, re, sys, glob, zipfile, subprocess
root = sys.argv[1]
out = []; p = out.append
dump = glob.glob(os.path.join(root, '**', 'bloom-dump.jsonl'), recursive=True)[0]
rows = [json.loads(l) for l in open(dump) if l.strip()]
pat = re.compile(r'cutlass|saber|sabre|pirate|corsair|buccaneer|captain', re.I)
cands = []
for r in rows:
    if r.get('kind') in ('item', 'entity') and (pat.search(r['id']) or pat.search(str(r.get('name') or ''))):
        s = json.dumps(r)
        p(s[:1500])
        if r.get('kind') == 'item' and (r.get('damage') or 0) > 0:
            cands.append(r)
p(f"\n== {len(cands)} weapon candidates: {[c['id'] for c in cands]}")
# Mods from the server zip.
zips = glob.glob(os.path.join(root, '**', '*server*.zip'), recursive=True)
p(f"server zips: {zips}")
mods = 'mods'
os.makedirs(mods, exist_ok=True)
with zipfile.ZipFile(zips[0]) as z:
    for n in z.namelist():
        if n.endswith('.jar') and '/mods/' in '/' + n:
            z.extract(n, mods)
jars = glob.glob(os.path.join(mods, '**', '*.jar'), recursive=True)
p(f"{len(jars)} jars")
def jar_for(ns):
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                if any(n.startswith(f'assets/{ns}/') or n.startswith(f'data/{ns}/') for n in z.namelist()):
                    return j
        except Exception:
            pass
def find_class(j, simple):
    with zipfile.ZipFile(j) as z:
        return [n for n in z.namelist() if n.endswith('/' + simple + '.class')]
def decompile(j, cls, limit=14000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls.replace('/', '.')[:-6]) + '$'],
                       capture_output=True, text=True)
    src = r.stdout
    return src[:limit] + ('\n... (cut)' if len(src) > limit else '')
seen = set()
for c in cands:
    ns = c['id'].split(':')[0]
    j = jar_for(ns)
    simple = (c.get('classes') or ['?'])[0]
    p(f"\n==== {c['id']} class {simple} jar {j}")
    if not j:
        continue
    for cls in find_class(j, simple):
        src = decompile(j, cls)
        p(f"---- {cls}\n{src}")
        # Classes of the same mod it refers to that look like entities or goals.
        for ref in sorted(set(re.findall(r'import ([\w.]+);', src))):
            if ref.startswith(cls.split('/')[0]) and re.search(r'entit|goal|summon|pirate|minion|ghost|spirit', ref, re.I) and ref not in seen:
                seen.add(ref)
                path = ref.replace('.', '/') + '.class'
                p(f"---- referenced {ref}\n{decompile(j, path, 12000)}")
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
