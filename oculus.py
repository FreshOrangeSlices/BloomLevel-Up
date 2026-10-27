import io, json, re, urllib.request, zipfile
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(u, binary=False):
    d = urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=120).read()
    return d if binary else json.loads(d)
out = []
v = get("https://api.modrinth.com/v2/project/oculus/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D")[0]
z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], True)))
out.append(f"oculus {v['version_number']} {v['files'][0]['filename']}")
hits = {}
for n in z.namelist():
    if n.endswith(".class"):
        data = z.read(n)
        for s in set(re.findall(rb"[A-Za-z_.-]*\.properties|enableShaders|shaderPack[A-Za-z]*|iris\.keybind\.[a-zA-Z]+|oculus\.keybind\.[a-zA-Z]+", data)):
            hits.setdefault(s.decode(), []).append(n.split("/")[-1])
for k, cls in sorted(hits.items()):
    out.append(f"  {k}: {sorted(set(cls))[:4]}")
for n in z.namelist():
    if n.endswith("en_us.json"):
        lang = json.loads(z.read(n))
        out.append(f"  {n}: " + "; ".join(f"{k}={v}" for k, v in lang.items() if "keybind" in k or k.startswith("key.")))
for n in z.namelist():
    if "KeyBind" in n or "Keybind" in n or "KeyMapping" in n:
        out.append(f"  class {n}")
text = "\n".join(out); print(text); open("oculus.txt", "w").write(text)
