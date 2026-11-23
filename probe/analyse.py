import os, re, sys, json, zipfile, urllib.request, traceback, collections
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
os.makedirs('probe-out', exist_ok=True)
summary = []
for name, url in json.load(open('probe/jars4.json')).items():
    out = []; p = out.append
    try:
        open('j.jar', 'wb').write(get(url)); z = zipfile.ZipFile('j.jar'); names = z.namelist()
        toml = next((n for n in names if n.endswith('mods.toml')), None)
        if toml:
            t = z.read(toml).decode('utf8', 'replace')
            dsc = re.search(r"description\s*=\s*'''(.*?)'''", t, re.S) or re.search(r'description\s*=\s*"([^"]*)"', t)
            p(f"# {name}: {re.findall(r'modId\s*=\s*\"([^\"]+)\"', t)}"); p("description: " + (dsc.group(1).strip()[:600] if dsc else ''))
        lang = {}
        for n in names:
            if re.search(r'assets/[^/]+/lang/en_us\.json$', n):
                try: lang.update(json.loads(z.read(n).decode('utf8', 'replace')))
                except Exception: pass
        items = [(k, v) for k, v in lang.items() if re.match(r'(item|block)\.[^.]+\.[^.]+$', k)]
        p(f"## items/blocks ({len(items)}): " + "; ".join(f"{k.split('.')[2]}={v}" for k, v in items)[:5000])
        tips = [(k, v) for k, v in lang.items() if re.search(r'(?i)tooltip|desc|info|guide|hint|help|usage|upgrade|tier', k) and not k.startswith(('key.', 'config', 'text.autoconfig'))]
        p(f"## tips ({len(tips)})")
        for k, v in tips[:220]: p(f"{k}: {str(v)[:260]}")
        advs = [n for n in names if re.search(r'data/[^/]+/advancements/.+\.json$', n) and '/recipes/' not in n]
        p(f"## advancements ({len(advs)}): " + ", ".join(n.replace('data/', '').replace('/advancements/', ':')[:-5] for n in advs[:80]))
        recs = [n for n in names if re.search(r'data/[^/]+/recipes?/.+\.json$', n)]
        p(f"## recipes ({len(recs)})")
        for n in recs:
            try:
                r = json.loads(z.read(n)); res = r.get('result') or r.get('output') or {}
                res = res.get('item', res.get('id', '')) if isinstance(res, dict) else res
                keys = sorted(set(re.findall(r'"(?:item|tag)": "([^"]+)"', json.dumps({k: v for k, v in r.items() if k not in ('result', 'output')}))))
                p(f"{n.split('/')[-1][:-5]} [{r.get('type','?').split(':')[-1]}] -> {res} <= {', '.join(keys)[:220]}")
            except Exception:
                pass
        summary.append(f"{name}: ok {len(chr(10).join(out))}")
    except Exception:
        out.append(traceback.format_exc()); summary.append(f"{name}: failed")
    open(os.path.join('probe-out', re.sub(r'\W+', '_', name) + '.txt'), 'w').write('\n'.join(out))
open('report.txt', 'w').write('\n'.join(summary))
