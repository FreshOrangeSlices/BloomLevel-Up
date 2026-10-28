#!/bin/bash
# Throwaway: KeySet (Forge 1.20.1): its own key bindings and defaults, and whether it handles Forge key modifiers.
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re, subprocess, os
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
p = get("https://api.modrinth.com/v2/project/keyset")
print("project", p["id"], p["title"], "downloads", p["downloads"], "client", p["client_side"], "server", p["server_side"], "updated", p["updated"][:10])
vs = get(f"https://api.modrinth.com/v2/project/{p['id']}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
for v in vs[:3]:
    print("version", v["version_number"], v["date_published"][:10], [f["filename"] for f in v["files"]], [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]])
v = vs[0]
data = get(next(f for f in v["files"] if f.get("primary") or True)["url"], raw=True)
open("ks.jar", "wb").write(data)
z = zipfile.ZipFile(io.BytesIO(data))
for n in z.namelist():
    if n.endswith("en_us.json"):
        l = json.loads(z.read(n))
        print("lang key.* :", {k: v for k, v in l.items() if k.startswith("key")})
mods = [n for n in z.namelist() if n.endswith("mods.toml")]
for n in mods: print(z.read(n).decode()[:800])
hits = {}
for n in z.namelist():
    if n.endswith(".class"):
        b = z.read(n)
        for w in [b"KeyModifier", b"setKeyModifierAndCode", b"getKeyModifier", b"m_90848_", b"setKey", b"options.txt", b"keyset", b"Controlling", b"KeyMappingLookup"]:
            if w in b: hits.setdefault(w.decode(), []).append(n.rsplit("/", 1)[-1])
print("class refs:", {k: v[:6] for k, v in hits.items()})
os.makedirs("x", exist_ok=True); z.extractall("x")
for n in z.namelist():
    if n.endswith(".class") and (b"KeyMapping" in z.read(n)) and b'"<init>"' not in z.read(n):
        pass
out = []
for root, _, files in os.walk("x"):
    for f in files:
        if f.endswith(".class"):
            path = os.path.join(root, f)
            if b"net/minecraft/client/KeyMapping" in open(path, "rb").read() and b"<init>" in open(path, "rb").read():
                cls = os.path.relpath(path, "x")[:-6].replace("/", ".")
                t = subprocess.run(["javap", "-c", "-p", "-constants", "-cp", "x", cls], capture_output=True, text=True).stdout
                for m in re.finditer(r"(ldc\s+#\d+\s+// String key\.[^\n]+|bipush\s+-?\d+|sipush\s+-?\d+|KeyMapping\.\"<init>\":[^\n]+)", t):
                    out.append(f"{cls.rsplit('.',1)[-1]}: {m.group(0).strip()[:120]}")
print("\n".join(out[:40]))
PY
cat out.txt
echo "::notice title=keyset::$(head -c 3900 out.txt | tr '\n' '|' | sed 's/%/%25/g')"
