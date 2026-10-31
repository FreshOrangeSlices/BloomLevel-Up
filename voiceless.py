"""Throwaway: Voiceless Survival's config as the pack's server made it (and defaults in its jar).
Usage: voiceless.py <server.zip> <shots dir> <out dir>; also walks the pack artifact folder next to the server zip."""
import io, os, re, sys, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
report = []
PAT = re.compile(r"ezvc|voiceless|vcsurvival", re.I)


def from_zip(z, label):
    for n in z.namelist():
        if PAT.search(n) and not n.endswith("/") and not n.endswith(".class") and not n.endswith(".png") and not n.endswith(".ogg"):
            data = z.read(n)
            if n.endswith(".jar"):
                inner = zipfile.ZipFile(io.BytesIO(data))
                for m in inner.namelist():
                    if re.search(r"\.(json|toml)$", m) and not m.startswith("assets/") or "lang/en_us" in m:
                        report.append(f"## {label}::{n}::{m}\n" + inner.read(m).decode("utf-8", "replace")[:6000])
            else:
                report.append(f"## {label}::{n}\n" + data.decode("utf-8", "replace")[:6000])


pack_dir = os.path.dirname(server_zip)
for root, _, files in os.walk(pack_dir):
    for f in files:
        path = os.path.join(root, f)
        if f.endswith(".zip") or f.endswith(".mrpack"):
            try:
                from_zip(zipfile.ZipFile(path), f)
            except zipfile.BadZipFile:
                pass
        elif PAT.search(path):
            report.append(f"## {path}\n" + open(path, encoding="utf-8", errors="replace").read()[:6000])
for root, _, files in os.walk(shots):
    for f in files:
        path = os.path.join(root, f)
        if PAT.search(path):
            report.append(f"## {path}\n" + open(path, encoding="utf-8", errors="replace").read()[:6000])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) or "nothing found")
