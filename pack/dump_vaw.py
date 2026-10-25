# Villagers at Work / after Work: which game classes their mixins change (client ones especially) and what they say about poses.
import io, json, re, sys, zipfile
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
def note(title, text):
    for i in range(0, len(text), 3800):
        print(f"::notice title={title}-{i//3800}::{esc(text[i:i+3800])}")
def walk(data, label, out):
    z = zipfile.ZipFile(io.BytesIO(data))
    names = z.namelist()
    if "fabric.mod.json" in names:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
        out.append(f"## {label}: id={meta.get('id')} env={meta.get('environment')} mixins={meta.get('mixins')} entry={list((meta.get('entrypoints') or {}).keys())}")
        out.append("   description: " + str(meta.get("description"))[:300])
    for n in names:
        if n.endswith(".mixins.json") or (n.endswith(".json") and "mixin" in n.lower() and "/" not in n):
            try:
                cfg = json.loads(z.read(n).decode("utf-8", "replace"), strict=False)
            except ValueError:
                continue
            pkg = cfg.get("package", "")
            for key in ("mixins", "client", "server"):
                for m in cfg.get(key, []) or []:
                    cls = (pkg + "." + m).replace(".", "/") + ".class"
                    target = ""
                    if cls in names:
                        raw = z.read(cls)
                        found = re.findall(rb"Lnet/minecraft/[A-Za-z0-9_/$]+;", raw)
                        target = ", ".join(sorted(set(f.decode()[1:-1].split("/")[-1] for f in found)))[:200]
                    out.append(f"   [{key}] {m} -> {target}")
    for n in names:
        if n.endswith(".jar"):
            walk(z.read(n), label + " > " + n.split("/")[-1], out)
    poses = sorted(set(n for n in names if re.search(r"(pose|anim|sit|model)", n, re.I) and n.endswith(".class")))
    if poses:
        out.append("   classes: " + ", ".join(p.split("/")[-1][:-6] for p in poses)[:1500])
mr = zipfile.ZipFile(sys.argv[1])
out = []
for n in mr.namelist():
    if re.search(r"overrides/mods/vaw", n):
        walk(mr.read(n), n.split("/")[-1], out)
note("vaw", "\n".join(out))
