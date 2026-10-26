"""Origins: Legacy's syncAttacker mixin: where it injects and what it does (javap)."""
import io, json, os, re, subprocess, sys, urllib.request, zipfile
UA = {'User-Agent': 'FreshOrangeSlices/BloomLevel-Up mod-check'}
API = 'https://api.modrinth.com/v2'
def get(path):
    return json.load(urllib.request.urlopen(urllib.request.Request(API + path, headers=UA), timeout=30))
def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
out = []
v = get('/project/origins-legacy/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D')[0]
data = urllib.request.urlopen(urllib.request.Request(v['files'][0]['url'], headers=UA)).read()
z = zipfile.ZipFile(io.BytesIO(data))
jars = [(n, z.read(n)) for n in z.namelist() if n.endswith('.jar')]
out.append('origins-legacy ' + v['version_number'] + ' nested: ' + ' '.join(n for n, _ in jars))
os.makedirs('x', exist_ok=True)
for name, blob in [('origins', data)] + jars:
    try:
        zz = zipfile.ZipFile(io.BytesIO(blob))
    except Exception:
        continue
    for n in zz.namelist():
        if n.endswith('.class'):
            b = zz.read(n)
            if b'syncAttacker' in b:
                path = os.path.join('x', n.replace('/', '_'))
                open(path, 'wb').write(b)
                dis = subprocess.run(['javap', '-c', '-p', '-v', path], capture_output=True, text=True).stdout
                # the annotations of syncAttacker and its code
                i = dis.find('syncAttacker')
                ann = [l for l in dis.splitlines() if 'Inject' in l or 'At' in l and 'value' in l or 'method=' in l or 'setLastHurtByMob' in l][:20]
                m = re.search(r'(void syncAttacker\(.*?)(?:\n\n|\Z)', dis, re.S)
                out.append(f'== {name}:{n}\n' + '\n'.join(ann) + '\n' + (m.group(1)[:3000] if m else dis[i - 200:i + 2500]))
text = '\n'.join(out)
for n, i in enumerate(range(0, min(len(text), 3900 * 6), 3900)):
    print(f"::notice title=scout5-{n:02d}::{esc(text[i:i + 3900])}")
