"""Throwaway: what LegionWareX's mods do to world generation. Reads every jar the pack builder downloaded (game/mods):
biomes they add, TerraBlender regions (biomes woven into the overworld), Forge biome modifiers that add mob spawns
(and which biomes), and spawn-related config files they ship. Prints GitHub annotations."""
import io, json, os, re, sys, zipfile
mods = sys.argv[1]
lines = []
for name in sorted(os.listdir(mods)):
    if not name.endswith(".jar"):
        continue
    try:
        jar = zipfile.ZipFile(os.path.join(mods, name))
    except zipfile.BadZipFile:
        continue
    names = jar.namelist()
    biomes = [n for n in names if re.match(r"data/[^/]+/worldgen/biome/.+\.json$", n)]
    terrablender = [n for n in names if n.endswith(".class") and b"terrablender/api/Region" in jar.read(n)] if any(n.endswith(".class") for n in names) else []
    modifiers = [n for n in names if re.match(r"data/[^/]+/forge/biome_modifier/.+\.json$", n)]
    noise = [n for n in names if re.match(r"data/minecraft/worldgen/(noise_settings|multi_noise_biome_source_parameter_list|world_preset)/.+\.json$", n)]
    parts = []
    if biomes:
        parts.append(f"adds {len(biomes)} biomes: " + ", ".join(sorted({b.split('/')[1] + ':' + b.rsplit('/', 1)[1][:-5] for b in biomes}))[:500])
    if terrablender:
        parts.append(f"TerraBlender regions in {len(terrablender)} classes: " + ", ".join(t.rsplit('/', 1)[1] for t in terrablender[:4]))
    if noise:
        parts.append("overrides vanilla world gen: " + ", ".join(noise[:5]))
    spawns = []
    for m in modifiers:
        try:
            data = json.loads(jar.read(m))
        except ValueError:
            continue
        if "spawn" in str(data.get("type", "")):
            ents = data.get("spawners", [])
            ents = ents if isinstance(ents, list) else [ents]
            what = ", ".join(f"{e.get('type','?').split(':')[-1]} w{e.get('weight')} {e.get('minCount')}-{e.get('maxCount')}" for e in ents)
            spawns.append(f"{m.rsplit('/',1)[1][:-5]} -> biomes {data.get('biomes')} : {what}")
    if spawns:
        parts.append(f"{len(spawns)} spawn modifiers: " + " || ".join(spawns)[:1500])
    if parts:
        lines.append(f"== {name}: " + " | ".join(parts))
text = "\n".join(lines) or "no mod touches world generation"
print(text)
esc = lambda t: t.replace("%", "%25").replace("\n", "%0A")
for n, i in enumerate(range(0, len(text), 3800)):
    print(f"::notice title=biomes-{n:02d}::{esc(text[i:i+3800])}")
