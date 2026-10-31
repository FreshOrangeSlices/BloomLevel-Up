"""Throwaway: everything a spawn map page needs, from a pack run: every biome's natural spawns and every structure's own,
each entity's dump row, English names (mods' and vanilla's lang files) and Minecraft mob type (from the classes that
override getMobType). Usage: spawnmap.py <server.zip> <shots dir> <out dir>"""
import glob, io, json, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)))
rows = [json.loads(l) for l in open(dump) if l.strip()]
data = {"biomes": [], "structures": [], "entities": {}, "names": {}, "mobtype": {}, "biome_names": {}}
for r in rows:
    k = r.get("kind")
    if k == "biome":
        data["biomes"].append({"id": r["id"], "tags": r.get("tags", []), "spawns": r.get("spawns", {})})
    elif k == "structure" and r.get("spawns"):
        data["structures"].append({"id": r["id"], "spawns": r["spawns"]})
    elif k == "entity" and r.get("category") != "misc":
        e = {x: r.get(x) for x in ("category", "classes", "health", "attack", "armor", "bossBar", "spawnRules", "spawnsInDaylight", "despawns")}
        data["entities"][r["id"]] = e


def jars():
    for n in server.namelist():
        if n.endswith(".jar"):
            try:
                yield n, zipfile.ZipFile(io.BytesIO(server.read(n)))
            except zipfile.BadZipFile:
                pass


TYPES = {"f_21640_": "undefined", "f_21641_": "undead", "f_21642_": "arthropod", "f_21643_": "illager", "f_21644_": "water"}
with tempfile.TemporaryDirectory() as tmp:
    for name, jar in jars():
        for n in jar.namelist():
            if re.search(r"assets/[^/]+/lang/en_us\.json$", n):
                try:
                    lang = json.loads(jar.read(n).decode("utf-8", "replace"))
                except ValueError:
                    continue
                for key, value in lang.items():
                    if key.startswith("entity.") and key.count(".") == 2:
                        data["names"].setdefault(key.split(".", 1)[1].replace(".", ":", 1), re.sub(r"§.", "", value))
                    if key.startswith("biome.") and key.count(".") == 2:
                        data["biome_names"].setdefault(key.split(".", 1)[1].replace(".", ":", 1), re.sub(r"§.", "", value))
            elif n.endswith(".class") and "net/minecraft/" not in n:
                b = jar.read(n)
                if b"m_6336_" in b and b"MobType" in b:
                    path = os.path.join(tmp, "c.class")
                    open(path, "wb").write(b)
                    text = subprocess.run(["javap", "-c", "-p", path], capture_output=True, text=True).stdout
                    m = re.search(r"MobType m_6336_\(\);\s*\n\s*Code:\s*\n(.*?)\n\s*\n", text, re.S)
                    if m:
                        f = re.search(r"MobType\.(f_2164\d_)", m.group(1))
                        if f:
                            data["mobtype"][n.rsplit("/", 1)[1][:-6]] = TYPES.get(f.group(1), f.group(1))
json.dump(data, open(os.path.join(out, "spawnmap.json"), "w"))
open(os.path.join(out, "summary.txt"), "w").write(f"{len(data['biomes'])} biomes, {len(data['structures'])} structures, {len(data['entities'])} entities, "
                                                    f"{len(data['names'])} names, {len(data['mobtype'])} mob type classes\n")
print(open(os.path.join(out, "summary.txt")).read())
