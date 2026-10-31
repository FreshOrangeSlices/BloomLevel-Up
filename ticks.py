"""Throwaway: what could put effects on a plain zombie as it lives: classes that add effects from a tick or equipment
handler (any mod), and Born in Chaos's Halloween gear (the rotten infernal pumpkin, the sweet axe and sword).
Usage: ticks.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
jars = {n: zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")}
ADD = re.compile(rb"m_7292_|m_147207_")
TICK = re.compile(rb"LivingTickEvent|LivingEvent\$LivingTickEvent|m_8119_|m_6075_|aiStep|m_8107_|onArmorTick|inventoryTick|m_6883_")
picked = []
for jname, jar in jars.items():
    for n in jar.namelist():
        if n.endswith(".class"):
            b = jar.read(n)
            if ADD.search(b) and (re.search(rb"LivingTickEvent", b) or re.search(rb"ROTTEN_INFERNAL_PUMPKIN|SWEET_SWORD|SWEET_AXE|EVIL_CARVED_PUMPKIN", b)):
                picked.append((jname, n))
report.append("# classes adding effects from a living-tick handler, or naming Born in Chaos's Halloween gear")
report += [f"{j.rsplit('/', 1)[1]} :: {n}" for j, n in picked]
with tempfile.TemporaryDirectory() as tmp:
    for j, n in picked[:40]:
        path = os.path.join(tmp, n.replace("/", "_"))
        open(path, "wb").write(jars[j].read(n))
        text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
        keep = [l for l in text.splitlines() if re.search(r"class |public|static|MobEffect|f_19\d+_|m_7292_|m_147207_|instanceof|Zombie|Monster|Mob\b|ROTTEN|SWEET|EVIL|HEAD|Event|ldc|Config", l)]
        report.append(f"## {j.rsplit('/', 1)[1]} :: {n}\n" + "\n".join(keep[:120]))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(picked), "classes")
