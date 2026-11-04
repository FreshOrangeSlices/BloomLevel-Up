"""Throwaway: javap -c of chosen food items and their eaten procedures. Usage: foodcheck2.py <server.zip> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
PAT = re.compile(r"(item/(RottenFish|MonsterFlesh|SmokedMonsterFlesh|SeaTerrorEye|IntoxicatingDecoction|StimulatingDecoction|RawSlither|LeechingSack|DemonStew|SmokedFish|SmokedFlesh|PotionofRage)Item"
                 r"|procedures/(MonsterFlesh|SmokedFlesh|RottenFish|SeaTerrorEye|IntoxicatingDecoction)\w*Procedure"
                 r"|procedures/(RawSlither|LeechingSack)PlayerFinishesUsingItemProcedure)\.class$")
report, summary = [], []
for name in sorted(server.namelist()):
    if not (name.startswith("mods/") and name.endswith(".jar") and re.search(r"born_in_chaos|skarrier|undead_unleashed", name)):
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    with tempfile.TemporaryDirectory() as tmp:
        for n in jar.namelist():
            if PAT.search(n):
                path = os.path.join(tmp, n)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(jar.read(n))
                text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
                report.append(f"## {n}\n{text[:20000]}")
                effects = sorted(set(re.findall(r"Field ([\w/]+\.\w+):L[\w/]*(?:MobEffect|RegistryObject)", text)))
                summary.append(f"{n}: {' '.join(effects)}")
open(os.path.join(out, "javap.txt"), "w").write("\n".join(report))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary))
print(len(report), "classes")
