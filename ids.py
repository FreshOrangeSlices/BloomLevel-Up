"""Throwaway: every structure, biome and dimension id in the pack's server mods, Iron's spell ids, and entity names."""
import json, os, re, sys, zipfile

mods = sys.argv[1]
out = {"structures": {}, "biomes": {}, "dimensions": {}, "spells": [], "entity_names": {}, "item_names": {}, "jars": {}}
def walk(z, jarname):
    for n in z.namelist():
        m = re.match(r"data/([^/]+)/worldgen/(structure|biome)/(.+)\.json$", n)
        if m:
            out["structures" if m.group(2) == "structure" else "biomes"].setdefault(m.group(1), []).append(m.group(3))
        m = re.match(r"data/([^/]+)/dimension/(.+)\.json$", n)
        if m:
            out["dimensions"].setdefault(m.group(1), []).append(m.group(2))
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            try:
                lang = json.loads(z.read(n).decode("utf-8", "replace"))
            except Exception:
                continue
            for k, v in lang.items():
                if k.startswith("entity.") and k.count(".") == 2:
                    out["entity_names"][k[7:].replace(".", ":", 1)] = v
                elif k.startswith("item.") and k.count(".") == 2:
                    out["item_names"][k[5:].replace(".", ":", 1)] = v
                elif k.startswith("spell.irons_spellbooks.") and k.count(".") == 2:
                    out["spells"].append(k.split(".")[2])
        if n.endswith(".jar") and n.startswith("META-INF/jarjar/"):
            try:
                import io
                walk(zipfile.ZipFile(io.BytesIO(z.read(n))), jarname + "!" + n)
            except Exception:
                pass
for f in sorted(os.listdir(mods)):
    if f.endswith(".jar"):
        try:
            with zipfile.ZipFile(os.path.join(mods, f)) as z:
                walk(z, f)
        except Exception as e:
            out["jars"][f] = str(e)
for k in ("structures", "biomes", "dimensions"):
    for ns in out[k]:
        out[k][ns].sort()
out["spells"] = sorted(set(out["spells"]))
json.dump(out, sys.stdout, indent=1, sort_keys=True)
