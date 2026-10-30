"""Throwaway: Book of Dragons 1.4's libraries (its project's dependency list), and whether one of them is modId
liberanimalium with a Forge 1.20.1 build. Usage: reports3.py <server.zip> <out dir>"""
import io, json, os, re, sys, urllib.parse, urllib.request, zipfile
OUT = sys.argv[2]; os.makedirs(OUT, exist_ok=True); lines = []
def say(*p):
    s = " ".join(str(x) for x in p); lines.append(s); print(s)
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"}), timeout=120) as r:
        d = r.read()
    return d if raw else json.loads(d)
deps = get("https://api.modrinth.com/v2/project/book-of-dragons/dependencies")
for p in deps.get("projects", []):
    say("dependency project:", p["id"], p["slug"], "|", p["title"], "|", p.get("description"), "| license", (p.get("license") or {}).get("id"), "| downloads", p.get("downloads"))
for q in ("fauna engine", "fauna-engine"):
    for h in get(f"https://api.modrinth.com/v2/search?query={urllib.parse.quote(q)}&limit=6")["hits"]:
        say("search", repr(q), h["project_id"], h["slug"], "|", h["title"], "|", h["author"], h["downloads"], h["versions"][-4:])
cands = [p["id"] for p in deps.get("projects", []) if "azure" not in p["slug"].lower()]
for pid in cands:
    vs = get(f"https://api.modrinth.com/v2/project/{pid}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    say("forge 1.20.1 versions of", pid, [(v["version_number"], v["date_published"][:10], v["files"][0]["filename"], v["files"][0]["size"]) for v in vs[:6]])
    if vs:
        z = zipfile.ZipFile(io.BytesIO(get(vs[0]["files"][0]["url"], raw=True)))
        for n in z.namelist():
            if n.endswith("mods.toml"):
                t = z.read(n).decode("utf-8", "replace")
                say("  mods.toml modIds:", re.findall(r'modId\s*=\s*"([^"]+)"', t), "| version", re.findall(r'^version\s*=\s*"([^"]+)"', t, re.M))
                open(os.path.join(OUT, pid + "-mods.toml"), "w").write(t)
        say("  jarjar:", [n for n in z.namelist() if n.startswith("META-INF/jarjar/")], "| mixins:", [n for n in z.namelist() if n.endswith(".json") and "mixins" in n and "/" not in n])
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines))
