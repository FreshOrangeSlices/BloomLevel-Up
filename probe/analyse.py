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
    for cls in ['InvulneriteSwordItem', 'MorgensternItem', 'ResisteelAxeItem', 'VoraciousBladeItem', 'MorgensternLivingEntityIsHitWithToolProcedure',
                'VoraciousBladeLivingEntityIsHitWithToolProcedure', 'DecayeriteUpgradeTemplateItem', 'InvulneriteOreBlockDestroyedByPlayerProcedure']:
        hits = [n for n in names if n.endswith('/' + cls + '.class')]
        for n in hits:
            p(f"---- {n}\n" + "\n".join(dec(n[:-6].replace('/', '.')))[:2600])
    p("==== voracious / decayerite / invulnerite anywhere in class names")
    p("\n".join(n for n in names if n.endswith('.class') and '$' not in n and __import__('re').search(r'(?i)voracious|decayerite|invulnerite', n)))
    p("==== worldgen and data naming invulnerite or decayerite")
    for n in names:
        if n.startswith('data/') and n.endswith('.json') and ('invulnerite' in n or 'decayerite' in n or 'worldgen' in n):
            t = z.read(n).decode('utf-8', 'replace')
            if 'invulnerite' in t or 'decayerite' in t or 'invulnerite' in n or 'decayerite' in n:
                p(f"-- {n}\n{t[:700]}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/skarrier2.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
