"""Where WASD's download pages lead, what's in the files, and their terms."""
import io
import re
import urllib.request
import zipfile

UA = {'User-Agent': 'Mozilla/5.0 (FreshOrangeSlices/BloomLevel-Up mod-check)'}


def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


out = []
for url in ('http://wasdbuildteam.website/data-packs/wasd-libraries/download/', 'http://wasdbuildteam.website/data-packs/wasd-resources/download/',
            'https://wasdbuildteam.website/data-packs/moar-bosses/download/'):
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60)
        data = r.read()
        out.append(f'## {url} -> {r.geturl()} [{r.headers.get("Content-Type")}] {len(data)} bytes')
        if data[:2] == b'PK':
            z = zipfile.ZipFile(io.BytesIO(data))
            names = z.namelist()
            out.append(f'ZIP {len(names)} entries; top: ' + ' '.join(sorted({n.split("/")[0] for n in names}))[:600])
            inner = [n for n in names if n.lower().endswith('.zip')]
            out.append('inner zips: ' + ' '.join(inner[:40]))
            for n in names:
                if re.search(r'(terms|instruction|readme|license)', n, re.I) and n.lower().endswith('.txt'):
                    out.append(f'--- {n}: ' + re.sub(r'\s+', ' ', z.read(n).decode('utf-8', 'replace'))[:1500])
        else:
            html = data.decode('utf-8', 'replace')
            links = sorted(set(re.findall(r'href="([^"]+)"', html)))
            out.append('LINKS: ' + ' '.join(l for l in links if re.search(r'26|zip|download|drive|mediafire|dropbox|file', l, re.I))[:2500])
    except Exception as e:
        out.append(f'## {url}: {e}')

# The Moar Bosses zip from Modrinth: its own terms and instructions.
try:
    import json
    v = json.load(urllib.request.urlopen(urllib.request.Request('https://api.modrinth.com/v2/project/moar-bosses/version?game_versions=%5B%2226.3%22%5D', headers=UA)))[0]
    data = urllib.request.urlopen(urllib.request.Request(v['files'][0]['url'], headers=UA)).read()
    z = zipfile.ZipFile(io.BytesIO(data))
    for n in z.namelist():
        if n.lower().endswith('.txt'):
            out.append(f'--- moar-bosses/{n}: ' + re.sub(r'\s+', ' ', z.read(n).decode('utf-8', 'replace'))[:2000])
except Exception as e:
    out.append(f'moar bosses zip: {e}')

text = '\n'.join(out)
for n, i in enumerate(range(0, min(len(text), 3900 * 8), 3900)):
    print(f"::notice title=scout4-{n:02d}::{esc(text[i:i + 3900])}")
