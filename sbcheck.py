"""Throwaway: Sophisticated Backpacks' key bindings (names and defaults) and its config keys, from its Forge 1.20.1 jar
on Modrinth. Usage: sbcheck.py <out dir>"""
import io, json, os, re, subprocess, sys, tempfile, urllib.request, zipfile
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
lines = []
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "warionex-relay"})).read()
for slug in ("sophisticated-backpacks", "sophisticated-core"):
    versions = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D"))
    v = versions[0]
    f = next(x for x in v["files"] if x["primary"])
    lines.append(f"{slug} {v['version_number']} {f['filename']} deps={[(d['project_id'], d['dependency_type']) for d in v['dependencies']]}")
    jar = zipfile.ZipFile(io.BytesIO(get(f["url"])))
    with tempfile.TemporaryDirectory() as tmp:
        for n in jar.namelist():
            if n.endswith(".class") and (b"keybind." in jar.read(n) or "Keybind" in n or "KeyMapping" in n):
                data = jar.read(n)
                if b"keybind." not in data:
                    continue
                path = os.path.join(tmp, n)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
                text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
                for m in re.finditer(r'ldc\s+#\d+\s+// String (keybind\.[\w.]+)(.{0,600})', text, re.S):
                    tail = m.group(2)
                    consts = re.findall(r'(?:bipush|sipush|iconst_\w+|ldc)\s+(?:#\d+\s+// (?:int|String) )?(-?\w+)', tail)[:6]
                    fields = re.findall(r'Field [\w/$]+\.(\w+):L[\w/$]*(?:KeyModifier|KeyConflictContext|IKeyConflictContext)', tail)[:3]
                    lines.append(f"  {n}: {m.group(1)} consts={consts} {fields}")
        for n in jar.namelist():
            if re.search(r"(Config|config)[^/]*\.class$", n) and "client" not in n.lower():
                pass
open(os.path.join(out, "summary.txt"), "w").write("\n".join(lines))
print("\n".join(lines)[:3000])

# Carry On's generated config, wherever the artifacts keep it (the server's config/, unzipped or in a zip)
found = []
for base in ("shots", "pack"):
    for folder, _, names in os.walk(base):
        for name in names:
            full = os.path.join(folder, name)
            if "carryon" in name.lower() and not name.endswith(".jar"):
                found.append(f"== {full}\n" + open(full, encoding="utf-8", errors="replace").read()[:6000])
            elif name.endswith(".zip"):
                try:
                    z = zipfile.ZipFile(full)
                except zipfile.BadZipFile:
                    continue
                for n in z.namelist():
                    if "carryon" in n.lower() and not n.endswith(".jar") and not n.endswith("/"):
                        found.append(f"== {full}!{n}\n" + z.read(n).decode("utf-8", "replace")[:6000])
open(os.path.join(out, "carryon.txt"), "w").write("\n".join(found) if found else "no carryon config in the artifacts")
print(len(found), "carryon files")
