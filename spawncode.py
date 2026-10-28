"""Throwaway: disassembles the classes behind the pack's code-driven spawns (javap), so their defaults and rules can be
read: Monster Expansion's Skrythe, Mutant Monsters' spawn weights, Skarrier Mobs' spawn rules, Cataclysm's and The
Graveyard's config spawns, Easy Mob Spawn Control. Usage: spawncode.py <server.zip> <out dir>"""
import io
import os
import re
import subprocess
import sys
import tempfile
import zipfile

TARGETS = {
    "monsterexpansion": [rb"skrythe", rb"SpawnPlacementRegisterEvent", rb"EntitySpawnProcessor"],
    "MutantMonsters": [rb"SpawnWeight", rb"spawn_weight", rb"addMutantSpawn"],
    "skarrier_mobs": [rb"SpawnPlacementRegisterEvent", rb"checkSkarrier", rb"SpawnPlacements"],
    "Cataclysm": [rb"cataclysm_mob_spawns", rb"SpawnWeight", rb"spawn_weight", rb"Spawn_Weight"],
    "graveyard": [rb"config_spawns", rb"spawnWeight", rb"weight"],
    "easy_mob_spawn_control": [rb"custom_spawn_modifier", rb"\.toml", rb"\.json"],
    "legionary": [rb"fieldguide", rb"biome_modifiers"],
    "Bosses": [rb"SpawnPlacementRegisterEvent", rb"add_spawns"],
    "block_factorys_bosses": [rb"SpawnPlacementRegisterEvent", rb"spawn"],
    "irons_spellbooks": [rb"necromancer", rb"SpawnPlacementRegisterEvent"],
}

server = zipfile.ZipFile(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)

for name in sorted(server.namelist()):
    if not name.endswith(".jar") or "/mods/" not in "/" + name:
        continue
    jar_name = name.rsplit("/", 1)[-1]
    key = next((k for k in TARGETS if k.lower() in jar_name.lower()), None)
    if key is None:
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    report = [f"# {jar_name}"]
    # Files that aren't classes but say how spawns work (default configs, data under fieldguide, etc.).
    for entry in jar.namelist():
        if not entry.endswith(".class") and re.search(r"spawn|fieldguide|config", entry, re.I) and not entry.endswith((".png", ".ogg", ".nbt")):
            data = jar.read(entry)
            report.append(f"## file {entry} ({len(data)} bytes)\n" + data.decode("utf-8", "replace")[:3000])
    with tempfile.TemporaryDirectory() as tmp:
        picked = []
        for entry in jar.namelist():
            if entry.endswith(".class"):
                data = jar.read(entry)
                if any(re.search(p, data) for p in TARGETS[key]) and len(picked) < 40:
                    path = os.path.join(tmp, entry)
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    open(path, "wb").write(data)
                    picked.append(entry)
        report.append("## classes: " + ", ".join(picked))
        for entry in picked:
            try:
                text = subprocess.run(["javap", "-c", "-p", "-constants", os.path.join(tmp, entry)], capture_output=True,
                                      text=True, timeout=60).stdout
            except Exception as e:
                text = f"javap failed: {e}"
            report.append(f"## javap {entry}\n{text[:60000]}")
    open(os.path.join(out, re.sub(r"[^A-Za-z0-9_.-]", "_", jar_name) + ".txt"), "w").write("\n".join(report))
    print(jar_name, "->", len(report), "sections")
