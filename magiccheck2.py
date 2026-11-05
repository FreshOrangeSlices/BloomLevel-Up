"""Throwaway: where Paladins & Priests keeps its spells, what they cost, and any rune items. Usage: magiccheck2.py <server.zip> <out dir>"""
import io, json, os, re, sys, zipfile
server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
summary = []
for name in sorted(server.namelist()):
    if not (name.startswith("mods/") and name.endswith(".jar")):
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    paths = jar.namelist()
    runes = [p for p in paths if re.search(r"rune", p, re.I) and not p.endswith(".class") and "irons_spellbooks" not in name]
    if re.search(r"paladins|rogues|spell_engine", name):
        dirs = sorted({re.sub(r"/[^/]+$", "", p) for p in paths if p.startswith("data/")})
        summary.append(f"DIRS {name}: " + " ".join(d for d in dirs if "spell" in d or "rune" in d)[:1500])
        for p in paths:
            if p.endswith(".json") and re.search(r"data/[^/]+/spells?/", p):
                try:
                    j = json.loads(jar.read(p))
                except Exception:
                    continue
                summary.append(f"SPELL {p}: school={j.get('school')} cost={json.dumps(j.get('cost'))[:220]}")
    if runes:
        summary.append(f"RUNES {name}: " + " ".join(runes[:40]))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary))
print(len(summary))
