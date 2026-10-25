# The resource packs in the pack: what each one replaces (models, animations, textures), and where two of them
# replace the same file (the one higher in the list wins the whole file).
import io, json, re, sys, zipfile, urllib.request, collections
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
def note(title, text):
    for i in range(0, min(len(text), 3800 * 4), 3800):
        print(f"::notice title={title}-{i//3800}::{esc(text[i:i+3800])}")
mr = zipfile.ZipFile(sys.argv[1])
index = json.loads(mr.read("modrinth.index.json"))
owners = collections.defaultdict(list)
for f in index["files"]:
    if not f["path"].startswith("resourcepacks/"):
        continue
    data = urllib.request.urlopen(urllib.request.Request(f["downloads"][0], headers={"User-Agent": "bloom ci"}), timeout=60).read()
    z = zipfile.ZipFile(io.BytesIO(data))
    name = f["path"].split("/")[-1]
    files = [n for n in z.namelist() if not n.endswith("/")]
    # ignore version-overlay folders' duplicates by normalising them to their asset path
    models = sorted(set(re.sub(r"^.*?(assets/)", r"\1", n) for n in files if n.endswith((".jem", ".jpm", ".properties")) and "/cem/" in n))
    textures = sorted(set(re.sub(r"^.*?(assets/)", r"\1", n) for n in files if n.endswith(".png") and "/textures/entity/" in n))
    other = sorted(set(re.sub(r"^.*?(assets/)", r"\1", n) for n in files if "/optifine/random/" in n or "/etf/" in n or "/emissive" in n))
    for path in models + textures:
        owners[path].append(name)
    short = lambda xs: ", ".join(x.split("/")[-1] for x in xs)
    note("rp-" + name[:28], f"{name}\nmodels ({len(models)}): {short(models)[:2500]}\ntextures ({len(textures)}): {short(textures)[:600]}\nrandom/etf ({len(other)}): {short(other)[:300]}")
    if "Player" in name or "Expressions" in name:
        for n in files:
            if n.endswith((".jem", ".jpm")) and "player" in n:
                text = z.read(n).decode("utf-8", "replace")
                refs = sorted(set(re.findall(r"(?:face|express|blink|eye|lid|pupil|brow|mouth)[a-z_0-9]*", text, re.I)))
                if refs:
                    note("rp-refs-" + name[:12] + "-" + n.split("/")[-1][:20], ", ".join(refs)[:1500])
clashes = [f"{p.split('/')[-1]}: {', '.join(o)}" for p, o in sorted(owners.items()) if len(o) > 1]
note("rp-clashes", "\n".join(clashes) or "none")
