"""The LegionWareX quest book: pack/quests/*.json written out as FTB Quests' own files (config/ftbquests/quests/).

One JSON file per chapter (its file name sorts the chapters). A chapter, quest, task or reward that names a mod the
pack (or a layer of it) leaves out is skipped, so every layer gets a book that only shows what it has. The ids FTB
Quests needs are made from the chapter's and quest's own ids, so they stay the same from build to build (a player's
progress is kept by id).

Chapter file:
  {"id": "magic", "title": "...", "subtitle": ["..."], "icon": "ns:item", "group": "guides" (see GROUPS),
   "mods": ["ns", ...] (the chapter needs all of them), "progression_mode": "flexible", "quests": [quest, ...]}
  progression_mode (a chapter's or a quest's own): "linear", the book's default, keeps a quest's tasks shut until its
  dependencies are done; "flexible" lets a player do any quest at any time, and one done early (a grey tick) pays out
  once the quests before it are done. The guide chapters are flexible: their lines suggest an order, nothing locks
  (so their quests get no lock icon).
Quest:
  {"id": "inscription_table", "title": "...", "subtitle": "...", "icon": "ns:item", "x": 0, "y": 0,
   "shape": "circle|square|rsquare|diamond|pentagon|hexagon|octagon|heart|gear", "size": 1.0,
   "description": ["line", "", "line"], "deps": ["quest id" or "chapter/quest id"], "min_deps": 0,
   "optional": false, "progression_mode": "flexible", "mods": [...], "tasks": [task, ...], "rewards": [reward, ...]}
Tasks:
  {"type": "item", "item": "ns:id", "count": 1}       held in the inventory (never taken)
  {"type": "kill", "entity": "ns:id", "count": 1}
  {"type": "structure", "structure": "ns:id" or "#ns:tag", "optional": true}
  {"type": "biome", "biome": "ns:id" or "#ns:tag"}
  {"type": "dimension", "dimension": "ns:id"}
  {"type": "advancement", "advancement": "ns:path"}
  {"type": "checkmark", "title": "..."}
  Any task may have "title" and "optional".
Rewards:
  {"type": "xp_levels", "levels": 5}                   vanilla levels (spend them on enchanting, or on Bloom in the hub)
  {"type": "xp", "xp": 100}                            vanilla experience points
  {"type": "bloom_xp", "tab": "combat|armor|utility|misc", "xp": 300}
  {"type": "major_levels", "count": 1}                 Bloom Major Levels
  {"type": "item", "item": "ns:id", "count": 4}
"""
import hashlib
import json
import os
import re

GROUPS = {"guides": "Guides", "bosses": "Bosses"}
TABS = {"combat": "Combat", "armor": "Armor", "utility": "Utility", "misc": "Misc"}
ALWAYS = {"minecraft", "forge", "bloomlevelup", "legionwarex", "ftbquests"}
SHAPES = {"circle", "square", "rsquare", "diamond", "pentagon", "hexagon", "octagon", "heart", "gear", "none"}
MODES = {"default", "linear", "flexible"}


def fid(*parts):
    """A stable FTB Quests id (16 hex digits, a positive long) from the object's own path in the book."""
    value = int(hashlib.sha1(("legionwarex/" + "/".join(parts)).encode()).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF
    return f"{max(value, 2):016X}"


# ---- SNBT, as FTB Library reads it (one key per line, no commas)

def snbt(value, indent=0):
    pad, inner = "\t" * indent, "\t" * (indent + 1)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, Long):
        return f"{int(value)}L"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return f"{value}d"
    if isinstance(value, str):
        return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'
    if isinstance(value, list):
        if not value:
            return "[ ]"
        return "[\n" + "\n".join(inner + snbt(v, indent + 1) for v in value) + "\n" + pad + "]"
    if isinstance(value, dict):
        if not value:
            return "{ }"
        lines = []
        for key in sorted(value):
            name = key if re.fullmatch(r"[A-Za-z0-9_\-.+]+", key) else snbt(key)
            lines.append(f"{inner}{name}: {snbt(value[key], indent + 1)}")
        return "{\n" + "\n".join(lines) + "\n" + pad + "}"
    raise TypeError(f"no SNBT for {value!r}")


class Long(int):
    pass


# ---- reading the book

def chapters(quest_dir):
    if not os.path.isdir(quest_dir):
        return
    for name in sorted(os.listdir(quest_dir)):
        if name.endswith(".json"):
            with open(os.path.join(quest_dir, name), encoding="utf-8") as f:
                yield name, json.load(f)


def namespace(resource):
    resource = resource.lstrip("#")
    return resource.split(":", 1)[0] if ":" in resource else "minecraft"


def needs(obj):
    """The mods (namespaces) a task, reward or quest names."""
    found = set(obj.get("mods", []))
    for key in ("item", "entity", "structure", "biome", "dimension", "advancement", "icon"):
        if isinstance(obj.get(key), str) and obj[key]:
            found.add(namespace(obj[key]))
    return found - ALWAYS


