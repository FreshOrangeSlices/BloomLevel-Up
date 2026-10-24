import json, sys, zipfile, re
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    names = z.namelist()
    out = [f"== {path}"]
    if "fabric.mod.json" in names:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
        out.append(f"id={meta.get('id')} env={meta.get('environment')} depends={meta.get('depends')}")
    tags = [n for n in names if re.search(r"data/[^/]+/tags/(entity_type|item|items)/", n)]
    out.append("tags: " + ", ".join(tags[:60]))
    for n in tags:
        if "boss" in n or "armor" in n:
            out.append(f"{n}: {z.read(n).decode('utf-8','replace')[:400]}")
    for n in names:
        if re.match(r"assets/[^/]+/lang/en_us\.json$", n):
            lang = json.loads(z.read(n).decode("utf-8", "replace"), strict=False)
            ents = [f"{k}={v}" for k, v in lang.items() if k.startswith("entity.") and k.count(".") == 2]
            arm = [f"{k}={v}" for k, v in lang.items() if k.startswith("item.") and re.search(r"helmet|chestplate|leggings|boots|armor|_hat|_cap|_robe|_crown", k)]
            out.append("entities: " + "; ".join(ents[:80]))
            out.append("armor items: " + "; ".join(arm[:80]))
    # armor values: item models / equipment assets
    eq = [n for n in names if "equipment" in n and n.endswith(".json")]
    out.append("equipment assets: " + ", ".join(eq[:40]))
    text = "\n".join(out)
    for i in range(0, len(text), 3800):
        print(f"::notice title=jar-{path.split('/')[-1][:30]}-{i//3800}::{esc(text[i:i+3800])}")
