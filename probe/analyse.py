import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    jars = json.load(open('probe/jars.json'))
    open('ss.jar', 'wb').write(get(jars['simply-swords']))
    z = zipfile.ZipFile('ss.jar'); names = z.namelist()
    p("simply swords file: " + jars['simply-swords'].split('/')[-1])
    for cls in ['net.sweenus.simplyswords.api.WeaponImplicitRegistry', 'net.sweenus.simplyswords.config.CompatibilityConfig']:
        p(f"==== {cls}\n{dec('ss.jar', cls, 7000)}")
    dirs = sorted(set('/'.join(n.split('/')[:3]) for n in names if n.startswith('data/')))
    p("data dirs: " + ", ".join(dirs))
    impl = [n for n in names if 'implicit' in n.lower()]
    p("implicit files: " + ", ".join(impl[:80]))
    lang = json.loads(z.read('assets/simplyswords/lang/en_us.json'))
    p("implicit lang: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if 'implicit' in k.lower())[:5000])
    # Oddities' Blade of the Orchid.
    open('od.jar', 'wb').write(get(jars['Bosses Oddities']))
    zo = zipfile.ZipFile('od.jar')
    for n in zo.namelist():
        if re.search(r'(?i)orchid.*(blade|sword)|blade.*orchid', n) and n.endswith('.class'):
            p(f"==== {n}\n{dec('od.jar', n[:-6].replace('/', '.'), 4000)}")
    for n in zo.namelist():
        if 'weapon_attributes' in n: p(f"oddities {n}: {zo.read(n).decode()[:400]}")
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
