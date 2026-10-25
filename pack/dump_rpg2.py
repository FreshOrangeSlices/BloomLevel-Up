# The RPG Series in numbers, for Bloom's tags: armor sets and weapons from the configs the server wrote, the tags the
# new jars add (weapon/armor types, vanilla and c: tags, damage type tags), their damage types and attributes.
# Usage: dump_rpg2.py <section> <new jars dir> <server dir>
import json, os, re, sys, zipfile
section, jars_dir, server = sys.argv[1:4]
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
def emit(title, text):
    for i in range(0, min(len(text), 3700 * 9), 3700):
        print(f"::notice title={title}-{i // 3700}::{esc(text[i:i + 3700])}")
jars = [os.path.join(jars_dir, f) for f in sorted(os.listdir(jars_dir)) if f.endswith(".jar") and "bloomlevelup" not in f and "fabric-api" not in f]
def entries(pattern):
    for j in jars:
        z = zipfile.ZipFile(j)
        for n in z.namelist():
            if re.search(pattern, n):
                yield os.path.basename(j), n, z.read(n)
def values(raw):
    try:
        d = json.loads(raw.decode("utf-8", "replace"), strict=False)
    except Exception:
        return ["?"]
    return [v if isinstance(v, str) else v.get("id", "?") + ("?" if not v.get("required", True) else "") for v in d.get("values", [])]
short = lambda s: re.sub(r"^(wizards|paladins|archers|rogues|arsenal|armory_rpgs|jewelry|spell_engine|rpg_series):", "", s)
out = []
def summarize(rel, d):
    for name, a in (d.get("armor_sets") or {}).items():
        pieces = "/".join(str((a.get(p) or {}).get("armor", "?")) for p in ("head", "chest", "legs", "feet"))
        attrs = sorted({str(m.get("attribute", m.get("id", "?"))).split(":")[-1] for p in ("head", "chest", "legs", "feet") for m in ((a.get(p) or {}).get("attributes") or [])})
        out.append(f"{rel} armor {name}: {pieces} t{a.get('armor_toughness')} kb{a.get('knockback_resistance')} [{','.join(attrs)}]")
    for name, w in (d.get("weapons") or {}).items():
        attrs = ",".join(f"{str(m.get('attribute', m.get('id','?'))).split(':')[-1]}{m.get('value')}" for m in (w.get("attributes") or []))
        out.append(f"{rel} weapon {name}: dmg{w.get('attack_damage')} spd{w.get('attack_speed')} [{attrs}]")
    for key in ("ranged_weapons", "shields"):
        for name, w in (d.get(key) or {}).items():
            out.append(f"{rel} {key[:-1]} {name}: {json.dumps(w)[:160]}")
    if not any(k in d for k in ("armor_sets", "weapons", "ranged_weapons", "shields")):
        out.append(f"{rel}: keys {list(d)[:12]}")
if section == "configs":
    for root, _, files in os.walk(os.path.join(server, "config")):
        for f in sorted(files):
            if not f.endswith(".json"):
                continue
            path = os.path.join(root, f)
            try:
                d = json.load(open(path))
            except Exception:
                continue
            if not isinstance(d, dict):
                continue
            rel = os.path.relpath(path, os.path.join(server, "config"))
            try:
                summarize(rel, d)
            except Exception as e:
                out.append(f"{rel}: {type(e).__name__} {e}; keys {list(d)[:8]}")
    emit("cfg", "\n".join(out))
elif section == "tags":
    for jar, n, raw in entries(r"^data/(rpg_series/tags/item/(weapon_type|armor_type)|c/tags/item|minecraft/tags/item/(swords|axes|spears|maces|pickaxes|shovels|hoes|bow|crossbow|arrows|trident))"):
        out.append(f"{jar[:14]} {n[5:-5]}: {' '.join(map(short, values(raw)))}")
    emit("tags", "\n".join(out))
elif section == "damage":
    for jar, n, raw in entries(r"^data/[^/]+/damage_type/[^/]+\.json$"):
        out.append(f"{jar[:14]} {n[5:-5]}: {raw.decode()[:120]}")
    for jar, n, raw in entries(r"^data/[^/]+/tags/damage_type/"):
        out.append(f"{jar[:14]} {n[5:-5]}: {' '.join(values(raw))}")
    for jar, n, raw in entries(r"^data/[^/]+/tags/entity_type/"):
        out.append(f"{jar[:14]} {n[5:-5]}: {' '.join(values(raw))[:300]}")
    emit("dmg", "\n".join(out))
elif section == "lang":
    for jar, n, raw in entries(r"^assets/[^/]+/lang/en_us\.json$"):
        try:
            lang = json.loads(raw.decode("utf-8", "replace"), strict=False)
        except Exception:
            continue
        attrs = [k for k in lang if k.startswith("attribute.")]
        keys = [f"{k}={v}" for k, v in lang.items() if k.startswith(("key", "keybind")) and "category" not in k]
        items = [k.split(".", 2)[2] for k in lang if re.match(r"item\.[^.]+\.[^.]+$", k)]
        prof = [k for k in lang if k.startswith("entity.minecraft.villager.")]
        ns = n.split('/')[1]
        out.append(f"{ns}: attrs {[a.split('.')[-1] for a in attrs]} keys {keys} jobs {[p.split('.')[-1] for p in prof]}")
    emit("lang", "\n".join(out))
