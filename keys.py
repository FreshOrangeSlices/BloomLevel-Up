"""Throwaway: every key binding in the pack's test client (game/options.txt after a real run), with each binding's
English name from the mods' lang files, and every key two or more bindings share.
Usage: keys.py <shots dir> <server.zip> <out dir>"""
import glob, io, json, os, re, sys, zipfile
shots, server_zip, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
options = next(iter(glob.glob(os.path.join(shots, "**", "options.txt"), recursive=True)), None)
names = {}
server = zipfile.ZipFile(server_zip)
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar"):
        try:
            jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        except Exception:
            continue
        for n in jar.namelist():
            if re.match(r"assets/[^/]+/lang/en_us\.json$", n):
                try:
                    for k, v in json.loads(jar.read(n).decode("utf-8", "replace")).items():
                        if k.startswith(("key.", "keybind", "category.")) or ".key." in k or "keybind" in k:
                            names[k] = v
                except Exception:
                    pass
rows = []
for line in open(options, encoding="utf-8", errors="replace") if options else []:
    if line.startswith("key_"):
        binding, key = line[4:].rstrip("\n").split(":", 1)
        rows.append({"binding": binding, "key": key, "name": names.get(binding, "")})
by_key = {}
for r in rows:
    if r["key"] != "key.keyboard.unknown":
        by_key.setdefault(r["key"], []).append(r)
clashes = {k: v for k, v in by_key.items() if len(v) > 1}
json.dump({"options": options, "bindings": rows, "clashes": clashes}, open(os.path.join(out, "keys.json"), "w"), indent=1, ensure_ascii=False)
lines = [f"{k}: " + "; ".join(f"{r['name'] or r['binding']} ({r['binding']})" for r in v) for k, v in sorted(clashes.items())]
open(os.path.join(out, "clashes.txt"), "w").write("\n".join(lines) + "\n")
print(f"{len(rows)} bindings, {len(clashes)} keys shared; from {options}")
