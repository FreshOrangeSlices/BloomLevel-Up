"""Throwaway: the pack batch as shipped: Paladins' spell costs in the pack's data pack, Carry On's config, Bloom Pets'
jar, the backpacks. Usage: batchcheck.py <server.zip> <out dir>"""
import json, os, re, sys, zipfile
server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
z = zipfile.ZipFile(server_zip)
lines = []
spells = [n for n in z.namelist() if "openloader/data" in n and "/paladins/spell/" in n and n.endswith(".json")]
for n in sorted(spells):
    cost = json.loads(z.read(n)).get("cost")
    lines.append(f"{n.rsplit('/', 1)[-1]}: {json.dumps(cost)}")
lines.append(f"{len(spells)} paladins spells in the pack's data pack")
for n in z.namelist():
    if "carryon" in n and n.endswith(".toml"):
        lines.append(f"{n}: " + z.read(n).decode()[:400].replace("\n", " | "))
lines += [n for n in z.namelist() if re.search(r"mods/(bloompets|sophisticated|legionwarex)", n)]
open(os.path.join(out, "summary.txt"), "w").write("\n".join(lines))
print("\n".join(lines)[:3000])
