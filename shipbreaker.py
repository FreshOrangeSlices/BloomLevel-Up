"""Throwaway: what Aquamirae's ShipbreakerSpawner spawns, where and how often (javap). Usage: shipbreaker.py <zip> <out>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
jar = next(zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.endswith(".jar") and "aquamirae" in n.lower())
out = []
with tempfile.TemporaryDirectory() as tmp:
    for entry in sorted(jar.namelist()):
        if entry.endswith(".class") and (re.search(r"Shipbreaker|Eel", entry) or b"ShipbreakerSpawner" in jar.read(entry)):
            path = os.path.join(tmp, entry)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "wb").write(jar.read(entry))
            text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
            out.append(f"## javap {entry}\n{text[:40000]}")
    for entry in sorted(jar.namelist()):
        if re.search(r"shipbreaker", entry, re.I) and not entry.endswith((".class", ".png", ".ogg")):
            out.append(f"## file {entry}\n{jar.read(entry).decode('utf-8', 'replace')[:2000]}")
    for entry in sorted(jar.namelist()):
        if entry.startswith("assets/aquamirae/lang/en_us.json"):
            text = jar.read(entry).decode("utf-8", "replace")
            out.append("## lang lines\n" + "\n".join(l for l in text.splitlines() if re.search(r"shipbreaker|eel|ship", l, re.I))[:4000])
open(sys.argv[2], "w").write("\n".join(out))
print(len(out), "sections")
