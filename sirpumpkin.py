"""Throwaway: how Sir Pumpkinhead's fight goes (which classes spawn his horseless form, what the horse form does when hurt
or killed) and where Lord Pumpkinhead comes from. Usage: sirpumpkin.py <server.zip> <shots dir> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
bic = next(zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if "born_in_chaos" in n.lower() and n.endswith(".jar"))
report = []
for needle in [b"SIR_PUMPKINHEAD_WITHOUT_HORSE", b"SIR_PUMPKINHEAD:", b"LORD_PUMPKINHEAD_WITHOUTA_HORSE", b"LORD_PUMPKINHEAD:", b"FELSTEED:"]:
    hits = [n for n in bic.namelist() if n.endswith(".class") and needle in bic.read(n)]
    report.append(f"# {needle.decode()}: {[h.rsplit('/', 1)[1] for h in hits]}")
picked = [n for n in bic.namelist() if re.search(r"/(SirPumpkinhead\w*Entity|SerPumpkinhead\w*Procedure|WithoutHorseP\w*|LordPumpkinheadsLamp\w*|LordPumpkinhead\w*Procedure)\.class$", n)]
with tempfile.TemporaryDirectory() as tmp:
    for n in picked:
        path = os.path.join(tmp, n.replace("/", "_"))
        open(path, "wb").write(bic.read(n))
        text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
        keep = [l for l in text.splitlines() if re.search(r"class |public|protected|ModEntities|m_6074_|m_146870_|kill|discard|m_21223_|getHealth|m_7967_|addFreshEntity|m_20329_|startRiding|m_6667_|die|Event|ldc|MobSpawnType", l)]
        report.append(f"## {n.rsplit('/', 1)[1]}\n" + "\n".join(keep[:90]))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(picked), "classes")