def kept(obj, present):
    return needs(obj) <= present


def ids_used(quest_dir, present):
    """Every item, entity, structure and biome the layer's book names, for the pack test to look up."""
    used = {"item": set(), "entity": set(), "structure": set(), "biome": set(), "advancement": set()}
    for _, chapter in chapters(quest_dir):
        if not set(chapter.get("mods", [])) <= present:
            continue
        if chapter.get("icon"):
            used["item"].add(chapter["icon"])
        for quest in chapter["quests"]:
            if not kept(quest, present):
                continue
            if quest.get("icon"):
                used["item"].add(quest["icon"])
            for task in quest.get("tasks", []):
                if kept(task, present):
                    for key in used:
                        if task.get(key) and not task[key].startswith("#"):
                            used[key].add(task[key])
            for reward in quest.get("rewards", []):
                if kept(reward, present) and reward.get("item"):
                    used["item"].add(reward["item"])
    return used


def text(value):
    """Words as FTB Quests shows them: '&' starts a colour code there ('&a'), so a plain '&' is written '\\&'."""
    if isinstance(value, list):
        return [text(v) for v in value]
    return value.replace("\\&", "&").replace("&", "\\&")


def task_data(chapter_id, quest_id, n, task):
    kind = task["type"]
    out = {"id": fid(chapter_id, quest_id, "task", str(n)), "type": kind}
    if task.get("title"):
        out["title"] = text(task["title"])
    if task.get("optional"):
        out["optional_task"] = True
    if kind == "item":
        out["item"] = task["item"]
        if task.get("count", 1) > 1:
            out["count"] = Long(task["count"])
        out["consume_items"] = False
    elif kind == "kill":
        out["entity"] = task["entity"]
        out["value"] = Long(task.get("count", 1))
    elif kind in ("structure", "biome", "dimension", "advancement"):
        out[kind] = task[kind]
        if kind == "advancement":
            out["criterion"] = task.get("criterion", "")
    elif kind != "checkmark":
        raise ValueError(f"{chapter_id}/{quest_id}: unknown task type {kind}")
    return out


def reward_data(chapter_id, quest_id, n, reward):
    kind = reward["type"]
    out = {"id": fid(chapter_id, quest_id, "reward", str(n))}
    if kind == "xp_levels":
        out.update(type="xp_levels", xp_levels=int(reward["levels"]))
    elif kind == "xp":
        out.update(type="xp", xp=int(reward["xp"]))
    elif kind == "item":
        out.update(type="item", item=reward["item"])
        if reward.get("count", 1) > 1:
            out["count"] = int(reward["count"])
    elif kind == "bloom_xp":
        tab = reward["tab"]
        out.update(type="command", command=f"/bloom admin xp {{p}} bloomlevelup:{tab} {int(reward['xp'])}", elevate_perms=True,
                   silent=True, title=f"{int(reward['xp'])} {TABS[tab]} XP (Bloom)", icon="minecraft:experience_bottle")
    elif kind == "major_levels":
        count = int(reward.get("count", 1))
        out.update(type="command", command=f"/bloom admin majorlevels {{p}} {count}", elevate_perms=True, silent=True,
                   title=f"{count} Major Level{'s' if count != 1 else ''} (Bloom)", icon="minecraft:nether_star")
    else:
        raise ValueError(f"{chapter_id}/{quest_id}: unknown reward type {kind}")
    if reward.get("title") and kind not in ("bloom_xp", "major_levels"):
        out["title"] = text(reward["title"])
    return out


def mode(obj, where):
    if obj["progression_mode"] not in MODES:
        raise ValueError(f"{where}: unknown progression_mode {obj['progression_mode']}")
    return obj["progression_mode"]


