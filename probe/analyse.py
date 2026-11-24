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
    import re as _re
    want = [n for n in names if n.endswith('.class') and '$' not in n and _re.search(r'(?i)(bluntness|mass|mobility)enchantment|morgenstern', n)]
    p("\n".join(want))
    for n in want:
        src = dec(n[:-6].replace('/', '.'))
        p(f"---- {n}\n" + "\n".join(src)[:2200])
    for n in names:
        if n.endswith('.class') and '$' not in n and _re.search(r'(?i)procedure', n):
            src = dec(n[:-6].replace('/', '.'))
            text = "\n".join(src)
            if 'MOBILITY' in text:
                p(f"---- uses MOBILITY: {n}\n" + text[:2200])
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/skarrier3.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
