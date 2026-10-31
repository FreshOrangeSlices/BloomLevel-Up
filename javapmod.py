"""Throwaway: javap -c of a mod jar's classes whose path matches a pattern. Usage: javapmod.py <server.zip> <out dir> <jar substring> <regex>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, out, jar_sub, pattern = sys.argv[1:5]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
for name in server.namelist():
    if name.startswith("mods/") and jar_sub in name:
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        with tempfile.TemporaryDirectory() as tmp:
            for n in jar.namelist():
                if n.endswith(".class") and re.search(pattern, n):
                    path = os.path.join(tmp, n)
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    open(path, "wb").write(jar.read(n))
                    text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
                    report.append(f"## {n}\n{text[:30000]}")
open(os.path.join(out, "javap.txt"), "w").write("\n".join(report))
print(len(report), "classes")
