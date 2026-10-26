"""Details for a few Modrinth projects: description excerpt, 26.3 files and what's inside them."""
import io
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile

API = 'https://api.modrinth.com/v2'
HEAD = {'User-Agent': 'FreshOrangeSlices/BloomLevel-Up mod-check'}


def get(path):
    with urllib.request.urlopen(urllib.request.Request(API + path, headers=HEAD), timeout=30) as r:
        return json.load(r)


def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


for slug in sys.argv[1].split(','):
    out = []
    p = get('/project/' + slug)
    body = re.sub(r'<[^>]+>|!\[[^\]]*\]\([^)]*\)', ' ', p.get('body', ''))
    body = re.sub(r'\s+', ' ', body)
    out.append(f"## {slug}: {p['title']} — {p['description']}\nclient={p['client_side']} server={p['server_side']}\nBODY: {body[:1500]}")
    vs = get('/project/' + slug + '/version?' + urllib.parse.urlencode({'game_versions': '["26.3"]'}))
    fabric = [v for v in vs if 'fabric' in v['loaders'] or 'datapack' in v['loaders']]
    if fabric:
        v = fabric[0]
        f = next((f for f in v['files'] if f.get('primary')), v['files'][0])
        data = urllib.request.urlopen(urllib.request.Request(f['url'], headers=HEAD), timeout=60).read()
        z = zipfile.ZipFile(io.BytesIO(data))
        names = z.namelist()
        out.append(f"FILE {f['filename']} ({len(data)//1024} KB, {len(names)} entries)")
        dirs = sorted({'/'.join(n.split('/')[:3]) for n in names})
        out.append('DIRS: ' + ' '.join(dirs[:80]))
        for meta in ('fabric.mod.json', 'pack.mcmeta'):
            if meta in names:
                out.append(f"{meta}: " + re.sub(r'\s+', ' ', z.read(meta).decode('utf-8', 'replace'))[:900])
        ents = sorted({n.split('/')[-1].split('.')[0] for n in names if '/entity_type/' in n or ('lang/en_us' in n)})
        lang = [n for n in names if n.endswith('lang/en_us.json')]
        if lang:
            try:
                L = json.loads(z.read(lang[0]))
                ent = [f"{k}={v}" for k, v in L.items() if k.startswith('entity.')]
                items = [f"{k}={v}" for k, v in L.items() if k.startswith('item.')]
                out.append('ENTITIES: ' + '; '.join(ent[:80]))
                out.append(f'ITEMS ({len(items)}): ' + '; '.join(items[:120]))
            except Exception as e:
                out.append(f'lang: {e}')
        out.append('FILES SAMPLE: ' + ' '.join(n for n in names if not n.endswith('/'))[:2500])
    text = '\n'.join(out)
    for n, i in enumerate(range(0, min(len(text), 3900 * 3), 3900)):
        print(f"::notice title={slug}-{n:02d}::{esc(text[i:i + 3900])}")
