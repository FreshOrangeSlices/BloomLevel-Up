"""Throwaway: from a pack run's artifacts (the layer-6 registry dump and the server zip), what's needed to sort the
pack's weapons: the dump's weapon rows, every mod's English lines that name a weapon (or its tooltip keys), and the
Spell Engine spell assignments. Written to data-out/weapons/."""
import io, json, os, re, sys, zipfile
dump_path, server_zip, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
rows = [json.loads(l) for l in open(dump_path) if l.strip()]
weapons = [r for r in rows if r.get("kind") == "item" and "hooks" in r]
print("dump rows", len(rows), "weapon-like items", len(weapons))
paths = {r["id"].split(":", 1)[1] for r in weapons}
long_paths = sorted(p for p in paths if len(p) >= 6)
short_paths = {p for p in paths if len(p) < 6}
keys = {k for r in weapons for k in r.get("tooltip", {}).get("keys", [])}
lang, spells, iron = {}, {}, {}
with zipfile.ZipFile(server_zip) as server:
    jars = [n for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")]
    for jar in jars:
        try:
            z = zipfile.ZipFile(io.BytesIO(server.read(jar)))
        except Exception as e:
            print("skip", jar, e); continue
        for n in z.namelist():
            if re.fullmatch(r"assets/[^/]+/lang/en_us\.json", n):
                try:
                    entries = json.loads(z.read(n).decode("utf-8", "replace"))
                except Exception:
                    continue
                for k, v in entries.items():
                    if k in keys or any(p in k for p in long_paths) or set(re.split(r"[._]", k)) & short_paths:
                        lang[k] = v
            elif "/spell_assignments/" in n and n.endswith(".json"):
                spells[n] = z.read(n).decode("utf-8", "replace")
            elif n.startswith("data/") and "/spells/" in n and n.endswith(".json") and len(spells) < 4000:
                spells[n] = z.read(n).decode("utf-8", "replace")[:600]
json.dump(weapons, open(os.path.join(out, "weapons.json"), "w"))
json.dump(lang, open(os.path.join(out, "lang.json"), "w"), indent=0, sort_keys=True)
json.dump(spells, open(os.path.join(out, "spells.json"), "w"), indent=0, sort_keys=True)
print("lang lines", len(lang), "spell files", len(spells))