def build(quest_dir, present, notes=None, title="LegionWareX"):
    """{path: bytes} for config/ftbquests/quests, with only what the present mods (namespaces) can show."""
    present = set(present) | ALWAYS
    files = {}
    groups = []
    order = {}
    skipped = []
    totals = [0, 0]
    book = list(chapters(quest_dir))
    # Quest ids across the book, for dependencies on other chapters ("chapter/quest").
    known = {}
    for _, chapter in book:
        if set(chapter.get("mods", [])) <= present:
            for quest in chapter["quests"]:
                if kept(quest, present):
                    known[f"{chapter['id']}/{quest['id']}"] = fid(chapter["id"], quest["id"])
    for name, chapter in book:
        cid = chapter["id"]
        if not set(chapter.get("mods", [])) <= present:
            skipped.append(f"chapter {cid}")
            continue
        group = chapter.get("group", "")
        if group and group not in groups:
            groups.append(group)
        quests = []
        for quest in chapter["quests"]:
            qid = quest["id"]
            if not kept(quest, present):
                skipped.append(f"{cid}/{qid}")
                continue
            tasks = [task_data(cid, qid, n, t) for n, t in enumerate(quest.get("tasks", [])) if kept(t, present)]
            if quest.get("tasks") and not tasks:
                skipped.append(f"{cid}/{qid} (no task left)")
                continue
            data = {"id": fid(cid, qid), "title": text(quest["title"]), "x": float(quest.get("x", 0)), "y": float(quest.get("y", 0)),
                    "tasks": tasks,
                    "rewards": [reward_data(cid, qid, n, r) for n, r in enumerate(quest.get("rewards", [])) if kept(r, present)]}
            for key, out_key in (("subtitle", "subtitle"), ("icon", "icon"), ("shape", "shape")):
                if quest.get(key):
                    data[out_key] = text(quest[key]) if key == "subtitle" else quest[key]
            if quest.get("shape") and quest["shape"] not in SHAPES:
                raise ValueError(f"{cid}/{qid}: unknown shape {quest['shape']}")
            if quest.get("size"):
                data["size"] = float(quest["size"])
            if quest.get("description"):
                data["description"] = text(list(quest["description"]))
            if quest.get("optional"):
                data["optional"] = True
            deps = []
            for dep in quest.get("deps", []):
                key = dep if "/" in dep else f"{cid}/{dep}"
                if key in known:
                    deps.append(known[key])
            if quest.get("min_deps") and int(quest["min_deps"]) > len(deps):
                # A milestone over more quests than this layer has (e.g. 40 bosses in a layer with 10).
                skipped.append(f"{cid}/{qid} (needs {quest['min_deps']} of {len(deps)})")
                continue
            if deps:
                data["dependencies"] = deps
                if quest.get("min_deps"):
                    data["min_required_dependencies"] = int(quest["min_deps"])
            if quest.get("hide_dependency_lines"):
                data["hide_dependency_lines"] = True
            if quest.get("progression_mode"):
                data["progression_mode"] = mode(quest, f"{cid}/{qid}")
            if deps and (quest.get("progression_mode") or chapter.get("progression_mode")) == "flexible":
                data["hide_lock_icon"] = True  # nothing is locked: tasks count from the start
            quests.append(data)
        if not quests:
            skipped.append(f"chapter {cid} (no quest left)")
            continue
        index = order.setdefault(group, 0)
        order[group] += 1
        chapter_data = {"id": fid(cid), "filename": cid, "group": fid("group", group) if group else "", "order_index": index,
                        "title": text(chapter["title"]), "icon": chapter.get("icon", ""), "default_quest_shape": "",
                        "default_hide_dependency_lines": False, "quests": quests, "quest_links": []}
        if chapter.get("progression_mode"):
            chapter_data["progression_mode"] = mode(chapter, cid)
        if chapter.get("subtitle"):
            chapter_data["subtitle"] = text(list(chapter["subtitle"]) if isinstance(chapter["subtitle"], list) else [chapter["subtitle"]])
        files[f"config/ftbquests/quests/chapters/{cid}.snbt"] = (snbt(chapter_data) + "\n").encode()
        totals[0] += 1
        totals[1] += len(quests)
    files["config/ftbquests/quests/chapter_groups.snbt"] = (snbt({"chapter_groups": [
        {"id": fid("group", g), "title": GROUPS.get(g, g.title())} for g in groups]}) + "\n").encode()
    files["config/ftbquests/quests/data.snbt"] = (snbt({
        "version": 13, "title": title, "icon": "minecraft:writable_book",
        "default_reward_team": False, "default_consume_items": False, "default_autoclaim_rewards": "disabled",
        "default_quest_shape": "circle", "default_quest_disable_jei": False, "emergency_items_cooldown": 300,
        "drop_loot_crates": False, "loot_crate_no_drop": {"passive": 4000, "monster": 600, "boss": 0},
        "disable_gui": False, "grid_scale": 0.5, "pause_game": False, "lock_message": "", "progression_mode": "linear",
        "detection_delay": 20, "drop_book_on_death": False, "show_lock_icons": True, "hide_excluded_quests": False,
    }) + "\n").encode()
    if notes is not None:
        notes.append(f"quest book: {totals[0]} chapters, {totals[1]} quests" + (f"; left out (mods not in this layer): {len(skipped)}" if skipped else ""))
    return files


if __name__ == "__main__":
    import sys
    # Check the book's JSON and print what it would write, with every mod present: quest_book.py [quests dir]
    folder = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "quests")
    every = set()
    for _, chapter in chapters(folder):
        every |= set(chapter.get("mods", []))
        for quest in chapter["quests"]:
            every |= needs(quest)
            for part in quest.get("tasks", []) + quest.get("rewards", []):
                every |= needs(part)
    notes = []
    written = build(folder, every, notes)
    print("\n".join(notes))
    for path, data in written.items():
        print(f"{path}: {len(data)} bytes")
