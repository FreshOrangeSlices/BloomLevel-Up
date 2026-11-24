"""Born in Chaos's (and Undead Unleashed's) spawn rules: the predicate each mob registers, light or not."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"}


def get(urls):
    for url in urls:
        for _ in range(3):
            try:
                return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
            except Exception:
                pass
    return None


jars = json.load(open("probe/alljars.json"))
out = []
p = out.append
for slug in ("borninchaos", "undead-unleashed"):
    path = slug + ".jar"
    open(path, "wb").write(get(jars[slug]))
    z = zipfile.ZipFile(path)
    for name in sorted(z.namelist()):
        if not name.endswith("Entity.class") or "$" in name or b"SpawnPlacements" not in z.read(name):
            continue
        cls = name[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        text = r.stdout
        at = text.find("SpawnPlacements.m_21754_(")
        pred = " ".join(text[at:at + 3000].split()) if at >= 0 else "(no register call found)"
        # Helper methods the predicate calls (MCreator puts conditions in procedures).
        p(f"{name.rsplit('/', 1)[-1][:-6]}: {pred[:2600] if any(w in name for w in ("DecayingZombie", "DecrepitSkeleton", "BarrelZombie", "SkeletonThrasher", "ZombieLumberjack", "Wraith")) else pred[:200]}")
        for helper in re.findall(r"(\w+Procedure)\.execute", pred):
            p(f"    uses {helper}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/bic_spawns.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
