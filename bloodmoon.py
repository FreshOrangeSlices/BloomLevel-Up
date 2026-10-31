"""Throwaway: Myths & Legends' blood moon (what it does to monsters, how often, its config) and which pumpkin folk float.
Usage: bloodmoon.py <server.zip> <shots dir> <out dir>"""
import glob, io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []


def javap(jar, entry, limit=40000):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, entry.replace("/", "_"))
        open(path, "wb").write(jar.read(entry))
        return subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout[:limit]


jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
ml = next(j for n, j in jars.items() if "mythsandlegends" in n)
names = ml.namelist()
report.append("# Myths & Legends classes naming the blood moon: " + ", ".join(n for n in names if n.endswith(".class") and b"BloodMoon" in ml.read(n)))
for n in names:
    if re.search(r"BloodMoon[^/]*\.class$|config/[^/]*\.class$|Config[^/]*\.class$", n):
        report.append(f"## {n}\n{javap(ml, n, 20000)}")
for n in names:
    if not n.endswith(".class") and re.search(r"blood|config", n, re.I) and not n.endswith((".png", ".ogg")):
        report.append(f"## file {n}\n{ml.read(n).decode('utf-8', 'replace')[:3000]}")
report.append("# lang lines about the blood moon")
for n in names:
    if n.endswith("en_us.json"):
        for line in ml.read(n).decode("utf-8", "replace").splitlines():
            if re.search(r"blood", line, re.I):
                report.append(line.strip())
report.append("# config files of Myths & Legends (server and client)")
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and re.search(r"myth|legend|blood", os.path.basename(path), re.I):
        report.append(f"## {os.path.relpath(path, shots)}\n" + open(path, encoding="utf-8", errors="replace").read()[:6000])
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and path.endswith((".toml", ".json", ".json5", ".cfg", ".properties")):
        text = open(path, encoding="utf-8", errors="replace").read()
        lines = [l.strip() for l in text.splitlines() if re.search(r"blood.?moon", l, re.I)]
        if lines:
            report.append(f"## mentions in {os.path.relpath(path, shots)}\n" + "\n".join(lines[:30]))
# Which pumpkin folk float: gravity, fall damage, move control, and their animation names.
bic = next(j for n, j in jars.items() if "born_in_chaos" in n.lower())
report.append("# pumpkin folk: gravity and flying hints")
for cls in ["MrPumpkinEntity", "MrsPumpkinEntity", "SenorPumpkinEntity", "PumpkinBruiserEntity", "SirPumpkinheadEntity"]:
    entry = next((n for n in bic.namelist() if n.endswith("/" + cls + ".class")), None)
    if entry:
        text = javap(bic, entry)
        hints = [l.strip() for l in text.splitlines() if re.search(r"m_20242_|NoGravity|Flying|Fly|fly|m_142535_|causeFall|m_7840_|hover|float|levitat|Swim|ldc .*String (?!empty)", l)]
        report.append(f"## {cls}\n" + "\n".join(hints[:60]))
for n in bic.namelist():
    if re.search(r"animations/(mr_?pumpkin|mrs_?pumpkin|senor|sir_?pumpkin|pumpkin_?bruiser)[^/]*\.json$", n, re.I):
        text = bic.read(n).decode("utf-8", "replace")
        report.append(f"## {n}: animations {re.findall(r'\"(animation\.[^\"]+)\"', text)[:30]}")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "sections")
