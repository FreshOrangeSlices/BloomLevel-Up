import os, re, json, zipfile, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
out = []; p = out.append
for name, url in json.load(open('probe/jars5.json')).items():
    try:
        open('j.jar', 'wb').write(get(url)); z = zipfile.ZipFile('j.jar'); names = z.namelist()
        lang = {}
        for n in names:
            if re.search(r'assets/[^/]+/lang/en_us\.json$', n):
                try: lang.update(json.loads(z.read(n).decode('utf8', 'replace')))
                except Exception: pass
        ents = [(k.split('.', 1)[1], v) for k, v in lang.items() if re.match(r'entity\.[a-z0-9_]+\.[a-z0-9_]+$', k)]
        tags = {}
        for n in names:
            m = re.match(r'data/([^/]+)/tags/entity_types/(.+)\.json$', n)
            if m:
                try: tags[f"{m.group(1)}:{m.group(2)}"] = json.loads(z.read(n)).get('values', [])
                except Exception: pass
        mods = []
        for n in names:
            if '/forge/biome_modifier/' in n and n.endswith('.json'):
                try:
                    b = json.loads(z.read(n)); sp = b.get('spawners', [])
                    sp = sp if isinstance(sp, list) else [sp]
                    bio = b.get('biomes'); bio = bio if isinstance(bio, str) else (bio.get('type') if isinstance(bio, dict) else ','.join(bio)[:60])
                    for s in sp:
                        if isinstance(s, dict) and s.get('type'): mods.append(f"{s['type'].split(':')[-1]}@{bio}")
                except Exception: pass
        p(f"# {name}: {len(ents)} entities")
        p("; ".join(f"{k}={v}" for k, v in ents))
        if tags: p("tags: " + "; ".join(f"{k}: {','.join(str(x) for x in v)[:150]}" for k, v in tags.items())[:1500])
        if mods: p("own spawns: " + "; ".join(mods)[:1500])
    except Exception:
        p(f"# {name}: failed {traceback.format_exc()[-300:]}")
os.makedirs('probe-out', exist_ok=True)
open('probe-out/mobs.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
