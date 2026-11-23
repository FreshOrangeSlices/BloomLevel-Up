import os, re, sys, glob, zipfile, subprocess, json, urllib.request, traceback
root = sys.argv[1]
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=60).read()
def dec(j, cls, limit=9000):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return '\n'.join(l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip())[:limit]
os.makedirs('dl', exist_ok=True)
def one(slug):
    try:
        vs = json.loads(get(f'https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D'))
    except Exception as e:
        p(f"#### {slug}: {e}"); return
    if not vs:
        p(f"#### {slug}: no Forge 1.20.1 version"); return
    v = vs[0]
    f = [x for x in v['files'] if x.get('primary')] or v['files']
    p(f"#### {slug}: {v['version_number']} ({v['date_published'][:10]}), {len(vs)} versions; file {f[0]['filename']}; deps " +
      ", ".join(f"{d.get('project_id')}:{d.get('dependency_type')}" for d in v.get('dependencies', [])))
    path = os.path.join('dl', f[0]['filename'])
    open(path, 'wb').write(get(f[0]['url']))
    z = zipfile.ZipFile(path); names = z.namelist()
    for n in names:
        if n.endswith('mods.toml'):
            t = z.read(n).decode('utf8', 'replace')
            p("mods.toml deps: " + ' | '.join(re.findall(r'modId\s*=\s*"([^"]+)"[^\[]*?mandatory\s*=\s*(\w+)', t)))
    langs = [n for n in names if n.endswith('lang/en_us.json')]
    for n in langs:
        lang = json.loads(z.read(n))
        keys = sorted(k for k in lang if re.match(r'^(item|block)\.[a-z_0-9]+\.[a-z_0-9]+$', k))
        p(f"lang {n}: {len(keys)} items/blocks: " + ', '.join(f"{k.split('.',1)[1]}={lang[k]}" for k in keys)[:5000])
        other = [f"{k}={lang[k]}" for k in lang if re.search(r'key|config|option', k, re.I)][:30]
        if other: p("lang keys/config: " + ' | '.join(other)[:2000])
    rec = sorted(n.split('/recipes/')[1] for n in names if '/recipes/' in n and n.endswith('.json'))
    p(f"recipes ({len(rec)}): " + ', '.join(rec)[:3000])
    tags = sorted(n for n in names if '/tags/' in n and n.endswith('.json'))
    p(f"tags ({len(tags)}): " + ', '.join(tags)[:1500])
    for n in names:
        if n.endswith('.class') and re.search(r'[Cc]onfig', n) and '$' not in n and slug != 'sophisticated-storage':
            p(f"== {n}\n{dec(path, n[:-6].replace('/', '.'), 6000)}")
for slug in ["only-hammers-and-excavators", "sophisticated-storage", "construction-wand", "enchantment-descriptions"]:
    try:
        one(slug)
    except Exception:
        p(f"#### {slug} failed: " + traceback.format_exc()[-1500:])
open('report.txt', 'w').write('\n'.join(out))
# Jars already in the pack.
zips = glob.glob(os.path.join(root, '**', '*server*.zip'), recursive=True)
with zipfile.ZipFile(zips[0]) as z:
    jars = [n for n in z.namelist() if n.endswith('.jar') and '/mods/' in '/' + n]
    for n in jars:
        if re.search(r'bettercombat|skarrier|spell_engine|spell-engine|spellengine', n, re.I):
            z.extract(n, 'mods')
def packjar(j):
    zf = zipfile.ZipFile(j); names = zf.namelist()
    p(f"#### pack jar {os.path.basename(j)}")
    presets = sorted(n.split('weapon_attributes/')[1] for n in names if 'data/bettercombat/weapon_attributes/' in n)
    if presets: p("bettercombat presets: " + ', '.join(presets))
    for n in names:
        if n.endswith('.class') and '$' not in n and (re.search(r'skarrier', j, re.I) and re.search(r'enchant|config', n, re.I)
                or re.search(r'spell', j, re.I) and re.search(r'(Enchant|Config)', n) and re.search(r'infinit|Config', n, re.I)):
            src = dec(j, n[:-6].replace('/', '.'), 5000)
            if re.search(r'skarrier', j, re.I) or re.search(r'infinit', src, re.I):
                p(f"== {n}\n{src}")
    for n in names:
        if n.endswith('.json') and 'enchant' in n.lower() and 'lang' not in n:
            p(f"json {n}: {zf.read(n)[:400]!r}")
for j in glob.glob('mods/**/*.jar', recursive=True):
    try:
        packjar(j)
    except Exception:
        p(f"#### {j} failed: " + traceback.format_exc()[-1500:])
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
