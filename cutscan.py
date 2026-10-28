"""Throwaway: for the weapons FreshOrangeSlices marked Cut, every recipe that makes or uses one, and every loot table,
loot modifier and tag that names one, across the pack's jars. Written to data-out/weapons/cutscan.json."""
import io, json, re, sys, zipfile, collections
server_zip, cut_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
cut = set(json.load(open(cut_path)))
ID = re.compile(r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"')
found = {"recipes": [], "loot": [], "modifiers": [], "tags": [], "other": []}
def walk_results(node, in_result=False, results=None, ingredients=None):
    if isinstance(node, dict):
        for k, v in node.items():
            r = in_result or k in ("result", "output", "results")
            if k in ("item", "id") and isinstance(v, str):
                (results if r else ingredients).add(v)
            elif k in ("result", "output") and isinstance(v, str):
                results.add(v)
            else:
                walk_results(v, r, results, ingredients)
    elif isinstance(node, list):
        for v in node:
            walk_results(v, in_result, results, ingredients)
with zipfile.ZipFile(server_zip) as server:
    for jar in [n for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")]:
        try:
            z = zipfile.ZipFile(io.BytesIO(server.read(jar)))
        except Exception:
            continue
        for n in z.namelist():
            if not (n.startswith("data/") and n.endswith(".json")):
                continue
            text = z.read(n).decode("utf-8", "replace")
            hits = sorted(set(ID.findall(text)) & cut)
            if not hits:
                continue
            entry = {"jar": jar.split("/")[-1], "file": n, "cut": hits}
            if "/recipes/" in n:
                try:
                    results, ingredients = set(), set()
                    walk_results(json.loads(text), False, results, ingredients)
                    entry["makes"] = sorted(results)
                    entry["uses_cut"] = sorted(ingredients & cut)
                    entry["type"] = json.loads(text).get("type")
                except Exception as e:
                    entry["error"] = str(e)
                found["recipes"].append(entry)
            elif "/loot_tables/" in n:
                found["loot"].append(entry)
            elif "/loot_modifiers/" in n:
                entry["text"] = text[:400]
                found["modifiers"].append(entry)
            elif "/tags/" in n:
                found["tags"].append(entry)
            else:
                found["other"].append({**entry, "text": text[:200]})
json.dump(found, open(out + "/cutscan.json", "w"), indent=1)
print({k: len(v) for k, v in found.items()})
uses = [r for r in found["recipes"] if r.get("uses_cut") and not set(r.get("makes", [])) <= cut]
print("recipes for KEPT items that use a cut weapon:", len(uses))
for r in uses[:40]:
    print("  ", r["file"], "makes", r.get("makes"), "from", r["uses_cut"])
