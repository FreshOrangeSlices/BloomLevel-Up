"""Throwaway: which shader loader and shader packs work on LegionWareX (Forge 1.20.1)."""
import io, json, re, sys, urllib.parse, urllib.request, zipfile, os

API = "https://api.modrinth.com/v2"
def get(path, **params):
    q = "&".join(f"{k}={urllib.parse.quote(json.dumps(v) if not isinstance(v, str) else v)}" for k, v in params.items())
    req = urllib.request.Request(f"{API}{path}" + (f"?{q}" if q else ""), headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"})
    return json.load(urllib.request.urlopen(req, timeout=60))
out = []
def say(*a):
    out.append(" ".join(str(x) for x in a))

# ---- the shader loader: Oculus (Iris for Forge) and what it needs
for slug in ["oculus", "embeddium", "iris"]:
    try:
        p = get(f"/project/{slug}")
        vs = get(f"/project/{slug}/version", game_versions=["1.20.1"], loaders=["forge"])
        say(f"== {slug}: {p['title']} ({p['downloads']} downloads, updated {p['updated'][:10]})")
        for v in vs[:4]:
            deps = [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]]
            say(f"   {v['version_number']} [{v['version_type']}] {v['date_published'][:10]} deps={deps}")
        if vs and slug == "oculus":
            f = next((f for f in vs[0]["files"] if f["primary"]), vs[0]["files"][0])
            jar = urllib.request.urlopen(urllib.request.Request(f["url"], headers={"User-Agent": "x"}), timeout=120).read()
            z = zipfile.ZipFile(io.BytesIO(jar))
            toml = z.read("META-INF/mods.toml").decode()
            say("   oculus mods.toml deps:")
            for block in re.findall(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\n\[\[|\Z)", toml, re.S):
                say("     " + " ".join(l.strip() for l in block.strip().splitlines() if l.strip() and not l.strip().startswith("#")))
            for name in z.namelist():
                if name.endswith(".json") and "mixins" in name and "/" not in name:
                    pass
    except Exception as e:
        say(f"== {slug}: error {e}")

# ---- the pack's Embeddium version (from the lock)
lock = json.load(open(sys.argv[1])).get("versions", {})
say("pack lock embeddium:", lock.get("embeddium"), " oculus:", lock.get("oculus"))

# ---- anything in the pack that names oculus in its mods.toml
mods = sys.argv[2]
for name in sorted(os.listdir(mods)):
    try:
        z = zipfile.ZipFile(os.path.join(mods, name))
        t = z.read("META-INF/mods.toml").decode(errors="replace")
        for block in re.findall(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\n\[\[|\Z)", t, re.S):
            if re.search(r'modId\s*=\s*"(oculus|iris)"', block):
                say(f"pack mod {name} mentions oculus/iris: " + " ".join(l.strip() for l in block.strip().splitlines() if l.strip()))
        if any(re.search(r"oculus|iris", n, re.I) for n in z.namelist() if n.endswith(".class") and "compat" in n.lower()):
            say(f"pack mod {name} has oculus/iris compat classes")
    except Exception:
        pass

# ---- shader packs that run on Iris/Oculus for 1.20.1
packs = ["complementary-unbound", "complementary-reimagined", "bsl-shaders", "makeup-ultra-fast-shaders", "photon-shader",
         "bliss-shader", "sildurs-vibrant-shaders", "rethinking-voxels", "solas-shader", "super-duper-vanilla", "miniature-shader",
         "euphoria-patches", "nostalgia-shader", "insanity-shader", "fancy-shaders"]
say("== shader packs (1.20.1, iris)")
for slug in packs:
    try:
        p = get(f"/project/{slug}")
        vs = get(f"/project/{slug}/version", game_versions=["1.20.1"], loaders=["iris"])
        v = vs[0] if vs else None
        say(f"  {slug}: {p['title']} | {p['downloads']:,} dl | updated {p['updated'][:10]} | license {p['license']['id']} | "
            + (f"1.20.1 iris: {v['version_number']} ({v['date_published'][:10]}) loaders={v['loaders']} file={v['files'][0]['filename']}" if v else "no 1.20.1 iris version")
            + f" | {p['description'][:110]}")
    except Exception as e:
        say(f"  {slug}: error {e}")
say("== search 'unleashed' shaders")
for q in ["unleashed", "iris unleashed"]:
    r = get("/search", query=q, facets=[["project_type:shader"]], limit="10")
    for h in r["hits"]:
        say(f"  [{q}] {h['slug']}: {h['title']} | {h['downloads']:,} dl | {h['description'][:100]}")
r = get("/search", query="unleashed", limit="10")
for h in r["hits"]:
    say(f"  [any unleashed] {h['project_type']} {h['slug']}: {h['title']} | {h['downloads']:,} dl | {h['description'][:90]}")
say("== top shaders on 1.20.1")
r = get("/search", facets=[["project_type:shader"], ["versions:1.20.1"]], index="downloads", limit="15")
for h in r["hits"]:
    say(f"  {h['slug']}: {h['title']} | {h['downloads']:,} dl | {h['description'][:90]}")

text = "\n".join(out)
print(text)
for n, i in enumerate(range(0, len(text), 3900)):
    chunk = text[i:i + 3900]
    print(f"::notice title=shaders-{n:02d}::" + chunk.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
