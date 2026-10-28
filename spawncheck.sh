#!/bin/bash
# Throwaway: does Myths & Legends' Cogwork Construct (crashes the first client that sees it) spawn by itself, and where
# do Legionary's Unbreakable (its attacks crash a dedicated server) and the rest come from?
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re, gzip
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
v = get("https://api.modrinth.com/v2/version/J9qH2IC8")
z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], raw=True)))
print("== mythsandlegends structures mentioning cogwork (decompressed nbt)")
for n in sorted(z.namelist()):
    if n.endswith(".nbt"):
        try:
            b = gzip.decompress(z.read(n))
        except Exception:
            b = z.read(n)
        names = sorted(set(m.decode() for m in re.findall(rb"mythsandlegends:[a-z_]+", b)))
        if names:
            print(" ", n, names)
for n in sorted(z.namelist()):
    if "structure_set/ancient_vestiges" in n or "worldgen/structure/ancient_vestiges" in n:
        print(" ", n, z.read(n).decode()[:600].replace("\n", " "))
v = get("https://api.modrinth.com/v2/version/zCtV7TZo")
z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], raw=True)))
b = z.read("data/legionary/fieldguide/biome_modifiers/legionary_biome_additions.json").decode()
i = b.find("unbreakable")
print("== legionary unbreakable spawn:", b[max(0, i - 700):i + 400].replace("\n", " "))
PY
cat out.txt
echo "::notice title=spawns::$(head -c 3800 out.txt | tr '\n' '|' | sed 's/%/%25/g')"
