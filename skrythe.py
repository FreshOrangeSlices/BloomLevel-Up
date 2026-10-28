"""Throwaway: every way Monster Expansion can put Skrythe in the world. Disassembles the classes that use its spawn
tag, a spawn search, or the lair's spawner, and describes the lair's template (size, blocks, markers).
Usage: skrythe.py <server.zip> <out file>"""
import gzip, io, os, re, struct, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
jar = None
for name in server.namelist():
    if name.endswith(".jar") and "monsterexpansion" in name.lower():
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
out = []
pick = re.compile(rb"ModBiomeTagProvider|SKRYTHE_SPAWN|skrythe_spawn|SPAWN_SEARCH|MIN_SPAWN_DISTANCE|EntitySpawnProcessor|SkrytheSpawn|ServerTickEvent|LevelTickEvent|PlayerTickEvent|ChunkEvent|EntityJoinLevelEvent|NaturalSpawner|MobSpawnType")
with tempfile.TemporaryDirectory() as tmp:
    for entry in sorted(jar.namelist()):
        if not entry.endswith(".class") or "/datagen/" in entry or "/entity/ai/" in entry or "/client/" in entry:
            continue
        data = jar.read(entry)
        hits = sorted({m.decode() for m in pick.findall(data)})
        if not hits:
            continue
        path = os.path.join(tmp, entry)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(data)
        text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
        out.append(f"## {entry} {hits}\n{text[:30000]}")
    for entry in sorted(jar.namelist()):
        if "skrythe" in entry and (entry.endswith(".json") and "/worldgen/" in entry or entry.endswith(".nbt")):
            raw = jar.read(entry)
            if entry.endswith(".nbt"):
                try:
                    raw = gzip.decompress(raw)
                except OSError:
                    pass
                names = sorted(set(m.decode() for m in re.findall(rb"[a-z_]+:[a-z_/]+", raw)))
                out.append(f"## nbt {entry} ({len(raw)} bytes) names: {names[:80]}")
            else:
                out.append(f"## json {entry}\n{raw.decode('utf-8', 'replace')[:3000]}")
open(sys.argv[2], "w").write("\n".join(out))
print(len(out), "sections")
