import os, re, sys, glob, zipfile, subprocess, json, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=60).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    v = json.loads(get('https://api.modrinth.com/v2/version/BeVdLB9R'))
    f = [x for x in v['files'] if x.get('primary')] or v['files']
    open('cab.jar', 'wb').write(get(f[0]['url']))
    z = zipfile.ZipFile('cab.jar'); names = z.namelist()
    for n in names:
        if re.search(r'(ModCompat|Config\w*|IModCompatModule)\.class$', n) or n.endswith('.toml') or n.endswith('mixins.json'):
            p(f"file {n}")
    for n in names:
        if re.search(r'/(ModCompat|IModCompatModule)\.class$', n):
            p(f"==== {n}\n{dec('cab.jar', n[:-6].replace('/', '.'), 5000)}")
    for n in names:
        if re.search(r'Config\w*\.class$', n) and '$' not in n:
            src = dec('cab.jar', n[:-6].replace('/', '.'), 60000)
            keep = [l for l in src.splitlines() if re.search(r'define|comment|push|compat|Compat|sophist', l)]
            p(f"==== {n}\n" + "\n".join(keep)[:3000])
    # 0.1.5 for Forge 1.20.1: does its compat still run at construction?
    vs = json.loads(get('https://api.modrinth.com/v2/project/backport-copper-age/version'))
    for vv in vs:
        if vv['version_number'] in ('1.20.1-0.1.5', '1.20.1-0.1.5.1-pre') and 'forge' in vv['loaders']:
            ff = [x for x in vv['files'] if x.get('primary')] or vv['files']
            open('cab5.jar', 'wb').write(get(ff[0]['url']))
            p(f"==== {vv['version_number']} {vv['version_type']} changelog: {(vv.get('changelog') or '')[:1500]}")
            p(dec('cab5.jar', 'com.github.smallinger.copperagebackport.compat.modules.SophisticatedStorageCompat', 2500))
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
