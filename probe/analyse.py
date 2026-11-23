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
    p(f"jar {f[0]['filename']}, {len(names)} entries")
    pat = re.compile(rb'sophisticated|onlyhammers|bookshelf|darkhax|constructionwand|enchdesc|TierSortingRegistry|hammer|excavator', re.I)
    for n in names:
        if n.endswith('.class'):
            data = z.read(n)
            hits = sorted(set(m.group(0).decode('latin1') for m in pat.finditer(data)))
            if hits:
                p(f"{n}: {hits}")
    for cls in ['com.github.smallinger.copperagebackport.CommonClass',
                'com.github.smallinger.copperagebackport.compat.modules.SophisticatedStorageCompat']:
        p(f"==== {cls}\n{dec('cab.jar', cls, 9000)}")
    for n in names:
        if 'compat' in n.lower() and n.endswith('.class'):
            p(f"compat class {n}")
    for n in names:
        if n.endswith('mods.toml'):
            p(z.read(n).decode('utf8', 'replace')[:1500])
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
