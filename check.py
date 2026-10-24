import json, urllib.request, urllib.parse, time
API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up mod-check (github actions)"}
def get(path, **params):
    q = "&".join(f"{k}={urllib.parse.quote(json.dumps(v) if isinstance(v, list) else str(v))}" for k, v in params.items())
    url = f"{API}{path}" + (f"?{q}" if q else "")
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)
lines = []
for slug in ["better-combat", "player-animation-library", "cloth-config"]:
    for v in get(f"/project/{slug}/version", game_versions=["26.3"], loaders=["fabric"])[:3]:
        deps = ", ".join(f"{d.get('dependency_type')}:{d.get('project_id')}@{d.get('version_id')}" for d in v.get("dependencies", []))
        lines.append(f"{slug}: version_number={v['version_number']} id={v['id']} type={v['version_type']} date={v['date_published'][:10]} files={[f['filename'] for f in v['files']]} deps=[{deps}]")
msg = "\n".join(lines).replace("%", "%25").replace("\n", "%0A")
print(f"::notice title=mods-versions::{msg}")
