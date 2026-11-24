"""Born in Chaos's natural spawn condition procedures: what each checks (light or not)."""
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
open("bic.jar", "wb").write(get(jars["borninchaos"]))
z = zipfile.ZipFile("bic.jar")
procs = sorted(n for n in z.namelist() if n.endswith(".class") and "/procedures/" in n
               and re.search(r"(?i)NaturalnoieUslovii|Usloviie|pri[A-Z]?Procedure|priProcedure|SpawnCondition|Poiavlien", n))
p(f"{len(procs)} spawn condition procedures")
for n in procs:
    cls = n[:-6].replace("/", ".")
    r = subprocess.run(["java", "-jar", "cfr.jar", "bic.jar", "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
    text = r.stdout
    at = text.find("public static boolean execute")
    body = " ".join(text[at:at + 1600].split()) if at >= 0 else "(no execute)"
    p(f"---- {n.rsplit('/', 1)[-1][:-6]}\n    {body[:1600]}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/bic_conditions.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
