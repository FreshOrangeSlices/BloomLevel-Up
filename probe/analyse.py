import os, re, sys, glob, zipfile, subprocess, json, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=60).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    vs = json.loads(get('https://api.modrinth.com/v2/project/sophisticated-storage/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D'))
    v = [v for v in vs if v['version_number'] == '1.20.1-1.5.19.2243'][0]
    f = [x for x in v['files'] if x.get('primary')] or v['files']
    open('ss.jar', 'wb').write(get(f[0]['url']))
    z = zipfile.ZipFile('ss.jar')
    lang = json.loads(z.read('assets/sophisticatedstorage/lang/en_us.json'))
    ids = sorted(set(re.sub(r'^(block|item)\.sophisticatedstorage\.', '', k) for k in lang if re.match(r'^(block|item)\.sophisticatedstorage\.[a-z0-9_]+$', k)))
    p("SS ids (" + str(len(ids)) + "): " + ", ".join(ids))
    models = sorted(n.split('/')[-1][:-5] for n in z.namelist() if n.startswith('assets/sophisticatedstorage/models/item/') and n.endswith('.json'))
    p("SS item models (" + str(len(models)) + "): " + ", ".join(models))
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
