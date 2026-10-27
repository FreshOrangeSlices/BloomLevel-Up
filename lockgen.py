"""Throwaway: the tested pack's exact Modrinth files (from its .mrpack), with slug, version id and whether the API still has them."""
import glob, json, re, sys, time, urllib.request, urllib.error, zipfile
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(url):
    for attempt in range(4):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60))
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(3)
    return None
mr = glob.glob("art/**/*.mrpack", recursive=True)[0]
index = json.loads(zipfile.ZipFile(mr).read("modrinth.index.json"))
lock, status = {}, []
for f in index["files"]:
    m = re.match(r"https://cdn\.modrinth\.com/data/([^/]+)/versions/([^/]+)/", f["downloads"][0])
    if not m:
        status.append(f"not modrinth: {f['path']}"); continue
    pid, vid = m.groups()
    project = get(f"https://api.modrinth.com/v2/project/{pid}")
    version = get(f"https://api.modrinth.com/v2/version/{vid}")
    slug = project["slug"] if project else pid
    listed = get(f"https://api.modrinth.com/v2/project/{pid}/version?game_versions=%5B%221.20.1%22%5D") or []
    in_list = any(v["id"] == vid for v in listed)
    status.append(f"{slug}: {version and version['version_number']} /version/{vid} -> {'OK' if version else '404'}, listed={in_list}, env={f.get('env')}")
    lock[slug] = {"version": version["version_number"] if version else None, "id": vid, "project": pid, "path": f["path"],
                  "url": f["downloads"][0], "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"], "size": f["fileSize"],
                  "required": [[d.get("project_id"), d.get("version_id")] for d in (version or {}).get("dependencies", []) if d["dependency_type"] == "required"]}
text = "\n".join(status); print(text)
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
for n, i in enumerate(range(0, len(text), 3900)):
    print(f"::notice title=lock-status-{n:02d}::" + esc(text[i:i+3900]))
blob = json.dumps(lock, separators=(",", ":"), sort_keys=True)
for n, i in enumerate(range(0, len(blob), 3900)):
    print(f"::notice title=lock-json-{n:02d}::" + esc(blob[i:i+3900]))
