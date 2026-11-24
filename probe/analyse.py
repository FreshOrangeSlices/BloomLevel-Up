"""The Damned's leftovers stayed for days: which of its mobs are persistent from birth or never despawn by themselves?

For each mod with mobs on The Damned, lists the entity classes that call setPersistenceRequired (m_21530_), override
removeWhenFarAway (m_6785_) or requiresCustomPersistence (m_8023_), and decompiles those methods and constructors.
"""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"}
SLUGS = ["borninchaos", "the-graveyard-forge", "myths-and-legends", "cf:ddenizens-1.20.1-2.0.0.jar", "undead-unleashed",
         "mariums-soulslike-weaponry"]
# The Damned's mobs, by the words in their ids, to pick their classes out.
WORDS = ["ghoul", "ghouling", "nightmare", "reaper", "wraith", "lifestealer", "stalker", "spiritofchaos", "spirit_of_chaos",
         "restless", "scarlet", "persecutor", "seared", "infernal", "imp", "alp", "eidolon", "condemned", "jiangshi", "bodak",
         "shadow", "daemon", "flamebreather", "fallenchaosknight", "fallen_chaos_knight", "chaosknight", "shadowlord",
         "withereddemon", "withered_demon"]


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
for slug in SLUGS:
    data = get(jars[slug]) if slug in jars else None
    if not data:
        p(f"{slug}: not downloaded")
        continue
    path = re.sub(r"[^A-Za-z0-9._-]", "_", slug) + ".jar"
    open(path, "wb").write(data)
    z = zipfile.ZipFile(path)
    p(f"==== {slug}")
    for name in sorted(z.namelist()):
        if not name.endswith(".class") or "$" in name:
            continue
        simple = name.rsplit("/", 1)[-1][:-6].lower()
        if not any(w in simple for w in WORDS) or "renderer" in simple or "model" in simple or "item" in simple:
            continue
        b = z.read(name)
        marks = [label for label, pat in (("setPersistenceRequired", b"m_21530_"), ("removeWhenFarAway", b"m_6785_"),
                                          ("requiresCustomPersistence", b"m_8023_")) if pat in b]
        p(f"{name}: {', '.join(marks) if marks else 'none of them'}")
        if not marks:
            continue
        cls = name[:-6].replace("/", ".")
        r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
        text = r.stdout
        for method in ("m_6785_", "m_8023_"):
            at = text.find(" " + method + "(")
            if at >= 0:
                p("    " + " ".join(text[at:at + 260].split()))
        for m in re.finditer(r"this\.m_21530_\(\)|\.m_21530_\(\)", text):
            line_start = text.rfind("\n", 0, m.start())
            ctx = text[max(0, line_start - 300):m.end() + 40]
            p("    persistence: " + " ".join(ctx.split())[-340:])
            break

os.makedirs("probe-out", exist_ok=True)
open("probe-out/damned.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
