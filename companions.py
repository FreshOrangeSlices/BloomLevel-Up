"""Throwaway: what the pack's boss-finding items are made from and point at. From every jar in the server zip: the
recipes that make them, their en_us lang lines, their loot table mentions, and their item classes (for javap).
Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import zipfile

out = sys.argv[2]
os.makedirs(os.path.join(out, "classes"), exist_ok=True)
WANT = re.compile(r"(cataclysm:\w+_eye|legendary_monsters:eye_of_\w+|soulsweapons:boss_compass|irons_spellbooks:(furled_map\w*|wayward_compass)"
                  r"|aquamirae:echo_compass|monsterexpansion:kobeko_locator|bookofdragons:\w+_map|oddities:orchid_mantis_eye"
                  r"|block_factorys_bosses:\w*key|cataclysm:strange_key|aquamirae:frozen_key|irons_spellbooks:(decrepit|bone)_key)\b")
CLASSES = re.compile(r"(DungeonEyeItem|EyeOf\w+|BossCompass|WaywardCompass|EchoCompassItem|FurledMap\w*|KobekoLocatorItem|OrchidMantisEyeItem"
                     r"|DragonRoostMapItem|UnderworldArenaKeyItem|Strange_Key)(\$\w+)?\.class$")
recipes, lang, loot = {}, {}, {}
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if not name.endswith(".jar"):
        continue
    try:
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    except zipfile.BadZipFile:
        continue
    for entry in jar.namelist():
        if entry.endswith(".class") and CLASSES.search(entry):
            target = os.path.join(out, "classes", entry.replace("/", "."))
            open(target, "wb").write(jar.read(entry))
        elif entry.endswith(".json") and "/recipes/" in entry:
            text = jar.read(entry).decode("utf-8", "replace")
            try:
                data = json.loads(re.sub(r"(?m)^\s*//.*$", "", text))
            except ValueError:
                continue
            result = json.dumps(data.get("result", data.get("output", "")))
            if WANT.search(result):
                recipes[entry] = data
        elif entry.endswith("lang/en_us.json"):
            try:
                data = json.loads(jar.read(entry).decode("utf-8", "replace"))
            except ValueError:
                continue
            for key, value in data.items():
                if re.search(r"(_eye|eye_of|compass|furled_map|locator|roost_map|mantis_eye|_key)", key):
                    lang[key] = value
        elif entry.endswith(".json") and "/loot_tables/" in entry:
            text = jar.read(entry).decode("utf-8", "replace")
            hits = sorted(set(m.group(0) for m in WANT.finditer(text)))
            if hits:
                loot[entry] = hits
json.dump({"recipes": recipes, "lang": lang, "loot": loot}, open(os.path.join(out, "finders.json"), "w"), indent=1)
print(len(recipes), "recipes,", len(lang), "lang lines,", len(loot), "loot tables,", len(os.listdir(os.path.join(out, "classes"))), "classes")
