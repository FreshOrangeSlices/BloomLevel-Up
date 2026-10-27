"""Throwaway: Ice and Fire's (1.20.1 Forge) config entries about its main menu, read from its jar."""
import io, json, re, urllib.request, urllib.parse, zipfile
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
def get(url, binary=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        d = r.read(); return d if binary else json.loads(d)
q = urllib.parse.urlencode({"loaders": json.dumps(["forge"]), "game_versions": json.dumps(["1.20.1"])})
v = get(f"https://api.modrinth.com/v2/project/ice-and-fire-dragons/version?{q}")[0]
f = next(f for f in v["files"] if f.get("primary"))
jar = zipfile.ZipFile(io.BytesIO(get(f["url"], binary=True)))
lines = [f"{v['version_number']} {f['filename']}"]
for name in jar.namelist():
    if not name.endswith(".class"):
        continue
    data = jar.read(name)
    strings = [s.decode("latin-1") for s in re.findall(rb"[\x20-\x7e]{4,}", data)]
    hits = [s for s in strings if re.search(r"(?i)main.?menu|title.?screen|custommenu", s)]
    if hits:
        lines.append(f"{name}: " + " | ".join(dict.fromkeys(hits))[:600])
    if "config" in name.lower() and ("Client" in name or "Config" in name):
        cfg = [s for s in strings if re.match(r"^[a-z][A-Za-z]+$", s) and len(s) > 8][:80]
        lines.append(f"  [{name} keys] " + ", ".join(cfg)[:900])
for name in jar.namelist():
    if name.endswith(".toml") or "config" in name.lower() and name.endswith((".json", ".cfg")):
        lines.append(f"file: {name}")
text = "\n".join(lines); print(text)
esc = lambda t: t.replace("%", "%25").replace("\n", "%0A")
for n, i in enumerate(range(0, len(text), 3800)):
    print(f"::notice title=find3-{n:02d}::{esc(text[i:i+3800])}")
