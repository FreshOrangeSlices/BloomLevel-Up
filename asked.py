"""Throwaway: the mods FreshOrangeSlices asked about, on Modrinth and CurseForge, for Forge 1.20.1."""
import io, json, re, time, urllib.parse, urllib.request, urllib.error, zipfile
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
def toml_info(jar):
    try:
        z = zipfile.ZipFile(io.BytesIO(jar))
        t = z.read("META-INF/mods.toml").decode(errors="replace")
    except Exception as e:
        return f"(no mods.toml: {e})"
    ids = re.findall(r'^\s*modId\s*=\s*"([^"]+)"', t, re.M)
    deps = []
    for block in re.findall(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\n\[\[|\Z)", t, re.S):
        mid = re.search(r'modId\s*=\s*"([^"]+)"', block); man = re.search(r'mandatory\s*=\s*(\w+)', block); rng = re.search(r'versionRange\s*=\s*"([^"]*)"', block); side = re.search(r'side\s*=\s*"([^"]*)"', block)
        if mid and mid.group(1) not in ("forge", "minecraft"):
            deps.append(f"{mid.group(1)}{'' if man and man.group(1) == 'true' else '(optional)'} {rng.group(1) if rng else ''} {side.group(1) if side else ''}")
    desc = re.search(r'description\s*=\s*\'\'\'(.*?)\'\'\'|description\s*=\s*"([^"]*)"', t, re.S)
    return f"mod ids {ids[:4]}; deps: {deps}; desc: {(desc.group(1) or desc.group(2) or '').strip()[:200] if desc else ''}"
for slug in ["bosses-oddities", "the-awakening", "gtbcs-cataclysmic-boss-ui", "kingdoms-villager-retaliation", "kingdoms-villager-retaliation-fa",
             "kingdoms", "bosses-rise", "bossesrise"]:
    data = get(f"https://api.cfwidget.com/minecraft/mc-mods/{slug}")
    if not data or "files" not in data:
        out.append(f"CF {slug}: not found"); continue
    files = data["files"]
    f20 = [f for f in files if "1.20.1" in f.get("versions", [])]
    loaders = sorted({v for f in files for v in f.get("versions", []) if v in ("Forge", "NeoForge", "Fabric", "Quilt")})
    games = sorted({v for f in files for v in f.get("versions", []) if re.fullmatch(r"1\.\d+(\.\d+)?|26\.\d+", v)})
    out.append(f"CF {slug}: '{data.get('title')}' by {[m.get('username') for m in data.get('members', [])][:2]} | {data.get('downloads', {}).get('total')} dl | loaders {loaders} | games {games[-6:]} | {data.get('summary', '')[:160]}")
    forge20 = [f for f in f20 if "Forge" in f.get("versions", [])]
    for f in sorted(forge20, key=lambda f: f.get("uploaded_at", ""), reverse=True)[:2]:
        fid, name = f["id"], f["name"]
        url = f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{name}"
        jar = get(url, binary=True)
        out.append(f"   forge 1.20.1 file {fid} {name} {f.get('uploaded_at', '')[:10]} {f.get('type')} -> {'OK' if jar and jar[:2] == b'PK' else 'no download'}; " + (toml_info(jar) if jar and jar[:2] == b'PK' else ""))
    if not forge20:
        out.append(f"   no Forge 1.20.1 file; 1.20.1 files: {[(f['name'], [v for v in f['versions'] if v in ('Forge','NeoForge','Fabric')]) for f in f20[:4]]}")
for q in ["Bosses Oddities", "The Awakening", "Cataclysmic Boss UI", "Kingdoms Villager Retaliation"]:
    r = get("https://api.modrinth.com/v2/search?" + urllib.parse.urlencode({"query": q, "limit": "4"}))
    for h in (r or {}).get("hits", []):
        out.append(f"Modrinth [{q}] {h['slug']}: {h['title']} | {h['downloads']:,} dl | loaders {[c for c in h['categories'] if c in ('forge','neoforge','fabric')]} | versions {h['versions'][-4:]} | {h['description'][:110]}")
text = "\n".join(out); print(text); open("asked.txt", "w").write(text)
