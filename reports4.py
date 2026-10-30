"""Throwaway: Fauna Engine (CurseForge), the library Book of Dragons 1.4 needs: is it modId liberanimalium, for Forge
1.20.1, and what does it carry. Usage: reports4.py <server.zip> <out dir>"""
import io, json, os, re, sys, urllib.parse, urllib.request, zipfile
OUT = sys.argv[2]; os.makedirs(OUT, exist_ok=True); lines = []
def say(*p):
    s = " ".join(str(x) for x in p); lines.append(s); print(s)
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"}), timeout=120) as r:
        d = r.read()
    return d if raw else json.loads(d)
cf = get("https://api.cfwidget.com/minecraft/mc-mods/fauna-engine")
say("fauna engine:", cf.get("title"), "|", cf.get("summary"), "| id", cf.get("id"), "| license", cf.get("license"), "| downloads", (cf.get("downloads") or {}).get("total"))
files = sorted(cf.get("files", []), key=lambda f: f.get("uploaded_at", ""), reverse=True)
for f in files[:8]:
    say("  file", f["id"], f["name"], f.get("uploaded_at", "")[:10], f.get("versions"), f.get("type"))
forge = [f for f in files if "1.20.1" in f.get("versions", []) and "Forge" in f.get("versions", [])]
if forge:
    f = forge[0]
    url = f"https://mediafilez.forgecdn.net/files/{f['id'] // 1000}/{f['id'] % 1000}/{urllib.parse.quote(f['name'])}"
    data = get(url, raw=True)
    say("  downloaded", f["name"], len(data), "bytes from", url)
    z = zipfile.ZipFile(io.BytesIO(data))
    for n in z.namelist():
        if n.endswith("mods.toml"):
            t = z.read(n).decode("utf-8", "replace")
            open(os.path.join(OUT, "fauna-mods.toml"), "w").write(t)
            say("  modIds:", re.findall(r'modId\s*=\s*"([^"]+)"', t), "| versions:", re.findall(r'^\s*version\s*=\s*"([^"]+)"', t, re.M),
                "| ranges:", re.findall(r'versionRange\s*=\s*"([^"]+)"', t), "| license:", re.findall(r'^license\s*=\s*"([^"]+)"', t, re.M))
    say("  classes:", len([n for n in z.namelist() if n.endswith(".class")]), "| jarjar:", [n for n in z.namelist() if n.startswith("META-INF/jarjar/")],
        "| mixins:", [n for n in z.namelist() if n.endswith(".json") and "mixins" in n and "/" not in n], "| data dirs:", sorted({"/".join(n.split("/")[:3]) for n in z.namelist() if n.startswith("data/")})[:20])
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
