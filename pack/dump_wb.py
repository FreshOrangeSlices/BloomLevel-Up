# World Bosses: which entities carry a boss bar, their max health, and the armor sets' numbers.
import os, re, subprocess, sys, zipfile
jar = sys.argv[1]
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
work = "wbdump"
os.makedirs(work, exist_ok=True)
zipfile.ZipFile(jar).extractall(work)
classes = [os.path.join(r, f) for r, _, fs in os.walk(work) for f in fs if f.endswith(".class")]
out = []
def javap(path, *flags):
    return subprocess.run(["javap", "-c", "-p", *flags, path], capture_output=True, text=True).stdout
for c in sorted(classes):
    data = open(c, "rb").read()
    rel = c[len(work) + 1:-6]
    boss = b"ServerBossEvent" in data
    attrs = b"createAttributes" in data or b"MAX_HEALTH" in data
    armor = b"ArmorMaterial" in data
    if not (boss or attrs or armor):
        continue
    text = javap(c)
    line = [rel + (" [boss bar]" if boss else "")]
    if attrs:
        # pairs of (attribute, number) inside the attribute builders
        tokens = re.findall(r"Field net/minecraft/world/entity/ai/attributes/Attributes\.(\w+)|// (?:double|float) (-?[\d.E]+)[dfDF]?|bipush\s+(-?\d+)|sipush\s+(-?\d+)", text)
        pairs, last = [], None
        for name, num, b, s in tokens:
            if name:
                last = name
            elif last and (num or b or s):
                pairs.append(f"{last}={num or b or s}")
                last = None
        if pairs:
            line.append("attrs: " + " ".join(pairs[:20]))
    if armor:
        clinit = text.split("static {};")[-1] if "static {};" in text else ""
        nums = re.findall(r"// (?:double|float|int) (-?[\d.E]+)|bipush\s+(-?\d+)|sipush\s+(-?\d+)|iconst_(\d)|String (\w+)|Field [\w/$]+\.(\w+):", clinit)
        flat = [next(x for x in t if x) for t in nums][:120]
        line.append("armor clinit: " + " ".join(flat))
    out.append(" | ".join(line))
text = "\n".join(out)
for i in range(0, len(text), 3500):
    print(f"::notice title=wb-{i//3500:02d}::{esc(text[i:i+3500])}")
