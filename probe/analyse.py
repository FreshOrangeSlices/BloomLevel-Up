"""The book slots: Iron's spellbook slot and Spell Engine's spell book slot on Forge (Curios), what they accept, and how
each mod reads the item in its slot (does a non-book item there break anything?)."""
import json, os, re, subprocess, urllib.request, zipfile

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe (book slots)"}


def get(url):
    for _ in range(3):
        try:
            return urllib.request.urlopen(urllib.request.Request(url.replace(" ", "%20"), headers=UA), timeout=240).read()
        except Exception:
            pass
    return None


out = []
p = out.append
jars = json.load(open("probe/alljars.json"))
for slug in ("irons-spells-n-spellbooks", "spell-engine"):
    urls = jars[slug]
    data = None
    for u in (urls if isinstance(urls, list) else [urls]):
        data = get(u)
        if data:
            break
    path = slug + ".jar"
    open(path, "wb").write(data)
    z = zipfile.ZipFile(path)
    p(f"==== {slug}")
    for n in sorted(z.namelist()):
        if n.startswith("data/curios/") or (n.startswith("data/") and "/curios/" in n):
            p(f"  {n}: " + " ".join(z.read(n).decode("utf-8", "replace").split())[:500])
    # Code that reads the book slot.
    for n in sorted(z.namelist()):
        if not n.endswith(".class"):
            continue
        b = z.read(n)
        if b"top/theillusivec4/curios" in b and (b"spellbook" in b.lower() or b"spell_book" in b.lower()):
            cls = n[:-6].replace("/", ".")
            r = subprocess.run(["java", "-jar", "cfr.jar", path, "--jarfilter", "^" + re.escape(cls) + "$"], capture_output=True, text=True)
            text = r.stdout
            p(f"  -- {cls}")
            for m in re.finditer(r"(?i)(spell_?book|getCuriosInventory|findFirstCurio|getStacksHandler|instanceof \w*(SpellBook|Book)\w*)", text):
                start = text.rfind("\n", 0, m.start())
                line = " ".join(text[start:text.find("\n", m.end())].split())
                if line:
                    p("     " + line[:260])

os.makedirs("probe-out", exist_ok=True)
open("probe-out/bookslots.txt", "w").write("\n".join(out))
open("report.txt", "w").write("done")
