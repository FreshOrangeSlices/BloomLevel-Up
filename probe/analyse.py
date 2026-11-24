import os, re, json, zipfile, urllib.request, traceback, io
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
out = []; p = out.append
try:
    z = zipfile.ZipFile(io.BytesIO(get("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip")))
    tsrg = z.read('config/joined.tsrg').decode()
    # obf -> srg for DamageTypes fields
    manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    v = next(x for x in manifest['versions'] if x['id'] == '1.20.1')
    meta = json.loads(get(v['url']))
    maps = get(meta['downloads']['client_mappings']['url']).decode()
    obf_cls, named_by_obf, in_cls = None, {}, False
    for line in maps.splitlines():
        if not line.startswith(' '):
            in_cls = line.startswith('net.minecraft.world.damagesource.DamageTypes ->')
            if in_cls: obf_cls = line.split(' -> ')[1].rstrip(':')
        elif in_cls and '->' in line and '(' not in line:
            named, obf = line.strip().split(' -> ')
            named_by_obf[obf] = named.split()[-1]
    p(f"DamageTypes is {obf_cls}")
    in_dt = False
    for line in tsrg.splitlines():
        if not line.startswith('\t'):
            in_dt = line.split()[0] == obf_cls
        elif in_dt and not line.startswith('\t\t'):
            parts = line.split()
            if parts[1] in ('f_268515_', 'f_268530_'):
                p(f"{parts[1]} = {named_by_obf.get(parts[0])}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/damagetypes.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
