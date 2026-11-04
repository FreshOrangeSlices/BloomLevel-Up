"""Throwaway: (1) foods whose bad effects come from code (MCreator 'eaten' procedures, finishUsingItem overrides),
for Iron Stomach; (2) Easy Mob Spawn Control's rule matching and light checks, disassembled.
Usage: foodcheck.py <server.zip> <out dir>"""
import io, os, re, subprocess, sys, tempfile, zipfile

server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)

PROC = re.compile(r"(Eaten|FinishesUsing|FinishUsing|Consumed|FoodEaten|EatenBy|WhenEaten)", re.I)
EFFECT_REF = re.compile(r"Field (net/minecraft/world/effect/MobEffects\.\w+|[\w/]+(?:Effects?|MobEffects)\.\w+)")
food_lines = []
emsc_text = []


def javap(tmp, name, data, full=True):
    path = os.path.join(tmp, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)
    flags = ["-c", "-p", "-constants"] if full else ["-p", "-constants"]
    return subprocess.run(["javap", *flags, path], capture_output=True, text=True).stdout


for jar_name in sorted(n for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")):
    try:
        jar = zipfile.ZipFile(io.BytesIO(server.read(jar_name)))
    except zipfile.BadZipFile:
        continue
    with tempfile.TemporaryDirectory() as tmp:
        for n in jar.namelist():
            if not n.endswith(".class"):
                continue
            base = n.rsplit("/", 1)[-1]
            if "easy_mob_spawn_control" in jar_name and re.search(r"/Storage(\$SpawnRule)?\.class$", n):
                emsc_text.append(f"## {n}\n" + javap(tmp, n, jar.read(n))[:120000])
                continue
            if "Procedure" in base and PROC.search(base):
                data = jar.read(n)
                if b"addEffect" in data or b"m_7292_" in data or b"m_147207_" in data:
                    text = javap(tmp, n, data)
                    refs = sorted(set(EFFECT_REF.findall(text)))
                    food_lines.append(f"{jar_name}\t{n}\t{' '.join(refs)}")
            elif "/item/" in n and base.endswith("Item.class"):
                data = jar.read(n)
                # finishUsingItem (m_5922_) overridden and an effect added in the class or a procedure it calls
                if b"m_5922_" in data and (b"Procedure" in data or b"m_7292_" in data):
                    text = javap(tmp, n, data)
                    m = re.search(r"m_5922_\(.*?\n\n", text, re.S)
                    calls = sorted(set(re.findall(r"Method ([\w/$]+Procedure)\.execute", m.group(0) if m else "")))
                    refs = sorted(set(EFFECT_REF.findall(m.group(0) if m else "")))
                    food_props = "FoodProperties" in text or "m_41489_" in text
                    food_lines.append(f"{jar_name}\t{n}\tfood={food_props}\tcalls={' '.join(calls)}\tadds={' '.join(refs)}")

open(os.path.join(out, "foods.txt"), "w").write("\n".join(food_lines))
open(os.path.join(out, "emsc_storage.txt"), "w").write("\n".join(emsc_text))
summary = [f"{len(food_lines)} food lines, {len(emsc_text)} emsc classes"]
summary += [l for l in food_lines if "born_in_chaos" in l.lower()][:40]
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary))
print("\n".join(summary[:5]))
