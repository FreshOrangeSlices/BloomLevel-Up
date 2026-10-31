"""Throwaway: recipe rows from the server's registry dump whose result id matches a pattern. Usage: recipes.py <shots dir> <out dir> <regex>"""
import glob, json, os, re, sys
shots, out, pattern = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)), None)
rows = []
for line in open(dump):
    if '"kind":"recipe"' in line.replace(" ", ""):
        r = json.loads(line)
        if re.search(pattern, json.dumps(r.get("result", r.get("output", ""))) + " " + r.get("id", "")):
            rows.append(r)
json.dump(rows, open(os.path.join(out, "recipes.json"), "w"), indent=1)
print(len(rows), "recipes")
