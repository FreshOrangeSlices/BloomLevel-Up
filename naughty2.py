"""Throwaway: Born in Chaos's naughtiness: the villager procedure in full, the player variables, every class that touches
the naughtiness value, and its game rules. Usage: naughty2.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
bic = next(zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and "born_in_chaos" in n.lower())
names = bic.namelist()
report = []


def javap(n, flags=("-c", "-p", "-constants")):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "x.class")
        open(path, "wb").write(bic.read(n))
        return subprocess.run(["javap", *flags, path], capture_output=True, text=True).stdout


NOISE = re.compile(r"^\s+\d+: (aload|astore|dload|dstore|iload|istore|dup|pop|checkcast|areturn|return|goto|new|invokespecial #\d+ +// Method java/lang/Object)")
def slim(text):
    return "\n".join(l for l in text.splitlines() if not NOISE.match(l))


vars_cls = [n for n in names if n.endswith("BornInChaosV1ModVariables$PlayerVariables.class")]
fields = javap(vars_cls[0], ("-p",)) if vars_cls else ""
report.append("## PlayerVariables fields\n" + fields)
field_names = [m.group(1) for m in re.finditer(r"public double (\w+);", fields)]
naughty = [f for f in field_names if re.search(r"naught|krampus|sin|bad", f, re.I)] or field_names
report.append("# naughtiness-ish fields: " + ", ".join(naughty))
for n in names:
    if n.endswith("/KillingVillagersProcedure.class"):
        report.append(f"## {n} in full\n" + slim(javap(n))[:30000])
touch = []
for n in names:
    if n.endswith(".class") and "/procedures/" in n:
        b = bic.read(n)
        if any(f.encode() in b for f in naughty):
            touch.append(n)
report.append("# procedures touching " + ", ".join(naughty) + ": " + ", ".join(t.rsplit("/", 1)[1] for t in touch))
for n in touch:
    if "KillingVillagers" in n or "Killinganimals" in n:
        continue
    text = slim(javap(n))
    keep = "\n".join(l for l in text.splitlines() if re.search(r"public|ldc|instanceof|String|Field|invokestatic|getfield|putfield|dcmp|if|sipush|bipush", l))
    report.append(f"## {n}\n" + keep[:7000])
rules = [n for n in names if n.endswith("ModGameRules.class")]
for n in rules:
    text = javap(n)
    report.append(f"## {n}\n" + "\n".join(l for l in text.splitlines() if re.search(r"ldc|iconst|bipush|putstatic|m_46250_|create", l))[:6000])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
