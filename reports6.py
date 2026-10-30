"""Throwaway: what the pack test's client wrote for chunk 1's settings. Usage: reports6.py <server.zip> <shots dir> <out dir>"""
import os, shutil, sys
SHOTS, OUT = sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
lines = []
game = os.path.join(SHOTS, "game")
for rel in ("config/inventoryprofilesnext", "config/craftingtweaks-common.toml", "config/craftingtweaks-client.toml",
            "config/block_factorys_bosses-client.toml", "config/combatroll", "config/xaero/minimap", "config/bloomlevelup",
            "config/monsterexpansion-rolls.toml", "options.txt"):
    src = os.path.join(game, rel)
    if os.path.isdir(src):
        shutil.copytree(src, os.path.join(OUT, rel), dirs_exist_ok=True)
        lines.append(f"dir {rel}: {sorted(os.listdir(src))}")
    elif os.path.exists(src):
        os.makedirs(os.path.dirname(os.path.join(OUT, rel)), exist_ok=True)
        shutil.copy(src, os.path.join(OUT, rel))
        lines.append(f"file {rel}: {os.path.getsize(src)} bytes")
    else:
        lines.append(f"missing {rel}")
cfg = os.path.join(game, "config")
lines.append("config folder: " + " ".join(sorted(os.listdir(cfg))) if os.path.isdir(cfg) else "no config folder")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
print("\n".join(lines))
