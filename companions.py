"""Throwaway: copy Bosses Oddities' jar out of the server zip (to read its code with javap).
Usage: companions.py <server.zip> <out dir>"""
import os
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
os.makedirs(sys.argv[2], exist_ok=True)
for name in server.namelist():
    if name.endswith(".jar") and "oddities" in name.lower():
        open(os.path.join(sys.argv[2], "oddities.jar.bin"), "wb").write(server.read(name))
        print(name, len(server.read(name)))
