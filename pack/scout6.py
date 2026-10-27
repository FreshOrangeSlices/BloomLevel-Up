"""Do these Modrinth projects have a 26.3 Fabric build? Type, sides, newest 26.3 version, downloads, one-line description."""
import json, sys, urllib.parse, urllib.request
UA = {'User-Agent': 'FreshOrangeSlices/BloomLevel-Up mod-check'}
API = 'https://api.modrinth.com/v2'
def get(path):
    try:
        return json.load(urllib.request.urlopen(urllib.request.Request(API + path, headers=UA), timeout=30))
    except Exception as e:
        return None
def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
out = []
for slug in sys.argv[1].split(','):
    p = get('/project/' + slug)
    if not p:
        hits = get('/search?' + urllib.parse.urlencode({'query': slug.replace('-', ' '), 'facets': '[["versions:26.3"],["categories:fabric"]]', 'limit': 3}))
        out.append(f"?? {slug}: not a slug; 26.3 fabric hits: " + '; '.join(f"{h['title']} ({h['slug']})" for h in (hits or {}).get('hits', [])))
        continue
    loader = 'datapack' if p['project_type'] == 'datapack' else ('minecraft' if p['project_type'] == 'resourcepack' else 'fabric')
    vs = get(f"/project/{slug}/version?" + urllib.parse.urlencode({'game_versions': '["26.3"]', 'loaders': json.dumps([loader])})) or []
    v = vs[0] if vs else None
    out.append(f"{'OK' if v else '--'} {p['title']} ({slug}) {p['project_type']} c={p['client_side']} s={p['server_side']} dl={p['downloads']//1000}k "
               f"{'26.3: ' + v['version_number'] + ' [' + v['version_type'] + '] ' + v['date_published'][:10] if v else 'newest: ' + (p['game_versions'][-1] if p['game_versions'] else '-')} | {p['description'][:110]}")
text = '\n'.join(out)
for n, i in enumerate(range(0, min(len(text), 3900 * 9), 3900)):
    print(f"::notice title=scout6-{n:02d}::{esc(text[i:i + 3900])}")
