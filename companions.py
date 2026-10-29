"""Throwaway: copy jars out of the server zip (to read their code with javap).
Usage: companions.py <server.zip> <out dir>"""
import os
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
os.makedirs(sys.argv[2], exist_ok=True)
for name in server.namelist():
    low = name.lower()
    for key, out in (("bookofdragons", "bookofdragons.jar.bin"),):
        if name.endswith(".jar") and key in low:
            data = server.read(name)
            open(os.path.join(sys.argv[2], out), "wb").write(data)
            print(name, len(data))
