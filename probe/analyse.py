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
    p("SS versions: " + ", ".join(v['version_number'] for v in vs[:40]))
    v = [v for v in vs if v['version_number'] == '1.20.1-1.5.19.2243'][0]
    for d in v['dependencies']:
        p(f"dep {d}")
    f = [x for x in v['files'] if x.get('primary')] or v['files']
    open('ss.jar', 'wb').write(get(f[0]['url']))
    z = zipfile.ZipFile('ss.jar'); names = z.namelist()
    for cls in ['net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock',
                'net.p3pp3rf1y.sophisticatedstorage.network.StoragePacketHandler']:
        n = cls.replace('.', '/') + '.class'
        p(f"==== {cls} present={n in names}")
        if n in names:
            src = dec('ss.jar', cls, 200000)
            keep = [l for l in src.splitlines() if 'static' in l or 'INSTANCE' in l or 'class ' in l]
            p("\n".join(keep)[:5000])
    vc = json.loads(get('https://api.modrinth.com/v2/project/backport-copper-age/version'))
    p("Copper Age versions: " + ", ".join(f"{v['version_number']} {v['loaders']} {v['game_versions']} {v['date_published'][:10]}" for v in vc[:15]))
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
