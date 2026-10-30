"""Throwaway: chunk 2's versions. Usage: reports7.py <server.zip> <shots dir> <out dir>"""
import json, os, sys, urllib.request
OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True); lines = []
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"}), timeout=120) as r:
        return json.loads(r.read())
for ref in ("backport-copper-age", "azurelib", "book-of-dragons", "wGKYL7st"):
    p = get(f"https://api.modrinth.com/v2/project/{ref}")
    lines.append(f"{ref}: slug={p['slug']} title={p['title']} id={p['id']} client={p.get('client_side')} server={p.get('server_side')}")
    vs = get(f"https://api.modrinth.com/v2/project/{p['id']}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    for v in vs[:8]:
        lines.append(f"   {v['version_number']} | {v['version_type']} | {v['date_published'][:10]} | {v['id']} | {v['files'][0]['filename']} | deps {[(d.get('project_id'), d.get('version_id'), d.get('dependency_type')) for d in v.get('dependencies', [])]}")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines))
