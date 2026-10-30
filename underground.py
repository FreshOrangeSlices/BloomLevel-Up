"""Throwaway: does anything in the pack change what's underground (stone blobs, ores, caves, the Overworld's noise)?
Every jar's worldgen and biome-modifier files that touch stone variants or the Overworld's noise, every mixin
config that names a worldgen class, and the pack's own data pack files. Usage: underground.py <server.zip> <out file>"""
import io, json, re, sys, zipfile

server = zipfile.ZipFile(sys.argv[1])
STONES = re.compile(r"diorite|granite|andesite|tuff|calcite|deepslate|ore_|stone", re.I)
WORLDGEN_CLASSES = re.compile(r"Ore|Noise|Aquifer|Carver|Cave|Surface|ChunkGenerator|DensityFunction|BiomeGeneration|PlacedFeature|Feature", re.I)
out = {"files": {}, "biome_modifiers": {}, "mixins": {}, "pack_data": [], "jars": []}


def scan_jar(label, jar):
    for entry in jar.namelist():
        low = entry.lower()
        if entry.endswith(".json") and low.startswith("data/"):
            parts = low.split("/")
            # data/<ns>/worldgen/<kind>/... ; data/<ns>/forge/biome_modifier/...
            if "worldgen" in parts:
                kind = parts[parts.index("worldgen") + 1] if len(parts) > parts.index("worldgen") + 1 else ""
                text = jar.read(entry).decode("utf-8", "replace")
                vanilla_override = parts[1] == "minecraft" and kind in ("placed_feature", "configured_feature", "noise_settings",
                                                                          "density_function", "noise", "configured_carver", "biome")
                if vanilla_override or (kind in ("placed_feature", "configured_feature") and re.search(r"diorite|granite|andesite", text)):
                    out["files"][label + "!" + entry] = text[:3000]
            if "biome_modifier" in parts:
                text = jar.read(entry).decode("utf-8", "replace")
                if re.search(r"underground_ores|underground_decoration|diorite|granite|andesite|remove_features|carver", text):
                    out["biome_modifiers"][label + "!" + entry] = text[:3000]
        elif low.endswith(".json") and "mixins" in low and "/" not in entry.strip("/"):
            try:
                config = json.loads(jar.read(entry).decode("utf-8", "replace"))
            except ValueError:
                continue
            names = [n for key in ("mixins", "server", "common") for n in (config.get(key) or [])]
            hits = [n for n in names if WORLDGEN_CLASSES.search(n)]
            if hits:
                out["mixins"][label + "!" + entry] = hits


for name in sorted(server.namelist()):
    if name.endswith(".jar") and "/mods/" in "/" + name:
        out["jars"].append(name)
        data = server.read(name)
        try:
            jar = zipfile.ZipFile(io.BytesIO(data))
        except zipfile.BadZipFile:
            continue
        scan_jar(name.split("/")[-1], jar)
        # Jar-in-jar libraries (META-INF/jarjar) can carry worldgen too.
        for inner in jar.namelist():
            if inner.startswith("META-INF/jarjar/") and inner.endswith(".jar"):
                try:
                    scan_jar(name.split("/")[-1] + ">" + inner.split("/")[-1], zipfile.ZipFile(io.BytesIO(jar.read(inner))))
                except zipfile.BadZipFile:
                    pass
    elif "worldgen" in name or "biome_modifier" in name:
        out["pack_data"].append(name)

json.dump(out, open(sys.argv[2], "w"), indent=1)
print(len(out["jars"]), "jars;", len(out["files"]), "worldgen files;", len(out["biome_modifiers"]), "biome modifiers;",
      len(out["mixins"]), "mixin configs;", len(out["pack_data"]), "pack worldgen files")
for key in ("files", "biome_modifiers", "mixins"):
    for entry in out[key]:
        print(key, entry)
for entry in out["pack_data"]:
    print("pack_data", entry)
