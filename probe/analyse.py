import os, re, json, zipfile, urllib.request, traceback, io
def get(url):
    req = urllib.request.Request(url.replace(' ', '%20'), headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
jars = {}
for f in ['probe/jars.json', 'probe/jars2.json', 'probe/jars3.json']:
    jars.update(json.load(open(f)))
out = []; p = out.append
try:
    z = zipfile.ZipFile(io.BytesIO(get(jars['borninchaos'])))
    lang = json.loads(z.read('assets/born_in_chaos_v1/lang/en_us.json').decode('utf-8', 'replace'))
    for k, v in lang.items():
        if re.search(r'(?i)transmut|elixir', k):
            p(f"lang {k} = {v}")
    want = set()
    for n in z.namelist():
        if n.startswith('data/') and n.endswith('.json'):
            t = z.read(n).decode('utf-8', 'replace')
            if 'transmuting' in t or 'transmuting' in n:
                p(f"-- {n}\n{re.sub(chr(10) + '| {2,}', '', t)[:900]}")
                want.update(re.findall(r'"(born_in_chaos_v1:[a-z_]+)"', t))
    for i in sorted(want):
        for n in z.namelist():
            if n.startswith('data/') and '/recipes/' in n and n.endswith('.json'):
                t = z.read(n).decode('utf-8', 'replace')
                if re.search(r'"result"[^}]*"' + re.escape(i) + '"', t):
                    p(f"-- makes {i}: {n}\n{re.sub(chr(10) + '| {2,}', '', t)[:600]}")
        loots = [n for n in z.namelist() if '/loot_tables/' in n and n.endswith('.json') and i in z.read(n).decode('utf-8', 'replace')]
        if loots:
            p(f"-- {i} in loot: {', '.join(x.split('/loot_tables/')[-1] for x in loots[:8])}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/elixir.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
