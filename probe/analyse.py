import os, re, sys, json, zipfile, urllib.request, traceback, collections
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
os.makedirs('probe-out', exist_ok=True)
summary = []
for name, url in json.load(open('probe/jars3.json')).items():
    out = []; p = out.append
    try:
        path = 'j.jar'; open(path, 'wb').write(get(url)); z = zipfile.ZipFile(path); names = z.namelist()
        toml = next((n for n in names if n.endswith('mods.toml')), None)
        if toml:
            t = z.read(toml).decode('utf8', 'replace')
            m = re.findall(r'modId\s*=\s*"([^"]+)"', t); dsc = re.search(r"description\s*=\s*'''(.*?)'''", t, re.S) or re.search(r'description\s*=\s*"([^"]*)"', t)
            p(f"# {name}: mod ids {m}"); p("description: " + (dsc.group(1).strip()[:800] if dsc else ''))
        langs = [n for n in names if re.search(r'assets/[^/]+/lang/en_us\.json$', n)]
        lang = {}
        for n in langs:
            try: lang.update(json.loads(z.read(n).decode('utf8', 'replace')))
            except Exception as e: p(f"lang {n} unreadable: {e}")
        groups = collections.defaultdict(list)
        for k, v in lang.items():
            kind = k.split('.')[0]
            groups[kind].append((k, v))
        for kind in ('item', 'block', 'entity', 'effect', 'enchantment'):
            if groups.get(kind):
                p(f"## {kind} ({len(groups[kind])})")
                p("; ".join(f"{'.'.join(k.split('.')[1:3])}={v}" for k, v in groups[kind] if k.count('.') == 2)[:6000])
                extra = [(k, v) for k, v in groups[kind] if k.count('.') > 2]
                if extra:
                    p(f"## {kind} details")
                    for k, v in extra[:120]: p(f"{k}: {str(v)[:240]}")
        adv = [(k, v) for k, v in lang.items() if 'advancement' in k.split('.')[0] or '.advancement' in k or k.startswith('advancements.')]
        if adv:
            p("## advancement text"); [p(f"{k}: {str(v)[:200]}") for k, v in adv[:120]]
        other = [(k, v) for k, v in lang.items() if k.split('.')[0] not in ('item', 'block', 'entity', 'effect', 'enchantment', 'advancements', 'advancement', 'subtitles', 'death', 'itemGroup', 'text', 'key', 'config', 'sound')
                 and re.search(r'(?i)tooltip|desc|lore|info|guide|hint|tip|book|page', k)]
        if other:
            p("## other text"); [p(f"{k}: {str(v)[:240]}") for k, v in other[:150]]
        advs = [n for n in names if re.search(r'data/[^/]+/advancements/.+\.json$', n)]
        p(f"## advancement files ({len(advs)})")
        for n in advs[:150]:
            try:
                a = json.loads(z.read(n)); crit = a.get('criteria', {})
                trig = ", ".join(sorted(set(c.get('trigger', '?').split(':')[-1] for c in crit.values())))
                disp = a.get('display', {}); title = disp.get('title'); title = title.get('translate', title) if isinstance(title, dict) else title
                ident = n.replace('data/', '').replace('/advancements/', ':').replace('.json', '')
                p(f"{ident} [{trig}] {lang.get(title, title) if isinstance(title, str) else ''} hidden={disp.get('hidden', False)}")
            except Exception as e:
                p(f"{n}: {e}")
        recs = [n for n in names if re.search(r'data/[^/]+/recipes?/.+\.json$', n)]
        types = collections.Counter(); custom = []
        for n in recs:
            try:
                r = json.loads(z.read(n)); ty = r.get('type', '?'); types[ty] += 1
                res = r.get('result') or r.get('output') or {}
                res = res.get('item', res.get('id', '')) if isinstance(res, dict) else res
                if not ty.startswith('minecraft:crafting') and ty not in ('minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking', 'minecraft:stonecutting'):
                    custom.append(f"{n.split('/')[-1][:-5]} ({ty}) -> {res}")
            except Exception:
                pass
        p(f"## recipe types: {dict(types)}")
        if custom: p("## other recipes: " + "; ".join(custom[:80]))
        structs = sorted(n.split('/worldgen/structure/')[-1][:-5] for n in names if '/worldgen/structure/' in n and n.endswith('.json'))
        if structs: p("## structures: " + ", ".join(structs))
        loot = [n for n in names if '/loot_tables/entities/' in n and n.endswith('.json')]
        if loot:
            p(f"## entity drops ({len(loot)})")
            for n in loot[:80]:
                try:
                    lt = json.loads(z.read(n)); items = set()
                    def walk(o):
                        if isinstance(o, dict):
                            if o.get('type', '').endswith('item') and 'name' in o: items.add(o['name'])
                            for v in o.values(): walk(v)
                        elif isinstance(o, list):
                            for v in o: walk(v)
                    walk(lt)
                    p(f"{n.split('/entities/')[-1][:-5]}: {', '.join(sorted(items))[:300]}")
                except Exception:
                    pass
        summary.append(f"{name}: ok, {len('\n'.join(out))} chars")
    except Exception:
        out.append(traceback.format_exc()); summary.append(f"{name}: failed")
    open(os.path.join('probe-out', re.sub(r'\W+', '_', name) + '.txt'), 'w').write('\n'.join(out))
open('report.txt', 'w').write('\n'.join(summary))
print('\n'.join(summary))
