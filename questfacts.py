"""Throwaway: facts for the LegionWareX quest book, from the mods in a pack's server zip.

For each mod: its advancements (title, description, parent, what triggers them: items, entities, structures, biomes),
its guide books (Patchouli entries' text), and the names of its items, blocks and entities from its English lang file.
Usage: questfacts.py <server.zip> <out dir>"""
import io, json, os, re, sys, zipfile

server = zipfile.ZipFile(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)


def loose_json(data):
    text = data.decode("utf-8", "replace")
    try:
        return json.loads(text)
    except Exception:
        # Some lang files carry comments or trailing commas.
        text = re.sub(r"^\s*//.*$", "", text, flags=re.M)
        text = re.sub(r",\s*([}\]])", r"\1", text)
        try:
            return json.loads(text)
        except Exception:
            return None


def text_of(component, lang):
    if component is None:
        return ""
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(text_of(c, lang) for c in component)
    if isinstance(component, dict):
        if "translate" in component:
            return lang.get(component["translate"], component.get("fallback", component["translate"]))
        return component.get("text", "") + "".join(text_of(c, lang) for c in component.get("extra", []))
    return str(component)


def walk_ids(obj, found):
    """Ids an advancement's criteria name (items, entities, structures, biomes, dimensions, effects, recipes)."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("items", "item", "type", "structure", "biome", "dimension", "entity", "effects", "recipe_id", "recipe", "block", "tag"):
                if isinstance(v, str) and ":" in v:
                    found.add(f"{k}={v}")
                elif isinstance(v, list):
                    for x in v:
                        if isinstance(x, str) and ":" in x:
                            found.add(f"{k}={x}")
            walk_ids(v, found)
    elif isinstance(obj, list):
        for v in obj:
            walk_ids(v, found)


jars = {}
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar"):
        jars[name.rsplit("/", 1)[-1]] = server.read(name)

summary = []
for jar_name, data in sorted(jars.items()):
    try:
        jar = zipfile.ZipFile(io.BytesIO(data))
    except Exception as e:
        summary.append(f"{jar_name}: unreadable ({e})")
        continue
    names = jar.namelist()
    # Nested jars (jar-in-jar) are other mods' libraries: skipped.
    namespaces = sorted({n.split("/")[1] for n in names if n.startswith("data/") and n.count("/") >= 2}
                        | {n.split("/")[1] for n in names if n.startswith("assets/") and n.count("/") >= 2})
    lang = {}
    for n in names:
        if re.match(r"assets/[^/]+/lang/en_us\.json$", n):
            parsed = loose_json(jar.read(n))
            if isinstance(parsed, dict):
                lang.update(parsed)
    report = {"jar": jar_name, "namespaces": namespaces, "advancements": [], "books": [], "names": {}}
    for n in names:
        m = re.match(r"data/([^/]+)/advancements?/(.+)\.json$", n)
        if not m or m.group(1) == "minecraft" and not jar_name.startswith("minecraft"):
            continue
        adv = loose_json(jar.read(n))
        if not isinstance(adv, dict):
            continue
        display = adv.get("display") or {}
        ids = set()
        walk_ids(adv.get("criteria", {}), ids)
        triggers = sorted({c.get("trigger", "") for c in (adv.get("criteria") or {}).values() if isinstance(c, dict)})
        icon = display.get("icon", {})
        report["advancements"].append({
            "id": f"{m.group(1)}:{m.group(2)}",
            "parent": adv.get("parent", ""),
            "title": text_of(display.get("title"), lang),
            "description": text_of(display.get("description"), lang),
            "icon": icon.get("item", "") if isinstance(icon, dict) else "",
            "frame": display.get("frame", ""),
            "hidden": display.get("hidden", False),
            "triggers": triggers,
            "ids": sorted(ids),
            "rewards": adv.get("rewards", {}),
        })
    # Patchouli books and other in-game guides: the text of each entry.
    for n in names:
        if re.search(r"/patchouli_books/.+/en_us/entries/.+\.json$", n) or re.search(r"/(guide|guidebook|book)s?/.+\.(json|md)$", n):
            raw = jar.read(n)
            if n.endswith(".md"):
                report["books"].append({"file": n, "text": raw.decode("utf-8", "replace")[:4000]})
                continue
            entry = loose_json(raw)
            if isinstance(entry, dict):
                pages = []
                for p in entry.get("pages", []):
                    if isinstance(p, dict):
                        pages.append({k: p.get(k) for k in ("type", "title", "text", "item", "recipe", "entity") if p.get(k)})
                report["books"].append({"file": n, "name": text_of(entry.get("name"), lang), "category": entry.get("category", ""),
                                        "icon": entry.get("icon", ""), "pages": pages})
    for key, value in lang.items():
        if re.match(r"(item|block|entity|biome|effect|enchantment|structure)\.[a-z0-9_]+\.[a-z0-9_./]+$", key):
            report["names"][key] = value
    ns = "_".join(namespaces[:2]) or "none"
    with open(os.path.join(out, re.sub(r"[^A-Za-z0-9_.-]", "_", jar_name) + ".json"), "w") as f:
        json.dump(report, f, indent=1, ensure_ascii=False)
    summary.append(f"{jar_name}: {len(report['advancements'])} advancements, {len(report['books'])} book entries, {len(report['names'])} names ({', '.join(namespaces[:6])})")

with open(os.path.join(out, "summary.txt"), "w") as f:
    f.write("\n".join(summary) + "\n")
print("\n".join(summary))
