"""Curios in the pack: every slot each mod defines, the slots players get, and the items each slot takes."""
import json, os, re, subprocess, urllib.request, zipfile
from collections import defaultdict

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (curios slots)"}


def get(urls):
    for url in urls:
        for _ in range(3):
            try:
                return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
            except Exception:
                pass
    return None


def flat(text):
    return " ".join(text.split())


jars = json.load(open("probe/alljars.json"))
out = []
p = out.append
slots = defaultdict(list)       # slot -> [(mod, file, json)]
entities = []                   # (mod, file, json)
tags = defaultdict(list)        # slot -> [(mod, values, replace)]
imc = []                        # (mod, cls, line)
curio_classes = defaultdict(list)
register_calls = []
missing = []
for mod, urls in jars.items():
    data = get(urls if isinstance(urls, list) else [urls])
    if not data:
        missing.append(mod)
        continue
    path = "jar.jar"
    open(path, "wb").write(data)
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        missing.append(mod + " (bad zip)")
        continue
    names = z.namelist()
    for n in names:
        m = re.match(r"data/([^/]+)/curios/slots/(.+)\.json$", n)
        if m:
            slots[m.group(2)].append((mod, n, flat(z.read(n).decode("utf-8", "replace"))))
        m = re.match(r"data/([^/]+)/curios/entities/(.+)\.json$", n)
        if m:
            entities.append((mod, n, flat(z.read(n).decode("utf-8", "replace"))))
        m = re.match(r"data/curios/tags/items/(.+)\.json$", n)
        if m:
            try:
                j = json.loads(z.read(n).decode("utf-8", "replace"))
                vals = [v if isinstance(v, str) else v.get("id", str(v)) for v in j.get("values", [])]
                tags[m.group(1)].append((mod, vals, j.get("replace", False)))
            except Exception as e:
                tags[m.group(1)].append((mod, ["<unreadable: %s>" % e], False))
    hits = []
    for n in names:
        if not n.endswith(".class"):
            continue
        b = z.read(n)
        if b"top/theillusivec4/curios/api/type/capability/ICurio" in b:
            curio_classes[mod].append(n[:-6].replace("/", "."))
        if b"SlotTypeMessage" in b or b"registerCurio" in b or b"SlotTypePreset" in b:
            hits.append(n[:-6].replace("/", "."))
    if mod == "curios":
        continue
    for cls in hits[:12]:
        try:
            r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls.split("$")[0]) + "$"],
                               capture_output=True, text=True, timeout=300)
        except Exception:
            continue
        for line in r.stdout.splitlines():
            if re.search(r"SlotTypeMessage|SlotTypePreset|registerCurio", line):
                (register_calls if "registerCurio" in line else imc).append((mod, cls, flat(line)[:300]))

p("==== Slot definitions (data/*/curios/slots)")
for slot in sorted(slots):
    for mod, n, j in slots[slot]:
        p(f"{slot} | {mod} | {n} | {j[:400]}")
p("")
p("==== Slots given to entities (data/*/curios/entities)")
for mod, n, j in entities:
    p(f"{mod} | {n} | {j[:600]}")
p("")
p("==== Slots registered in code (IMC)")
for mod, cls, line in imc:
    p(f"{mod} | {cls} | {line}")
p("")
p("==== Items per slot tag (data/curios/tags/items)")
for slot in sorted(tags):
    total = sum(len(v) for _, v, _ in tags[slot])
    p(f"-- {slot}: {total} entries")
    for mod, vals, rep in tags[slot]:
        p(f"   {mod}{' (replace)' if rep else ''}: {', '.join(vals)}")
p("")
p("==== Curio item classes in code (ICurioItem / ICurio), per mod")
for mod in sorted(curio_classes):
    cl = curio_classes[mod]
    p(f"{mod}: {len(cl)}: " + ", ".join(c.split('.')[-1] for c in cl[:60]))
p("")
p("==== registerCurio calls")
for mod, cls, line in register_calls:
    p(f"{mod} | {cls} | {line}")
p("")
p("==== Jars not read: " + ", ".join(missing))

os.makedirs("probe-out", exist_ok=True)
open("probe-out/curios.txt", "w").write("\n".join(out))
open("report.txt", "w").write("\n".join(out))
