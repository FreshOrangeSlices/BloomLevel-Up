"""Why doesn't the Charcraw's recipe show? Monster Expansion's Foundry recipes and its JEI support (or none)."""
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
for slug in ["monster-expansion", "jei"]:
    if slug not in jars:
        p(f"{slug}: not in the pack list")
        continue
    data = get(jars[slug])
    if not data:
        p(f"{slug}: download failed")
        continue
    path = slug + ".jar"
    open(path, "wb").write(data)
    z = zipfile.ZipFile(path)
    names = z.namelist()
    if slug == "jei":
        p(f"jei: {len(names)} files, version in mods.toml: " + re.sub(r"\s+", " ", z.read("META-INF/mods.toml").decode()[:600]))
        continue
    p(f"==== {slug}: {len(names)} files")
    p("mods.toml: " + re.sub(r"\s+", " ", z.read("META-INF/mods.toml").decode("utf-8", "replace"))[:2500])
    jei = [n for n in names if re.search(r"(?i)jei|emi|rei/|recipeviewer|compat", n)]
    p(f"files naming jei/emi/rei/compat: {jei[:60]}")
    refs = []
    for n in names:
        if n.endswith(".class"):
            b = z.read(n)
            if b"mezz/jei" in b or b"JeiPlugin" in b or b"IModPlugin" in b:
                refs.append(n)
    p(f"classes using JEI's API: {refs}")
    recipes = [n for n in names if n.startswith("data/") and "/recipes/" in n and n.endswith(".json")]
    foundry = []
    for n in recipes:
        text = z.read(n).decode("utf-8", "replace")
        if "foundry" in text:
            foundry.append(n)
    p(f"{len(recipes)} recipes, {len(foundry)} of them Foundry recipes: {[os.path.basename(f) for f in foundry]}")
    for n in names:
        if re.search(r"(?i)charcraw", n) and n.endswith(".json"):
            p(f"-- {n}: " + re.sub(r"\s+", " ", z.read(n).decode("utf-8", "replace"))[:1500])
    for n in foundry[:2]:
        if "charcraw" not in n:
            p(f"-- {n}: " + re.sub(r"\s+", " ", z.read(n).decode("utf-8", "replace"))[:1200])
    # The Foundry's own recipe type and serializer, and anything about a recipe book or unlocking.
    for n in names:
        if n.endswith(".class") and re.search(r"(?i)foundry", n):
            p(f"class {n}")
    for n in names:
        if n.endswith(".class") and re.search(r"(?i)foundry.*(recipe|serializer)|recipe.*foundry", n):
            r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(n[:-6].replace("/", ".")) + "$"],
                               capture_output=True, text=True)
            lines = [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(" *") and not l.startswith("import ") and not l.startswith("/*")]
            p(f"---- {n}\n" + "\n".join(l[:220] for l in lines)[:3500])
    lang = json.loads(z.read("assets/monsterexpansion/lang/en_us.json").decode("utf-8", "replace"))
    for k, v in lang.items():
        if re.search(r"(?i)foundry|jei|hunter", k):
            p(f"lang {k} = {v}")

os.makedirs("probe-out", exist_ok=True)
open("probe-out/charcraw.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
