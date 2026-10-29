"""Throwaway: Bosses'Rise's jar out of the server zip, and vanilla 1.20.1's overworld worldgen JSON (from Mojang's
server jar). Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import sys
import urllib.request
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if name.endswith(".jar") and "bossesrise" in name.lower().replace("_", "").replace("-", "") or ("block_factory" in name.lower() and name.endswith(".jar")):
        open(os.path.join(out, "bossesrise.jar.bin"), "wb").write(server.read(name))
        print("Bosses'Rise:", name)
manifest = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
url = next(v["url"] for v in manifest["versions"] if v["id"] == "1.20.1")
meta = json.load(urllib.request.urlopen(url))
bundler = zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(meta["downloads"]["server"]["url"]).read()))
inner = zipfile.ZipFile(io.BytesIO(bundler.read("META-INF/versions/1.20.1/server-1.20.1.jar")))
wanted = [n for n in inner.namelist() if n.startswith("data/minecraft/worldgen/") and (
    "density_function/overworld/" in n or n.endswith("noise_settings/overworld.json") or "/noise/erosion" in n or "/noise/continentalness" in n)]
with zipfile.ZipFile(os.path.join(out, "vanilla_worldgen.zip"), "w") as z:
    for n in wanted:
        z.writestr(n, inner.read(n))
print(len(wanted), "vanilla worldgen files")
