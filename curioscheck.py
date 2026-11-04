"""Throwaway: the pack's Curios slots and what fits each. Usage: curioscheck.py <server.zip> <out dir>"""
import io, json, os, re, sys, zipfile
server_zip, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
tags, slots, entities, names, slotnames, jarof = {}, {}, [], {}, {}, {}


def load(data):
    try:
        return json.loads(re.sub(rb"^\xef\xbb\xbf", b"", data).decode("utf-8", "replace"))
    except Exception:
        return None


for name in sorted(server.namelist()):
    if not (name.startswith("mods/") and name.endswith(".jar")):
        continue
    try:
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    except zipfile.BadZipFile:
        continue
    short = name[5:]
    for n in jar.namelist():
        m = re.match(r"data/curios/tags/items/(.+)\.json$", n)
        if m:
            j = load(jar.read(n)) or {}
            for v in j.get("values", []):
                vid = v["id"] if isinstance(v, dict) else v
                tags.setdefault(m.group(1), []).append((vid, short))
            continue
        m = re.match(r"data/([^/]+)/curios/slots/(.+)\.json$", n)
        if m:
            slots.setdefault(m.group(2), []).append((short, load(jar.read(n))))
            continue
        m = re.match(r"data/([^/]+)/curios/entities/(.+)\.json$", n)
        if m:
            entities.append((short, n, load(jar.read(n))))
            continue
        if re.match(r"assets/[^/]+/lang/en_us\.json$", n):
            j = load(jar.read(n)) or {}
            for k, v in j.items():
                if k.startswith("item.") or k.startswith("block."):
                    names[k] = v
                if k.startswith("curios.identifier."):
                    slotnames[k[len("curios.identifier."):]] = v
        m = re.match(r"data/([^/]+)/tags/items/(.+)\.json$", n)
        if m:
            j = load(jar.read(n)) or {}
            for v in j.get("values", []):
                vid = v["id"] if isinstance(v, dict) else v
                tags.setdefault("@" + m.group(1) + ":" + m.group(2), []).append((vid, short))


def resolve(ref, seen):
    if not ref.startswith("#"):
        return [ref]
    key = ref[1:]
    ns, path = key.split(":", 1)
    entries = tags.get(path, []) if ns == "curios" else tags.get("@" + key, [])
    outl = []
    for v, _ in entries:
        if v in seen:
            continue
        seen.add(v)
        outl += resolve(v, seen)
    return outl


def nice(i):
    ns, path = i.split(":", 1)
    return names.get(f"item.{ns}.{path}") or names.get(f"block.{ns}.{path}") or path


result = {}
for slot in sorted(k for k in tags if not k.startswith("@")):
    items = []
    for v, src in tags[slot]:
        for i in resolve(v, set()):
            items.append(i)
    items = sorted(set(items))
    result[slot] = [{"id": i, "name": nice(i)} for i in items]

summary = []
summary.append("SLOTS defined: " + ", ".join(sorted(slots)))
for short, n, j in entities:
    summary.append(f"ENTITY {short} {n}: {json.dumps(j)[:300]}")
summary.append("SLOT NAMES: " + json.dumps(slotnames)[:1500])
for slot, items in result.items():
    mods = sorted({i['id'].split(':')[0] for i in items})
    summary.append(f"{slot} ({len(items)}): " + "; ".join(f"{i['name']} [{i['id'].split(':')[0]}]" for i in items)[:900])
json.dump({"slots": {k: v for k, v in slots.items()}, "fits": result, "slotnames": slotnames}, open(os.path.join(out, "curios.json"), "w"), indent=1)
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary))
print("\n".join(summary[:3]))
