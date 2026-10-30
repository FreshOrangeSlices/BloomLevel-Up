"""Throwaway: Born in Chaos's seasonal events. Which classes call each event procedure, and the procedures' bytecode
(date checks, what they spawn). Usage: reports14.py <server.zip> <shots dir> <out dir>"""
import io, json, os, re, subprocess, sys, time, urllib.request, zipfile
OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
url = "https://cdn.modrinth.com/data/1kcHy4de/versions/8wmEhUSt/born_in_chaos_%5BForge%5D1.20.1_1.7.5.jar"
data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180).read()
z = zipfile.ZipFile(io.BytesIO(data)); names = z.namelist()
work = os.path.join(OUT, "classes"); os.makedirs(work, exist_ok=True)
lines = []
events = ["HalloweenEventSpawnProcedure", "ChillingHorrorEventSpawnProcedure", "ChaoticSpringEventSpawnProcedure", "SerPumpkinheadSpavnProcedure",
          "HalloweenEventDropProcedure"]
for ev in events:
    callers = [c for c in names if c.endswith(".class") and ev.encode() in z.read(c) and not c.endswith(ev + ".class")]
    lines.append(f"## {ev}: called from {[c.rsplit('/',1)[1] for c in callers]}")
    for c in [c for c in names if c.endswith("/" + ev + ".class")] + callers[:6]:
        path = os.path.join(work, c.replace("/", "_"))
        open(path, "wb").write(z.read(c))
        out = subprocess.run(["javap", "-c", "-p", path], capture_output=True, text=True).stdout
        keep = [l for l in out.splitlines() if re.search(r"Calendar|LocalDate|MonthDay|getMonth|DAY_OF|MONTH|String |EntityType|ModEntities|BornInChaos.*Entities|bipush|sipush|iconst|if_icmp|Method .*Procedure|public|static|Field .*Entities", l)]
        lines.append(f"### {c.rsplit('/',1)[1]}\n" + "\n".join(keep[:220]))
# Which entity classes register spawn placements with a predicate mentioning an event procedure
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines)[:3000])
