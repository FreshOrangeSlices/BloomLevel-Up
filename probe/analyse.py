import os, re, sys, glob, zipfile, subprocess, json, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=60).read()
def dec(j, cls, limit=6000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
try:
    def jar(slug, ver, name):
        vs = json.loads(get(f'https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D'))
        v = [v for v in vs if v['version_number'] == ver][0]
        f = [x for x in v['files'] if x.get('primary')] or v['files']
        open(name, 'wb').write(get(f[0]['url']))
        return zipfile.ZipFile(name).namelist()
    ss = jar('sophisticated-storage', '1.20.1-1.5.19.2243', 'ss.jar')
    core = jar('sophisticated-core', '1.20.1-1.5.40.2493', 'core.jar')
    src = dec('ss.jar', 'net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage', 200000)
    i = src.find('getNetworkProtocolVersion'); p("==== SophisticatedStorage\n" + src[:1800] + "\n...\n" + src[i-200:i+900] if i >= 0 else src[:3000])
    for n in core:
        if re.search(r'network/(PacketHandler|SimplePacketBase|SophisticatedPacketHandler)\.class$', n):
            p(f"==== {n}\n{dec('core.jar', n[:-6].replace('/', '.'), 3500)}")
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
