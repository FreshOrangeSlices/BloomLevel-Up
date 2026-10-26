"""Links on the WASD pages (Moar Bosses' library and resource pack), and the 26.3 dependencies of a few projects."""
import json
import re
import sys
import urllib.parse
import urllib.request

UA = {'User-Agent': 'Mozilla/5.0 (FreshOrangeSlices/BloomLevel-Up mod-check)'}
API = 'https://api.modrinth.com/v2'


def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode('utf-8', 'replace')


def esc(t):
    return t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


out = []
for url in ('https://wasdbuildteam.website/data-packs/wasd-libraries/', 'https://wasdbuildteam.website/data-packs/wasd-resources/',
            'https://wasdbuildteam.website/data-packs/moar-bosses/'):
    try:
        html = fetch(url)
        links = sorted(set(re.findall(r'href="([^"]+)"', html)))
        keep = [l for l in links if re.search(r'26\.|\.zip|download|mediafire|drive|planetminecraft|modrinth|curseforge|dropbox|github', l, re.I)]
        text = re.sub(r'<script.*?</script>|<style.*?</style>', ' ', html, flags=re.S)
        text = re.sub(r'<[^>]+>', ' ', text)
        text = re.sub(r'\s+', ' ', text)
        i = text.find('26.3')
        out.append(f'## {url}\nLINKS: ' + ' '.join(keep[:60]) + f'\nTEXT near 26.3: {text[max(0, i - 600):i + 600] if i >= 0 else text[:900]}')
    except Exception as e:
        out.append(f'## {url}: {e}')

for slug in sys.argv[1].split(','):
    vs = json.load(urllib.request.urlopen(urllib.request.Request(
        API + f'/project/{slug}/version?' + urllib.parse.urlencode({'game_versions': '["26.3"]', 'loaders': '["fabric"]'}), headers=UA), timeout=30))
    for v in vs[:1]:
        deps = []
        for d in v.get('dependencies', []):
            name = d.get('file_name') or d.get('project_id')
            if d.get('project_id'):
                p = json.load(urllib.request.urlopen(urllib.request.Request(API + '/project/' + d['project_id'], headers=UA), timeout=30))
                name = f"{p['slug']} ({p['title']}, 26.3={'26.3' in p['game_versions']})"
            deps.append(f"{name}:{d['dependency_type']}")
        out.append(f'## {slug} {v["version_number"]}: deps={deps}')

text = '\n'.join(out)
for n, i in enumerate(range(0, min(len(text), 3900 * 8), 3900)):
    print(f"::notice title=scout3-{n:02d}::{esc(text[i:i + 3900])}")
