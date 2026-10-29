"""Throwaway: Illager Invasion's and Puzzles Lib's jars out of the server zip. Usage: companions.py <server.zip> <out dir>"""
import os
import sys
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    low = name.lower()
    if name.endswith(".jar") and ("illagerinvasion" in low or "puzzleslib" in low):
        target = os.path.join(out, os.path.basename(name) + ".bin")
        open(target, "wb").write(server.read(name))
        print("jar:", name)
