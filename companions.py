"""Throwaway: Jade's and Inventory Profiles Next's lang lines and option/key names (from Modrinth), for turning off
IPN's continuous crafting and break warnings and Jade's overlay. Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
jars = {
    "jade": "https://cdn.modrinth.com/data/nvQzSEkH/versions/xJQHCmWJ/Jade-1.20.1-Forge-11.13.3.jar",
    "ipn": "https://cdn.modrinth.com/data/O7RBXm3n/versions/CrtAI3P9/InventoryProfilesNext-forge-1.20-1.10.20.jar",
}
lines = []
for name, url in jars.items():
    jar = zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(url).read()))
    for entry in jar.namelist():
        if entry.endswith("en_us.json"):
            data = json.loads(jar.read(entry).decode("utf-8", "replace"))
            for key, value in data.items():
                if re.search(r"(?i)key\.|keybind|overlay|display|craft|refill|break|damage|durab|notif|warn|alert", key + value):
                    lines.append(f"{name} LANG {key} = {value[:200]}")
        elif entry.endswith(".class") and ("Key" in entry or "Config" in entry or "Setting" in entry or "ModSettings" in entry):
            text = jar.read(entry).decode("latin-1")
            for word in sorted(set(re.findall(r"[A-Za-z0-9_.]{4,60}", text))):
                if re.search(r"(?i)^key\.jade|continuous|auto_?craft|refill|damage|break|notif|warning|keypad|kp_|GLFW", word):
                    lines.append(f"{name} CLASS {entry} {word}")
open(os.path.join(out, "qol.txt"), "w").write("\n".join(lines))
print(len(lines), "lines")
