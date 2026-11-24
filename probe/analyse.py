import os, re, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    url = url.replace(' ', '%20')
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(cls, jar='j.jar'):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ') and not l.startswith('/*')]
out = []; p = out.append
WEAPONS = ['ban_sword', 'blitzblade', 'decayerite_sword', 'invulnerite_axe', 'invulnerite_sword', 'morgenstern', 'resisteel_axe', 'voracious_blade',
           'ascendant_ingot', 'contagium_ingot', 'decayerite_ingot', 'pristinium_ingot', 'invulnerite', 'resisteel']
try:
    jars = json.load(open('probe/jars.json'))
    open('j.jar', 'wb').write(get(jars['Skarrier Mobs'])); z = zipfile.ZipFile('j.jar'); names = z.namelist()
    lang = json.loads(z.read('assets/skarrier_mobs/lang/en_us.json').decode('utf-8', 'replace'))
    p("==== lang")
    for k, v in lang.items():
        if any(w.split('_')[0] in k for w in WEAPONS) or re.search(r'invuln|morgen|blitz|voracious|ban_|resisteel|decayer|tooltip|desc', k):
            p(f"{k} = {v}")
    p("==== data files naming them")
    for n in names:
        if n.startswith('data/') and n.endswith('.json'):
            t = z.read(n).decode('utf-8', 'replace')
            hits = [w for w in WEAPONS if 'skarrier_mobs:' + w in t]
            if hits:
                p(f"-- {n}: {hits}\n{t[:900]}")
    p("==== item classes")
    item_classes = [n for n in names if n.endswith('.class') and '/item/' in n.lower() and '$' not in n
                    and re.search(r'(?i)invuln|morgen|blitz|voracious|ban|resisteel|decayer', n)]
    p("\n".join(item_classes))
    for n in item_classes[:14]:
        src = dec(n[:-6].replace('/', '.'))
        p(f"---- {n}\n" + "\n".join(src)[:3500])
    # procedures that mention these items (MCreator)
    p("==== procedures naming them")
    for n in names:
        if n.endswith('.class') and '$' not in n and re.search(r'(?i)procedure', n) and re.search(r'(?i)invuln|morgen|blitz|voracious|ban|resisteel|decayer', n):
            src = dec(n[:-6].replace('/', '.'))
            p(f"---- {n}\n" + "\n".join(src)[:2500])
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/skarrier.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
