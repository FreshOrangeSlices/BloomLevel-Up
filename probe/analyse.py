import os, re, sys, json, zipfile, io, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
jars = json.load(open('probe/jars3.json'))
out = []; p = out.append
try:
    mz = zipfile.ZipFile(io.BytesIO(get('https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip')))
    tsrg = mz.read('config/joined.tsrg').decode()
    man = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    v = next(x for x in man['versions'] if x['id'] == '1.20.1')
    vj = json.loads(get(v['url'])); client = get(vj['downloads']['client_mappings']['url']).decode()
    # official -> obf for the two classes
    want = {'net.minecraft.world.item.Items': ['f_42579_', 'f_42436_', 'f_42437_'], 'net.minecraft.world.item.alchemy.Potions': ['f_43601_']}
    for cls, srgs in want.items():
        obf_cls = None; fields = {}
        lines = client.splitlines()
        for i, l in enumerate(lines):
            if l.startswith(cls + ' -> '):
                obf_cls = l.split(' -> ')[1].rstrip(':')
                for l2 in lines[i + 1:]:
                    if not l2.startswith('    '): break
                    m = re.match(r'\s+\S+ (\w+) -> (\w+)$', l2)
                    if m: fields[m.group(2)] = m.group(1)
                break
        # tsrg: find obf class block, map obf field -> srg
        srg_to_off = {}
        tl = tsrg.splitlines(); inside = False
        for l in tl:
            if not l.startswith('\t') and not l.startswith(' '):
                inside = l.split(' ')[0] == obf_cls
                continue
            if inside:
                parts = l.strip().split(' ')
                if len(parts) >= 2 and parts[1].startswith('f_'):
                    srg_to_off[parts[1]] = fields.get(parts[0], '?')
        for s in srgs: p(f"{cls}.{s} = {srg_to_off.get(s, '?')}")
except Exception: p(traceback.format_exc())
def jar(name):
    open('j.jar', 'wb').write(get(jars[name])); return zipfile.ZipFile('j.jar')
try:
    z = jar('Skarrier Mobs'); names = z.namelist()
    p("blank totem: " + re.sub(r'\s+', ' ', z.read([n for n in names if n.endswith('blank_totem_recipe.json')][0]).decode()))
    for n in names:
        if n.startswith('data/') and n.endswith('.json'):
            b = z.read(n)
            if re.search(rb'resisteel_ingot|resisteel_block|grave_construct|ancient_remains|heart_of_tranquility|wrong_deepslate', b) and '/recipes/' not in n and 'advancement' not in n and 'tags/' not in n and 'blocks/' not in n.split('loot_tables/')[-1][:7]:
                p(f"names {n}: {re.sub(chr(10)+'| +', ' ', b.decode())[:240]}")
    for n in names:
        if '/structure' in n and n.endswith('.json'): p(f"structure file {n}: {re.sub(r'\\s+', ' ', z.read(n).decode())[:300]}")
except Exception: p(traceback.format_exc())
try:
    z = jar('myths-and-legends'); names = z.namelist()
    for n in names:
        if ('structure_set' in n or 'worldgen/structure/' in n) and n.endswith('.json') and re.search(r'chronicler|old_camp|mausoleum|vestiges|scorched', n):
            p(f"{n}: {re.sub(r'\\s+', ' ', z.read(n).decode())[:400]}")
    for n in names:
        if 'has_structure' in n and n.endswith('.json'): p(f"{n}: {re.sub(r'\\s+', ' ', z.read(n).decode())[:300]}")
    r = [n for n in names if 'data/mythsandlegends/research/' in n and n.endswith('.json')]
    for n in r: p(f"research {n.split('/')[-1]}: {re.sub(r'\\s+', ' ', z.read(n).decode())[:700]}")
except Exception: p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/facts3.txt', 'w').write('\n'.join(out))
open('report.txt', 'w').write('done')
