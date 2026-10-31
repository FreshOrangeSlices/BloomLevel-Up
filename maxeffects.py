"""Throwaway: classes that give effects lasting Integer.MAX_VALUE ticks (the pumpkin zombie's strength: 2147481605 left),
in any mod. Usage: maxeffects.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
MAX = b"\x03\x7f\xff\xff\xff"
ADD = re.compile(rb"m_7292_|m_147207_|MobEffectInstance")
picked = []
for jname, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".class"):
            b = jar.read(n)
            if MAX in b and ADD.search(b):
                picked.append((jname, n))
report.append(f"# {len(picked)} classes with Integer.MAX_VALUE and effects")
report += [f"{j.rsplit('/', 1)[1]} :: {n}" for j, n in picked]
with tempfile.TemporaryDirectory() as tmp:
    for j, n in picked[:60]:
        path = os.path.join(tmp, n.replace("/", "_"))
        open(path, "wb").write(jars[j].read(n))
        text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
        lines = text.splitlines()
        hits = [i for i, l in enumerate(lines) if "2147483647" in l]
        keep = set()
        for i in hits:
            keep.update(range(max(0, i - 12), min(len(lines), i + 6)))
        head = [l for l in lines if re.search(r"^\S.*class |public static void on|Event\)", l)][:8]
        report.append(f"## {j.rsplit('/', 1)[1]} :: {n}\n" + "\n".join(head) + "\n...\n" + "\n".join(lines[i] for i in sorted(keep))[:5000])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(picked), "classes")
