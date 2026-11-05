"""Throwaway: what Paladins & Priests' spells cost, Spell Engine's config and casting hooks, Iron's mana API.
Usage: magiccheck.py <server.zip> <out dir>"""
import io, json, os, re, subprocess, sys, tempfile, zipfile
server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
summary, detail = [], []


def javap(tmp, jar, n, full=False):
    path = os.path.join(tmp, n)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(jar.read(n))
    flags = ["-c", "-p", "-constants"] if full else ["-p", "-constants"]
    return subprocess.run(["javap", *flags, path], capture_output=True, text=True).stdout


def scan(name, jar, depth=0):
    nested = [n for n in jar.namelist() if n.endswith(".jar")]
    if nested:
        summary.append(f"{'  '*depth}{name} nests: {', '.join(nested)}")
    for n in nested:
        try:
            scan(n, zipfile.ZipFile(io.BytesIO(jar.read(n))), depth + 1)
        except zipfile.BadZipFile:
            pass
    with tempfile.TemporaryDirectory() as tmp:
        for n in jar.namelist():
            low = n.lower()
            if re.search(r"data/[^/]+/spells/.+\.json$", n) and ("paladin" in name or "rogue" in name or "spell_engine" in name):
                try:
                    j = json.loads(jar.read(n))
                except Exception:
                    continue
                cost = j.get("cost", {})
                summary.append(f"SPELL {n}: school={j.get('school')} cost={json.dumps(cost)[:200]}")
            elif "spell_engine" in name and n.endswith(".class") and re.search(r"(ServerConfig|SpellHelper|SpellCast|config/.*Config)\.class$", n):
                detail.append(f"## {name} {n}\n" + javap(tmp, jar, n)[:20000])
            elif "irons_spellbooks" in name and n.endswith(".class") and re.search(r"capabilities/magic/MagicData\.class$|ClientMagicData\.class$|ServerConfigs\.class$", n):
                detail.append(f"## {name} {n}\n" + javap(tmp, jar, n)[:20000])
            elif re.search(r"(paladins|runes|spell_engine)[^/]*/lang/en_us\.json$", n) or re.search(r"assets/(paladins|runes)/lang/en_us\.json$", n):
                j = json.loads(jar.read(n))
                summary.append(f"LANG {n}: " + "; ".join(f"{k}={v}" for k, v in j.items() if "rune" in k or "rune" in str(v).lower())[:800])


for name in sorted(server.namelist()):
    if name.startswith("mods/") and name.endswith(".jar") and re.search(r"paladins|rogues|spell_engine|spell-engine|irons_spellbooks|runes", name, re.I):
        summary.append(f"JAR {name}")
        scan(name, zipfile.ZipFile(io.BytesIO(server.read(name))))
for name in server.namelist():
    if re.search(r"config/.*(spell_engine|paladins|runes)", name, re.I):
        summary.append(f"CONFIG {name}: " + server.read(name).decode("utf-8", "replace")[:1500].replace("\n", " "))
open(os.path.join(out, "detail.txt"), "w").write("\n".join(detail))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary))
print(len(summary), len(detail))
