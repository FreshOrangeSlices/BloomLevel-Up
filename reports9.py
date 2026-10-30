"""Throwaway: do the six unused drops have a use in their mod's code? Every class (outside the item registry, creative
tab and model/render code) that names each item's registry field or id, and what those classes are.
Usage: reports9.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, zipfile
server = zipfile.ZipFile(sys.argv[1]); OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
items = {"skarrier_mobs": ["ascendant_ingot", "contagium_ingot", "decayerite_ingot", "pristinium_ingot"],
         "cataclysm": ["enderite_ingot", "void_shard"]}
lines = []
for n in server.namelist():
    if not (n.endswith(".jar") and "/mods/" in "/" + n):
        continue
    z = zipfile.ZipFile(io.BytesIO(server.read(n)))
    names = z.namelist()
    for ns, ids in items.items():
        if not any(x.startswith(f"assets/{ns}/") or x.startswith(f"data/{ns}/") for x in names):
            continue
        lines.append(f"== {n.split('/')[-1]} ({ns})")
        for i in ids:
            field = i.upper().encode()
            hits = []
            for c in names:
                if not c.endswith(".class"):
                    continue
                raw = z.read(c)
                if field in raw or f"{ns}:{i}".encode() in raw or i.encode() in raw:
                    simple = c.rsplit("/", 1)[-1]
                    if re.search(r"(?i)ModItems|Items\.class$|ItemRegistry|Tabs?\.class$|CreativeTab|ModelProvider|LangProvider|ItemModel|Render", c):
                        continue
                    hits.append(c)
            lines.append(f"  {i}: {len(hits)} classes: {hits[:15]}")
        for x in names:
            if x.startswith("data/") and x.endswith(".json") and any(f'"{ns}:{i}"' in z.read(x).decode("utf-8", "replace") for i in ids):
                lines.append(f"  data file naming one: {x}")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines))
