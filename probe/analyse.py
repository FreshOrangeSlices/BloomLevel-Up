import os, re, json, zipfile, subprocess, urllib.request, traceback, io
def get(url):
    url = url.replace(' ', '%20')
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
def dec(cls, jar):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ') and not l.startswith('/*')]
jars = {}
for f in ['probe/jars.json', 'probe/jars2.json', 'probe/jars3.json']:
    jars.update(json.load(open(f)))
out = []; p = out.append
def scan(key, jarname, needles, limit=12):
    try:
        open(jarname, 'wb').write(get(jars[key])); z = zipfile.ZipFile(jarname)
    except Exception as e:
        p(f"!! {key}: {e}"); return
    hits = {nd: [] for nd in needles}
    for n in z.namelist():
        if not n.endswith('.class') or '$' in n:
            continue
        data = z.read(n)
        for nd in needles:
            if nd.encode() in data:
                hits[nd].append(n)
    for nd, files in hits.items():
        p(f"==== {key}: {nd} named in {len(files)} classes")
        for n in files[:limit]:
            src = "\n".join(dec(n[:-6].replace('/', '.'), jarname))
            lines = [l for l in src.split("\n") if nd in l or 'SubscribeEvent' in l or 'public static void execute' in l or 'm_7967_' in l or 'spawn' in l.lower() or 'random' in l.lower()]
            p(f"---- {n}\n" + "\n".join(l[:300] for l in lines[:14]))
    for n in z.namelist():
        if n.startswith('data/') and n.endswith('.json'):
            t = z.read(n).decode('utf-8', 'replace')
            for nd in needles:
                low = nd.lower()
                if low in t:
                    p(f"-- data {n} names {nd}: {t[:300]}")
try:
    scan('Skarrier Mobs', 'sk.jar', ['PURE_DARKNESS_ITEM', 'WRONG_DEEPSLATE_ORE_INVULNERITE', 'WRETCH', 'TANGLED_WRONG_DEEPSLATE'])
    scan('irons-spells-n-spellbooks', 'ir.jar', ['twilight_gale_crystal', 'TWILIGHT_GALE_CRYSTAL', 'LIGHTNING_BOTTLE'], 6)
    scan('aquamirae', 'aq.jar', ['DAGGER_OF_GREED', 'dagger_of_greed'], 6)
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/sources.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
