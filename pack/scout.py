"""Scouts Modrinth for mods by name: the top hits that have a 26.3 build, then each named slug's 26.3 files.

Usage: scout.py "query1;query2" "slug1,slug2"   (prints GitHub annotations)
"""
import json
import sys
import urllib.parse
import urllib.request

API = 'https://api.modrinth.com/v2'
HEAD = {'User-Agent': 'FreshOrangeSlices/BloomLevel-Up mod-check'}


def get(path):
    with urllib.request.urlopen(urllib.request.Request(API + path, headers=HEAD), timeout=30) as r:
        return json.load(r)


def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


out = []
for query in [q for q in sys.argv[1].split(';') if q]:
    for label, facets in (('26.3', '[["versions:26.3"]]'), ('any', '[]')):
        q = urllib.parse.urlencode({'query': query, 'facets': facets, 'limit': 6})
        hits = get('/search?' + q)['hits']
        out.append(f'## "{query}" ({label}):')
        for h in hits:
            vs = h.get('versions', [])
            out.append(f"  {h['title']} | slug={h['slug']} | {h['project_type']} | by {h['author']} | cats={','.join(h.get('categories', []))} | "
                       f"dl={h['downloads']} | 26.3={'26.3' in vs} | newest={vs[-1] if vs else '-'}")

for slug in [s for s in sys.argv[2].split(',') if s] if len(sys.argv) > 2 else []:
    try:
        p = get('/project/' + slug)
        out.append(f"## {slug}: {p['title']} ({p['project_type']}) client={p['client_side']} server={p['server_side']} loaders={p['loaders']} "
                   f"games={p['game_versions'][-6:]}")
        vs = get('/project/' + slug + '/version?' + urllib.parse.urlencode({'game_versions': '["26.3"]'}))
        for v in vs[:4]:
            deps = []
            for d in v.get('dependencies', []):
                name = d.get('project_id')
                try:
                    name = get('/project/' + d['project_id'])['slug'] if d.get('project_id') else d.get('file_name')
                except Exception:
                    pass
                deps.append(f"{name}:{d.get('dependency_type')}")
            out.append(f"  {v['version_number']} [{v['version_type']}] loaders={v['loaders']} games={v['game_versions']} "
                       f"files={[f['filename'] for f in v['files']]} deps={deps} date={v['date_published'][:10]}")
        if not vs:
            out.append('  no 26.3 versions')
    except Exception as e:
        out.append(f'## {slug}: {e}')

text = '\n'.join(out)
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=scout-{n:02d}::{esc(text[i:i + 3900])}")
print(text)
