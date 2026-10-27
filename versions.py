import json, sys, urllib.request
out = []
for slug in sys.argv[1:]:
    req = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version", headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"})
    for v in json.load(urllib.request.urlopen(req, timeout=60))[:12]:
        out.append(f"{slug}: {v['version_number']!r} [{v['version_type']}] {v['date_published'][:16]} games={v['game_versions'][:4]} loaders={v['loaders']} files={[f['filename'] for f in v['files']]} deps={[(d.get('project_id'), d.get('version_id'), d['dependency_type']) for d in v['dependencies']]}")
text = "\n".join(out); print(text)
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=versions-{n:02d}::" + text[i:i+3900].replace("%", "%25").replace("\n", "%0A"))
