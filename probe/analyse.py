import os, re, json, zipfile, urllib.request, traceback, io
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
out = []; p = out.append
try:
    z = zipfile.ZipFile(io.BytesIO(get("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip")))
    tsrg = z.read('config/joined.tsrg').decode()
    # obf -> srg for DamageTypes fields
    cls = None; srg_by_obf = {}; in_dt = False
    p("tsrg head: " + repr(tsrg[:200]))
    for line in tsrg.splitlines():
        line = line.rstrip("\r")
        if not line.startswith('\t'):
            parts = line.split()
            in_dt = 'net/minecraft/world/damagesource/DamageTypes' in parts
            if in_dt: cls = parts[0]
        elif in_dt and not line.startswith('\t\t'):
            parts = line.split()
            for want in ('f_268515_', 'f_268530_'):
                if want in parts:
                    srg_by_obf[parts[0]] = want
    p(f"DamageTypes obf class {cls}; {srg_by_obf}")
    manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    v = next(x for x in manifest['versions'] if x['id'] == '1.20.1')
    meta = json.loads(get(v['url']))
    maps = get(meta['downloads']['client_mappings']['url']).decode()
    in_cls = False
    for line in maps.splitlines():
        if not line.startswith(' '):
            in_cls = line.startswith('net.minecraft.world.damagesource.DamageTypes ->')
        elif in_cls and '->' in line:
            named, obf = line.strip().split(' -> ')
            if obf in srg_by_obf:
                p(f"{srg_by_obf[obf]} = {named}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/damagetypes.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
