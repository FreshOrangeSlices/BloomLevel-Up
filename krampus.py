"""Throwaway: Born in Chaos's Krampus and his henchmen: biome modifiers, spawn placement (init) and the procedures it
calls, game rules naming them. Usage: krampus.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []


def javap(jar, entries, limit=40000):
    texts = {}
    with tempfile.TemporaryDirectory() as tmp:
        for n in entries:
            path = os.path.join(tmp, n.replace("/", "_"))
            open(path, "wb").write(jar.read(n))
            texts[n] = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout[:limit]
    return texts


bic = None
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar") and "born_in_chaos" in name.lower():
        bic = zipfile.ZipFile(io.BytesIO(server.read(name)))
names = bic.namelist()
for n in sorted(names):
    if "biome_modifier" in n and "krampus" in n.lower():
        report.append(f"## {n}\n{bic.read(n).decode('utf-8', 'replace')}")
ents = [n for n in names if re.search(r"/entity/Krampus\w*Entity\.class$", n)]
report.append("# entity classes: " + ", ".join(ents))
procs = set()
for n, text in javap(bic, ents).items():
    keep, on = [], False
    for l in text.splitlines():
        if re.search(r"static void init\(\)|lambda\$init", l):
            on = True
        elif re.match(r"  \S", l) and on:
            on = bool(re.search(r"lambda\$init", l))
        if on:
            keep.append(l)
    procs |= set(re.findall(r"procedures/(\w+)", text))
    report.append(f"## {n} calls: {sorted(set(re.findall(r'procedures/(\w+)', text)))}\n" + "\n".join(keep[:200]))
spawnish = sorted(p for p in procs if re.search(r"spa[wv]n|condition|natural|usloviy", p, re.I))
report.append("# spawn-ish procedures: " + ", ".join(spawnish))
for n, text in javap(bic, [n for n in names if re.search(r"/procedures/(" + "|".join(spawnish or ["NONE"]) + r")\.class$", n)]).items():
    report.append(f"## {n}\n{text[:14000]}")
# Other procedures that mention Krampus and a date or a game rule.
for n in names:
    if n.endswith(".class") and "/procedures/" in n:
        b = bic.read(n)
        if b"KRAMPUS" in b and (b"java/util/Calendar" in b or b"GameRules" in b or b"LocalDate" in b):
            report.append(f"# {n} names KRAMPUS with a date or game rule")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
print("\n".join(report)[:3000])
