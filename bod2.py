import glob, io, json, re, sys, time, urllib.request, urllib.error, zipfile
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
def get(url, binary=False):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                if r.status == 202:
                    time.sleep(8); continue
                data = r.read()
                return data if binary else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(4)
        except Exception:
            time.sleep(4)
    return None
out = []
# the tested pack's own record of Book of Dragons and AzureLib, and whether those files still download
for mr in glob.glob("art/**/*.mrpack", recursive=True):
    index = json.loads(zipfile.ZipFile(mr).read("modrinth.index.json"))
    for f in index["files"]:
        if re.search(r"bookofdragons|azurelib|liber", f["path"], re.I):
            d = get(f["downloads"][0], binary=True)
            out.append(f"{mr}: {f['path']} {f['downloads'][0]} -> {'OK ' + str(len(d)) if d and d[:2] == b'PK' else 'GONE'}")
for z in glob.glob("art/**/*server*.zip", recursive=True):
    names = [n for n in zipfile.ZipFile(z).namelist() if re.search(r"bookofdragons|azurelib|liber", n, re.I)]
    out.append(f"{z}: {names}")
for slug in ["book-of-dragons", "bookofdragons", "liber-animalium", "liberanimalium"]:
    data = get(f"https://api.cfwidget.com/minecraft/mc-mods/{slug}")
    if not data or "files" not in data:
        out.append(f"cf {slug}: none"); continue
    files = [f for f in data["files"] if "1.20.1" in f.get("versions", [])]
    out.append(f"cf {slug}: '{data.get('title')}' {data.get('urls',{}).get('curseforge')} license={data.get('license')} 1.20.1 files:")
    for f in sorted(files, key=lambda f: f.get("uploaded_at", ""), reverse=True)[:6]:
        fid, name = f["id"], f["name"]
        u = f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{name}"
        d = get(u, binary=True)
        out.append(f"   {fid} {name} {f.get('uploaded_at','')[:10]} {f.get('type')} {f.get('versions')} -> {'OK ' + str(len(d)) if d and d[:2] == b'PK' else 'FAIL'}")
r = get("https://api.modrinth.com/v2/search?query=liber%20animalium&limit=5")
out += [f"modrinth search: {h['slug']} {h['title']} {h['project_type']} {h['versions'][-3:]}" for h in (r or {}).get("hits", [])]
text = "\n".join(out); print(text)
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=bod2-{n:02d}::" + text[i:i+3900].replace("%", "%25").replace("\n", "%0A"))
