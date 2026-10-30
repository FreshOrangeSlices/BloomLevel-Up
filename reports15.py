"""Throwaway: the three new mob mods' gear for the trim: every weapon and armor piece of undead_unleashed,
born_in_chaos_v1 and ddenizens from the layer-6 dump (classes, damage, speed, own hooks, armor), its tooltip from the
client dump (plain and Shift), whether a recipe makes it and which loot tables name it, and what its recipe takes.
Usage: reports15.py <server.zip> <shots dir> <out dir>"""
import glob, io, json, os, re, sys, zipfile
server = zipfile.ZipFile(sys.argv[1]); SHOTS = sys.argv[2]; OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
NS = ("undead_unleashed", "born_in_chaos_v1", "ddenizens")
dump = next(iter(glob.glob(os.path.join(SHOTS, "**", "bloom-dump.jsonl"), recursive=True)))
tips_file = next(iter(glob.glob(os.path.join(SHOTS, "**", "tooltips.jsonl"), recursive=True)), None)
rows = [json.loads(l) for l in open(dump) if l.strip()]
tips = {}
if tips_file:
    for l in open(tips_file):
        if l.strip():
            t = json.loads(l); tips[t["id"]] = t
recipes = [r for r in rows if r.get("kind") == "recipe"]
made = {}
for r in recipes:
    made.setdefault(r.get("result"), []).append(r)
used_in = {}
for r in recipes:
    for i in r.get("ingredients", []):
        used_in.setdefault(i, []).append(r.get("result"))
loot = {}
for n in server.namelist():
    if not (n.endswith(".jar") and "/mods/" in "/" + n):
        continue
    try:
        z = zipfile.ZipFile(io.BytesIO(server.read(n)))
    except zipfile.BadZipFile:
        continue
    for x in z.namelist():
        if "/loot_tables/" in x and x.endswith(".json") and x.split("/")[1] in NS:
            text = z.read(x).decode("utf-8", "replace")
            for m in re.findall(r'"name"\s*:\s*"([a-z0-9_]+:[a-z0-9_/]+)"', text):
                if m.split(":")[0] in NS:
                    loot.setdefault(m, []).append(x.split("loot_tables/")[1][:-5])
FMT = re.compile(r"§.")
out = []
for r in rows:
    if r.get("kind") != "item" or r["id"].split(":")[0] not in NS:
        continue
    cls = r.get("classes", [])
    is_gear = "hooks" in r or any(c in cls for c in ("ArmorItem", "SwordItem", "AxeItem", "BowItem", "CrossbowItem", "TridentItem", "DiggerItem", "ShieldItem")) or r.get("armor")
    if not is_gear:
        continue
    t = tips.get(r["id"], {})
    out.append({"id": r["id"], "name": r.get("name"), "classes": cls[:4], "damage": r.get("damage"), "speed": r.get("speed"),
                "armor": r.get("armor"), "slot": r.get("slot"), "hooks": r.get("hooks"), "stats": r.get("stats"),
                "bettercombat": r.get("bettercombat"),
                "plain": [FMT.sub("", s) for s in t.get("plain", [])][1:12], "shift": [FMT.sub("", s) for s in t.get("shift", [])][1:14],
                "recipe": [{"type": x.get("type"), "takes": x.get("ingredients")} for x in made.get(r["id"], [])][:2],
                "loot": sorted(set(loot.get(r["id"], [])))[:6], "used_in": sorted(set(used_in.get(r["id"], [])))[:6]})
json.dump(out, open(os.path.join(OUT, "gear.json"), "w"), indent=1)
lines = [f"{len(out)} gear items from {dump}, tooltips {len(tips)}"]
for g in out:
    lines.append(f"\n{g['id']} | {g['name']} | {g['classes'][:2]} dmg {g['damage']} spd {g['speed']} armor {g['armor']} | hooks {sorted((g['hooks'] or {}).keys())}")
    lines.append(f"  recipe: {g['recipe']} | loot: {g['loot']} | used in: {g['used_in']}")
    lines.append("  tip: " + " / ".join(x for x in (g['shift'] or g['plain']) if x.strip())[:500])
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines)[:3000])
