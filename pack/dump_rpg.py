# The RPG Series and Combat Roll, for fitting them to Bloom: copies the jars the new mods brought (compared with the
# pack without them) and the configs the server wrote on its first start into one folder, plus the key bindings each
# jar registers (javap: the constants passed to KeyMapping).
import os, re, shutil, subprocess, sys, zipfile
base_mods, mods, server, out = sys.argv[1:5]
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
os.makedirs(f"{out}/jars", exist_ok=True)
base = set(os.listdir(base_mods))
new = sorted(f for f in os.listdir(mods) if f not in base)
server_new = sorted(f for f in os.listdir(f"{server}/mods") if f not in base and f not in new and f.endswith(".jar"))
for f in new:
    shutil.copy(f"{mods}/{f}", f"{out}/jars/{f}")
for f in server_new:
    shutil.copy(f"{server}/mods/{f}", f"{out}/jars/{f}")
shutil.copytree(f"{server}/config", f"{out}/server-config", dirs_exist_ok=True)
keys = []
for f in new:
    work = f"/tmp/k/{f}"
    with zipfile.ZipFile(f"{mods}/{f}") as z:
        for n in z.namelist():
            if n.endswith(".class") and b"KeyMapping" in z.read(n):
                z.extract(n, work)
                text = subprocess.run(["javap", "-c", "-p", f"{work}/{n}"], capture_output=True, text=True).stdout
                for block in re.findall(r"((?:ldc.*\n.*?){0,6}?invokespecial #\d+\s+// Method net/minecraft/client/KeyMapping\.\"<init>\".*)", text):
                    consts = re.findall(r"String ([\w.]+)|bipush\s+(-?\d+)|sipush\s+(-?\d+)|iconst_m?(\d)", block)
                    keys.append(f"{f} {n}: " + " ".join(next(x for x in c if x) for c in consts))
open(f"{out}/keys.txt", "w").write("\n".join(keys) + "\n")
summary = f"new jars ({len(new)}): " + ", ".join(new) + "\nserver-only new: " + ", ".join(server_new) + "\n\nkeys:\n" + "\n".join(keys)
print(f"::notice title=rpg-new::{esc(summary[:3800])}")
