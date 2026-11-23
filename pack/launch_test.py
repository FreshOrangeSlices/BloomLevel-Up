"""The pack launch test: a real Forge 1.20.1 client with the pack's client side joins the pack's server.

Usage (inside a virtual display): python3 pack/launch_test.py <server folder> <client game folder> <screenshots dir>

The server folder is the one boot_server.py left (Forge installed). It's switched to a flat world in offline mode
and started; the client (installed and started by portablemc, offline, as the build_pack.py --client-run folder has
it) first starts to the title screen (captured: it should be Minecraft's own), then joins it. The server then sets a scene (noon, some Fresh Animations mobs in front of the player) and the screen
is captured a few times. Then every modded creature is summoned in front of the player a few at a time and
cleared again (the mob check), so a mod that breaks when one of them spawns or ticks (as Better Mob Combat did with
many of them), or that the client can't draw, shows up here instead of in a playthrough. The test server removes an
entity that fails to tick and logs it (Forge's removeErroringEntities) so one bad mob doesn't hide the rest; a
creature with a spawn egg doing that fails the test, anything else (projectiles, effects summoned bare) is a warning.
Checks: the client gets in, Bloom switched the pack's resource packs (and shader) on (log line and options.txt), the
mob check, and nothing crashed. Results and the client's warnings go out as GitHub annotations; the screenshots are
written as PNGs for the screenshot job.
"""
import collections
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quest_book  # noqa: E402 (pack/quest_book.py: which ids the quest book names)

PLAYER = "BloomTester"
TITLE_LIMIT = 1200  # seconds for the first start (installs Forge's client, then loads every mod) to reach the title screen
JOIN_LIMIT = 900  # seconds for the second start to join
FORGE_FALLBACK = "1.20.1-47.4.28"  # build_pack.py writes the pack's own into the game folder (forge-version.txt)
SCENE = [
    "time set 6000",
    "weather clear",
    "gamerule doDaylightCycle false",
    "gamerule doWeatherCycle false",
    "gamerule doMobSpawning false",
    "difficulty easy",
    f"execute as {PLAYER} at @s run tp @s ~ ~ ~ 0 8",
    f"execute as {PLAYER} at @s run summon minecraft:creeper ~-2.5 ~ ~5 {{NoAI:1b,Rotation:[180f,0f]}}",
    f"execute as {PLAYER} at @s run summon minecraft:zombie ~-0.8 ~ ~5.5 {{NoAI:1b,Rotation:[180f,0f]}}",
    f"execute as {PLAYER} at @s run summon minecraft:spider ~1.2 ~ ~5 {{NoAI:1b,Rotation:[180f,0f]}}",
    f"execute as {PLAYER} at @s run summon minecraft:villager ~3 ~ ~5.5 {{NoAI:1b,Rotation:[180f,0f]}}",
    f"execute as {PLAYER} at @s run summon minecraft:pig ~0.5 ~ ~3.5 {{NoAI:1b,Rotation:[150f,0f]}}",
    f"execute as {PLAYER} at @s run summon minecraft:chicken ~-1.5 ~ ~3 {{NoAI:1b,Rotation:[200f,0f]}}",
    f"give {PLAYER} minecraft:diamond_sword",
]


class Process:
    """A child process whose output is collected line by line in the background."""

    def __init__(self, args, cwd, env=None, stdin=False):
        self.lines = []
        self.folder = cwd
        # Its own process group, so stopping a launcher stops the game it started too.
        self.process = subprocess.Popen(args, cwd=cwd, env=env, stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for line in self.process.stdout:
            self.lines.append(line.rstrip())

    def wait_for(self, pattern, limit):
        end = time.time() + limit
        seen = 0
        while time.time() < end:
            while seen < len(self.lines):
                if re.search(pattern, self.lines[seen]):
                    return self.lines[seen]
                seen += 1
            if self.process.poll() is not None:
                return None
            time.sleep(1)
        return None

    def send(self, command):
        """Sends a console command; False when the process has gone (its stdin is closed)."""
        try:
            self.process.stdin.write(command + "\n")
            self.process.stdin.flush()
            return True
        except (BrokenPipeError, OSError, ValueError):
            return False


def progress(step):
    """A milestone, with the machine's free memory, written down as it happens (so a run that dies shows how far it got):
    to pack-progress.txt, which the workflow's next step prints as one annotation (GitHub keeps only 10 notices a step,
    and this step's own findings need them)."""
    free = next((int(l.split()[1]) // 1024 for l in open("/proc/meminfo") if l.startswith("MemAvailable:")), -1)
    line = f"{step} ({free} MB free, {time.strftime('%H:%M:%S')})"
    print(f"progress: {line}", flush=True)
    with open("pack-progress.txt", "a") as out:
        out.write(line + "\n")


def set_properties(path, values):
    lines = open(path).read().splitlines() if os.path.exists(path) else []
    keys = {line.split("=", 1)[0] for line in lines if "=" in line}
    lines = [f"{line.split('=', 1)[0]}={values[line.split('=', 1)[0]]}" if "=" in line and line.split("=", 1)[0] in values else line
             for line in lines]
    lines += [f"{key}={value}" for key, value in values.items() if key not in keys]
    open(path, "w").write("\n".join(lines) + "\n")


def settle(process, quiet, limit):
    """Waits until the process has printed nothing for {quiet} seconds (at most {limit})."""
    end = time.time() + limit
    count, since = len(process.lines), time.time()
    while time.time() < end and process.process.poll() is None:
        time.sleep(1)
        if len(process.lines) != count:
            count, since = len(process.lines), time.time()
        elif time.time() - since >= quiet:
            return


def stop(process):
    """Stops a launcher and the game it started (its whole process group)."""
    for sig, wait in ((signal.SIGTERM, 60), (signal.SIGKILL, 30)):
        try:
            os.killpg(process.process.pid, sig)
        except ProcessLookupError:
            return
        try:
            process.process.wait(timeout=wait)
            time.sleep(3)  # the game may outlive the launcher by a moment
            os.killpg(process.process.pid, 0)
        except (subprocess.TimeoutExpired, ProcessLookupError):
            if sig == signal.SIGTERM:
                continue
            return
        except OSError:
            return


# Known problems the mob check steps over (so it can test everything after them), each with what happens: they're
# reported every run, and the pack has to deal with them some other way.
KNOWN = {
    "mythsandlegends:cogwork_construct": "Myths & Legends 1.0.6's renderer reads its entity before GeckoLib sets it, so the "
                                         "first one a player sees crashes their game",
    # Not a problem in play, only when summoned bare: an entity that can't exist without its data.
    "handcrafted:fancy_painting": "summoned bare it has no picture and Handcrafted's renderer can't draw it; one a player "
                                  "hangs always has one",
}


def modded_entities(mods_dir):
    """Entity ids from the mods' language files (entity.<mod>.<name>), and which of them have a spawn egg."""
    entities, eggs = set(), set()
    for jar in sorted(os.listdir(mods_dir)):
        try:
            archive = zipfile.ZipFile(os.path.join(mods_dir, jar))
        except (zipfile.BadZipFile, IsADirectoryError):
            continue
        for name in archive.namelist():
            match = re.fullmatch(r"assets/([a-z0-9_.-]+)/lang/en_us\.json", name)
            if not match or match.group(1) == "minecraft":
                continue
            try:
                lang = json.loads(archive.read(name).decode("utf-8-sig", errors="replace"))
            except ValueError:
                continue
            for key in lang:
                parts = key.split(".")
                if len(parts) == 3 and parts[0] == "entity" and parts[1] != "minecraft" and re.fullmatch(r"[a-z0-9_/-]+", parts[2]):
                    entities.add(f"{parts[1]}:{parts[2]}")
                elif len(parts) == 3 and parts[0] == "item" and "egg" in parts[2]:
                    eggs.add((parts[1], parts[2]))
    with_egg = {e for e in entities if any(ns == e.split(":")[0] and e.split(":")[1] in item for ns, item in eggs)}
    return sorted(entities), with_egg


# Better Combat categories Bloom gives no family: spell weapons, shields, and mining tools (the pickaxe's swing, which the
# pack gives Only Hammers And Excavators' hammers). Fist weapons (gauntlets, claws) are Bloom's Unarmed since 2026-11-22.
BLOOM_NONE = {"staff", "battlestaff", "wand", "spellbreaker", "horn", "shield", "pickaxe"}


def pack_data_check(server_dir, dump_file, server_lines):
    """The pack's own data pack (pack_data, loaded by Open Loader from config/openloader/data): every recipe it takes
    out is gone from the server, nothing makes an item it hides, and each hidden item carries JEI's hidden tag. From
    the files the server zip shipped and Bloom's registry dump. Ids from mods this layer leaves out are skipped.
    Returns (failures, notes)."""
    packs = os.path.join(server_dir, "config", "openloader", "data")
    if not os.path.isdir(packs):
        return [], []
    removed, hidden, spell_costs, swings = [], [], [], {}
    for pack in sorted(os.listdir(packs)):
        data = os.path.join(packs, pack, "data")
        for folder, _, names in os.walk(data):
            for file_name in names:
                parts = os.path.relpath(os.path.join(folder, file_name), data).replace(os.sep, "/").split("/", 2)
                # Better Combat swings the pack gives (data_files): {item: the preset}.
                if len(parts) == 3 and parts[1] == "weapon_attributes" and file_name.endswith(".json"):
                    try:
                        swings[f"{parts[0]}:{parts[2][:-5]}"] = json.load(open(os.path.join(folder, file_name))).get("parent", "")
                    except ValueError:
                        swings[f"{parts[0]}:{parts[2][:-5]}"] = ""
                if len(parts) == 3 and parts[1] == "recipes" and file_name.endswith(".json"):
                    # Only recipes switched off (a forge:false condition); recipe_edits are changed, not removed.
                    try:
                        recipe = json.load(open(os.path.join(folder, file_name)))
                    except ValueError:
                        recipe = {}
                    if {"type": "forge:false"} in recipe.get("conditions", []):
                        removed.append(f"{parts[0]}:{parts[2][:-5]}")
        # Spell costs edited (data_edits on a mod's spell folder, Paladins & Priests): no item cost and no hunger left.
        for namespace in (os.listdir(data) if os.path.isdir(data) else []):
            spells = os.path.join(data, namespace, "spell")
            if not os.path.isdir(spells):
                continue
            for file_name in sorted(os.listdir(spells)):
                try:
                    cost = json.load(open(os.path.join(spells, file_name))).get("cost") or {}
                except ValueError:
                    cost = {}
                spell_costs.append((f"{namespace}:{file_name[:-5]}", cost))
        tag = os.path.join(data, "c", "tags", "items", "hidden_from_recipe_viewers.json")
        if os.path.exists(tag):
            hidden += [v["id"] if isinstance(v, dict) else v for v in json.load(open(tag))["values"]]
    if not os.path.exists(dump_file):
        return ["pack data: no registry dump to check it against"], []
    rows = [json.loads(line) for line in open(dump_file) if line.strip()]
    recipes = [r for r in rows if r.get("kind") == "recipe"]
    items = {r["id"]: r for r in rows if r.get("kind") == "item"}
    mods = {i.split(":")[0] for i in items}
    failures = []
    if not recipes:
        failures.append("pack data: the registry dump has no recipes (an older Bloom?)")
    loaded = {r["id"] for r in recipes}
    failures += [f"pack data: the recipe {r} is still loaded" for r in removed if r in loaded]
    checked = [i for i in hidden if i.split(":")[0] in mods]
    for item in checked:
        if item not in items:
            failures.append(f"pack data: there's no item {item} to hide (a typo, or the mod renamed it)")
            continue
        if "c:hidden_from_recipe_viewers" not in items[item]["tags"]:
            failures.append(f"pack data: {item} isn't hidden from JEI (no c:hidden_from_recipe_viewers tag)")
        makers = sorted(r["id"] for r in recipes if r.get("result") == item)
        if makers:
            failures.append(f"pack data: {item} is hidden but still made by {', '.join(makers)}")
    # Each swing the pack gives took (the dump has Better Combat's category for every item): a weapon with one at all, a
    # mining hammer with the pickaxe's, not the mace's its name would get.
    given = [item for item in swings if item in items]
    for item in given:
        category = (items[item].get("bettercombat") or "").split("|")[0]
        wanted = swings[item].split(":")[-1]
        if not category:
            failures.append(f"pack data: {item} has no Better Combat swing (the pack gives {swings[item]})")
        elif wanted == "pickaxe" and category != "pickaxe":
            failures.append(f"pack data: {item} swings as {category}, not the pickaxe the pack gives it")
    costly = [spell for spell, cost in spell_costs if "item" in cost or cost.get("exhaust", 0.0) != 0.0]
    if costly:
        failures.append(f"pack data: {len(costly)} edited spells still cost an item or hunger: {', '.join(costly[:10])}")
    injected = next((l.split("]: ", 1)[-1] for l in server_lines if "Successfully injected" in l), "Open Loader said nothing")
    notes = [f"pack data: {len([r for r in removed if r.split(':')[0] in mods])} recipes out, {len(checked)} items hidden "
             f"(of {len(removed)} and {len(hidden)} listed; the rest are from mods this layer leaves out)"
             + (f"; {len(spell_costs)} spells cost only their cooldown" if spell_costs else "")
             + (f"; {len(given)} Better Combat swings given" if given else "") + f"; {injected}"]
    return failures, notes


def carry_on_check(server_dir):
    """Carry On's mob whitelist as the server left its config (the pack ships it; the mod adds its other settings): only
    plain vanilla animals and villagers can be picked up. Nothing to check without the mod. Returns (failures, notes)."""
    if not any(f.lower().startswith("carryon") for f in os.listdir(os.path.join(server_dir, "mods"))):
        return [], []
    path = os.path.join(server_dir, "config", "carryon-common.toml")
    if not os.path.exists(path):
        return ["carry on: config/carryon-common.toml isn't on the server"], []
    text = open(path, encoding="utf-8", errors="replace").read()
    allowed = re.search(r"allowedEntities\s*=\s*\[(.*?)\]", text, re.S)
    allowed = re.findall(r'"([^"]+)"', allowed.group(1)) if allowed else []
    if not re.search(r"useWhitelistEntities\s*=\s*true", text) or "minecraft:cow" not in allowed:
        return [f"carry on: the mob whitelist isn't on (useWhitelistEntities, {len(allowed)} allowed)"], []
    modded = [e for e in allowed if not e.startswith("minecraft:")]
    return ([f"carry on: modded mobs on the whitelist: {', '.join(modded)}"] if modded else []), \
        [f"carry on: only {len(allowed)} vanilla mobs can be carried"]


def voice_check(server_dir, spec_file):
    """Voiceless Survival's mob list as the server left it: every mob the pack switches on (pack.json's config_files) should
    still be on after the mod has added the ones it found itself. Nothing to check without the mod. Returns (failures, notes)."""
    name = "config/ezvcsurvival/entities_voices.json"
    if not any("voiceless" in f.lower() for f in os.listdir(os.path.join(server_dir, "mods"))):
        return [], []
    shipped = (json.load(open(spec_file)).get("config_files") or {}).get(name, {}) if os.path.exists(spec_file) else {}
    path = os.path.join(server_dir, name)
    if not shipped or not os.path.exists(path):
        return ([f"voice: {name} isn't on the server"] if shipped else []), []
    try:
        have = json.load(open(path))
    except ValueError as e:
        return [f"voice: {name} can't be read: {e}"], []
    off = [f"{kind} {mob}" for kind in ("monsters", "animals") for mob, entry in (shipped.get(kind) or {}).items()
           if entry.get("enabled") and not (have.get(kind) or {}).get(mob, {}).get("enabled")]
    listed = sum(len(have.get(kind) or {}) for kind in ("monsters", "animals"))
    heard = sum(1 for kind in ("monsters", "animals") for entry in (have.get(kind) or {}).values() if entry.get("enabled"))
    if off:
        return [f"voice: {len(off)} mobs the pack turns on are off on the server: " + ", ".join(off[:20])], []
    return [], [f"voice: {heard} of {listed} mobs hear voice chat (the pack's {sum(len(shipped.get(k) or {}) for k in ('monsters', 'animals'))} kept)"]


def spawn_check(server_dir, dump_file, spec_file):
    """The pack's natural spawns (pack_data.spawns, as biome modifiers in its data pack), from the biome rows of Bloom's
    registry dump (every biome's spawn list after all mods' modifiers). Fails when a mob the pack adds is missing from
    every biome (its modifier didn't load) or is a monster that never despawns (natural spawns would pile up; animals never
    do, and spawns.persistent lists the monsters kept anyway), when Skrythe's lair can go
    in a biome that isn't a mountain, when a Skarrier cave mob is still listed in the Nether or the End, when a biome list
    of the pack's reaches a dimension it doesn't name (Overworld only, unless it says), when a structure set isn't at
    the pack's spacing, when a mob the pack adds to a structure isn't in its spawns, or when a mob it adds has no spawn
    rules (its mod registered none) and no Easy Mob Spawn Control rule from the pack. Reports each
    overworld biome's modded share of the night's spawns, counted in mobs (weight x average group): about 1 in 5 is
    the aim. Mobs that spawn only underground, after the Wither or under a condition of their own (spawns.conditional)
    and quiet biomes are left out. Returns (failures, notes, report lines)."""
    folder = os.path.join(server_dir, "config", "openloader", "data")
    added = {}
    for pack in (sorted(os.listdir(folder)) if os.path.isdir(folder) else []):
        mods_dir = os.path.join(folder, pack, "data", "legionwarex", "forge", "biome_modifier", "spawns")
        for name in sorted(os.listdir(mods_dir)) if os.path.isdir(mods_dir) else []:
            for spawner in json.load(open(os.path.join(mods_dir, name))).get("spawners", []):
                added.setdefault(spawner["type"], []).append(name[:-5])
    overridden = any(os.path.isdir(os.path.join(folder, pack, "data", ns, "worldgen", "structure"))
                     for pack in (os.listdir(folder) if os.path.isdir(folder) else [])
                     for ns in (os.listdir(os.path.join(folder, pack, "data")) if os.path.isdir(os.path.join(folder, pack, "data")) else []))
    if not added and not overridden:
        return [], ["spawns: none added in this layer"], []
    if not os.path.exists(dump_file):
        return ["spawns: no registry dump to check them against"], [], []
    rows = [json.loads(line) for line in open(dump_file) if line.strip()]
    biomes = [r for r in rows if r.get("kind") == "biome" and "spawns" in r]
    entities = {r["id"]: r for r in rows if r.get("kind") == "entity"}
    if not biomes:
        return ["spawns: the registry dump has no biomes (an older Bloom?)"], [], []
    spawns = (json.load(open(spec_file)).get("pack_data") or {}).get("spawns", {}) if os.path.exists(spec_file) else {}
    conditional = {e for k, v in spawns.get("conditional", {}).items() if not k.startswith("_") for e in v}
    quiet_ids = set(spawns.get("quiet", {}).get("biomes", []))
    persistent = set(spawns.get("persistent", {}).get("mobs", []))
    failures, notes_extra = [], []
    listed = {s["type"] for b in biomes for v in b["spawns"].values() for s in v}
    # Easy Mob Spawn Control's rules (the pack's config): what keeps a mob with no spawn rules of its own to the night.
    assignments_file = os.path.join(server_dir, "config", "EasyMSC", "spawn_rule_assignments.json")
    ruled = json.load(open(assignments_file)) if os.path.exists(assignments_file) else {}
    unruled_ok = set(spawns.get("unruled", {}).get("mobs", []))
    # Its rules are checked against the world itself, so one on a mob that spawns while chunks generate (animals)
    # waits on a chunk that can't load: the server hangs starting.
    for mob in sorted(m for m, names in ruled.items() if names and entities.get(m, {}).get("category") == "creature"):
        failures.append(f"spawns: {mob} is an animal with an Easy Mob Spawn Control spawn rule, which hangs world generation")
    for mob, groups in sorted(added.items()):
        if mob not in listed:
            failures.append(f"spawns: {mob} ({', '.join(groups)}) isn't in any biome's spawns (its modifier didn't load)")
        elif entities.get(mob, {}).get("spawnRules") is False and not ruled.get(mob) and mob not in unruled_ok:
            failures.append(f"spawns: {mob} ({', '.join(groups)}) has no spawn rules of its own, so it would spawn in daylight, "
                            f"in caves and on treetops: give it an Easy Mob Spawn Control rule (config/EasyMSC/spawn_rule_assignments.json)")
        elif entities.get(mob, {}).get("spawnsInDaylight") and not ruled.get(mob) and mob not in unruled_ok:
            failures.append(f"spawns: {mob} ({', '.join(groups)}) can spawn in daylight (its spawn rules don't ask for the dark, "
                            f"like illagers' patrol rules): give it an Easy Mob Spawn Control rule (config/EasyMSC/spawn_rule_assignments.json)")
        elif (entities.get(mob, {}).get("despawns") is False and entities[mob].get("category") == "monster"
              and mob not in persistent):
            failures.append(f"spawns: {mob} ({', '.join(groups)}) never despawns, so natural spawns would pile up")
    lair = [b["id"] for b in biomes if "monsterexpansion:skrythe_biomes" in b["tags"]
            and not {"minecraft:is_mountain", "forge:is_mountain"} & set(b["tags"])]
    if lair:
        failures.append(f"spawns: Skrythe's lair can still go in {', '.join(lair[:8])}")
    overworld_only = {"skarrier_mobs:" + m for m in ("breacher", "dragger", "eater", "wrought", "bowlder", "gorger", "slither_spawner_dummy", "viruleon")}
    elsewhere = sorted({s["type"] for b in biomes if "minecraft:is_overworld" not in b["tags"] for v in b["spawns"].values()
                        for s in v if s["type"] in overworld_only})
    if elsewhere:
        failures.append(f"spawns: Skarrier's overworld mobs are still listed outside the Overworld: {', '.join(elsewhere)}")
    # The pack's own biome lists (spawn groups, tags it adds to) stay in the Overworld unless they say otherwise
    # ("dimensions"): Forge's plain is_dry, for one, has the Nether and the End in it.
    dimension = lambda b: "nether" if "minecraft:is_nether" in b["tags"] else "end" if "minecraft:is_end" in b["tags"] else "overworld"
    reach = lambda values: {b["id"]: dimension(b) for b in biomes for v in values
                            if (v.startswith("#") and v[1:] in b["tags"]) or v == b["id"]}
    lists = [(f"spawn group {n}", s.get("biomes", []), s) for n, s in spawns.get("add", {}).items() if not n.startswith("_")]
    lists += [(f"biome tag {t}", s.get("values", []), s) for t, s in spawns.get("biome_tags", {}).items() if not t.startswith("_")]
    for label, values, spec in lists:
        astray = sorted(i for i, d in reach(values).items() if d not in spec.get("dimensions", ["overworld"]))
        if astray:
            failures.append(f"spawns: {label} reaches {', '.join(astray[:6])}{' ...' if len(astray) > 6 else ''} (outside its dimensions)")
    # Structures the pack overrides (structure_spawns): every spawn its file lists is in the loaded structure's spawns.
    structures = {r["id"]: r for r in rows if r.get("kind") == "structure"}
    for pack in (sorted(os.listdir(folder)) if os.path.isdir(folder) else []):
        data_dir = os.path.join(folder, pack, "data")
        for namespace in sorted(os.listdir(data_dir)) if os.path.isdir(data_dir) else []:
            base = os.path.join(data_dir, namespace, "worldgen", "structure")
            for here, _, files in os.walk(base):
                for file_name in sorted(f for f in files if f.endswith(".json")):
                    ident = f"{namespace}:{os.path.relpath(os.path.join(here, file_name), base)[:-5]}"
                    loaded = (structures.get(ident) or {}).get("spawns", {})
                    for category, override in json.load(open(os.path.join(here, file_name))).get("spawn_overrides", {}).items():
                        missing = [f"{s['type']} {s['weight']} ({s['minCount']}-{s['maxCount']})" for s in override.get("spawns", [])
                                   if {"type": s["type"], "weight": s["weight"], "min": s["minCount"], "max": s["maxCount"]}
                                   not in loaded.get(category, {}).get("spawns", [])]
                        if missing:
                            failures.append(f"spawns: {ident} doesn't list {', '.join(missing)} (its override didn't load)")
                        else:
                            notes_extra.append(f"structure spawns: {ident} {category}: " + ", ".join(
                                f"{s['type'].split(':')[1]} {s['weight']}" for s in loaded[category]["spawns"]))
    # Book of Dragons' roosts: every type in BOP's biomes too, at the pack's spacing.
    sets = {r["id"]: r for r in rows if r.get("kind") == "structure_set"}
    roosts = []
    for ident, want in spawns.get("structure_sets", {}).items():
        if ident.startswith("_") or ident.split(":")[0] not in {s.split(":")[0] for s in sets}:
            continue
        got = sets.get(ident)
        if not got or (got["spacing"], got["separation"]) != (want["spacing"], want["separation"]):
            failures.append(f"spawns: {ident} is {got and (got['spacing'], got['separation'])}, not {want['spacing']}/{want['separation']} chunks apart")
            continue
        tag = ident.replace(":roost_", ":has_roost_")
        where = [b["id"] for b in biomes if tag in b["tags"]]
        if tag != ident:
            if not any(i.startswith("biomesoplenty:") for i in where) and any(b["id"].startswith("biomesoplenty:") for b in biomes):
                failures.append(f"spawns: {ident} can't go in any of Biomes O' Plenty's biomes")
            roosts.append(f"{ident.split(':')[1]} {want['spacing']} chunks apart in {len(where)} biomes")
        else:
            roosts.append(f"{ident} {want['spacing']} chunks apart")
    report, shares = [], []
    for b in sorted(biomes, key=lambda b: b["id"]):
        if "minecraft:is_overworld" not in b["tags"] or b["id"] in quiet_ids:
            continue
        live = [s for s in b["spawns"].get("monster", []) if s["type"] not in conditional
                and not (s["type"] == "minecraft:slime" and "minecraft:allows_surface_slime_spawns" not in b["tags"])]
        mobs = lambda s: s["weight"] * (s["min"] + s["max"]) / 2
        total = sum(mobs(s) for s in live)
        modded = sum(mobs(s) for s in live if not s["type"].startswith("minecraft:"))
        if total:
            shares.append((modded / total, b["id"]))
    shares.sort()
    for share, ident in shares:
        report.append(f"{share * 100:5.1f}%  {ident}")
    land = [s for s in shares if not any(w in s[1] for w in ("ocean", "river", "beach", "shore", "caves", "grotto"))]
    median = land[len(land) // 2][0] if land else 0
    notes = [f"spawns: {len(added)} mobs added by the pack; modded share of night spawns (in mobs) on land: median "
             f"{median * 100:.0f}%, {land[0][0] * 100:.0f}% ({land[0][1]}) to {land[-1][0] * 100:.0f}% ({land[-1][1]})" if land else "spawns: no land biomes"]
    if roosts:
        notes.append("structure sets: " + ", ".join(roosts))
    # The daylight check itself (Bloom's dump asks each monster's spawn rules about a daylit spot near spawn): a zombie
    # may not spawn there, a pillager (patrol rules) may.
    probe = {m: entities.get(m, {}).get("spawnsInDaylight") for m in ("minecraft:zombie", "minecraft:pillager")}
    if probe["minecraft:zombie"] is None:
        notes.append("daylight check: didn't run (night at the dump, or no open ground near spawn)")
    elif probe != {"minecraft:zombie": False, "minecraft:pillager": True}:
        failures.append(f"daylight check: zombie {probe['minecraft:zombie']}, pillager {probe['minecraft:pillager']} (should be false, true)")
    unruled = sorted(m for m in added if entities.get(m, {}).get("spawnRules") is False or entities.get(m, {}).get("spawnsInDaylight"))
    if unruled:
        notes.append("spawn rules from the pack (their mods give none, or allow daylight): " + ", ".join(f"{m} ({'/'.join(ruled.get(m, [])) or 'none'})" for m in unruled))
    notes += notes_extra
    # Biome tags the pack adds to (structures, roosts): each on some biome, and on one of BOP's when it names BOP's.
    have_bop = any(b["id"].startswith("biomesoplenty:") for b in biomes)
    for tag, want in spawns.get("biome_tags", {}).items():
        if tag.startswith("_") or not want.get("values") or tag.split(":")[0] not in {t.split(":")[0] for b in biomes for t in b["tags"]}:
            continue
        on = [b["id"] for b in biomes if tag in b["tags"]]
        if not on:
            failures.append(f"spawns: no biome carries {tag}")
        elif have_bop and not any(i.startswith("biomesoplenty:") for i in on) and any(
                v.startswith("#") or v.startswith("biomesoplenty:") for v in want["values"]):
            failures.append(f"spawns: {tag} has none of Biomes O' Plenty's biomes")
    # Recipes the pack changed in place: loaded, with the new item and without the old.
    recipes = {r["id"]: r for r in rows if r.get("kind") == "recipe"}
    edits = (json.load(open(spec_file)).get("pack_data") or {}).get("recipe_edits", {}) if os.path.exists(spec_file) else {}
    for ident, edit in edits.items():
        if ident.startswith("_") or ident.split(":")[0] not in {e.split(":")[0] for e in entities}:
            continue
        takes = set((recipes.get(ident) or {}).get("ingredients", []))
        if ident not in recipes:
            failures.append(f"recipe edits: {ident} isn't loaded")
        elif any(old in takes or new not in takes for old, new in edit["swap"].items()):
            failures.append(f"recipe edits: {ident} takes {sorted(takes)}, not the swapped items")
        else:
            notes.append(f"recipe edits: {ident} takes {', '.join(sorted(takes))}")
    return failures, notes, report


def bloom_sorting(dump_file):
    """How much of the pack Bloom has sorted, from the server's registry dump (-Dbloomlevelup.dump): weapons into its
    families, armor into its classes, daggers into its dagger tag, bosses with a boss bar into its boss tag. Returns
    (summary, unsorted lines)."""
    if not os.path.exists(dump_file):
        return "no registry dump", []
    rows = [json.loads(line) for line in open(dump_file) if line.strip()]
    families = ("sword", "axe", "bow", "crossbow", "trident", "unarmed")
    in_family = lambda r: any(f"bloomlevelup:weapon/{f}" in r["tags"] and f"bloomlevelup:weapon/not_{f}" not in r["tags"] for f in families)
    weapons, armor, daggers, bosses, unsorted = 0, 0, 0, 0, []
    for r in rows:
        if r.get("kind") == "item":
            category = (r.get("bettercombat") or "").split("|")[0]
            weaponish = (category and category not in BLOOM_NONE) or (category not in BLOOM_NONE and any(
                c in r.get("classes", []) for c in ("SwordItem", "AxeItem", "BowItem", "CrossbowItem", "TridentItem")))
            if weaponish:
                weapons += 1
                if not in_family(r):
                    unsorted.append(f"weapon {r['id']} ({category or r['classes'][0]})")
            if category in ("dagger", "soul_knife"):
                daggers += 1
                if "bloomlevelup:weapon/dagger" not in r["tags"]:
                    unsorted.append(f"dagger {r['id']}")
            if "armor" in r:
                armor += 1
                if not any(f"bloomlevelup:armor/{c}" in r["tags"] for c in ("light", "medium", "heavy")):
                    unsorted.append(f"armor {r['id']} ({r['armor']['material']})")
        elif r.get("kind") == "entity" and r.get("bossBar"):
            bosses += 1
            if "bloomlevelup:bosses" not in r["tags"]:
                unsorted.append(f"boss {r['id']}")
    return f"Bloom sorting: {weapons} weapons, {armor} armor pieces, {daggers} daggers, {bosses} bosses with a boss bar; {len(unsorted)} unsorted", unsorted


MATERIAL_TAGS = ("forge:ingots", "forge:nuggets", "forge:gems", "forge:raw_materials", "forge:dusts", "forge:rods", "forge:leather")
MATERIAL_NAMES = re.compile(r"(_ingot|_nugget|_gem|_shard|_scales?|_plating|_fragment|_crystal|_chunk|_alloy|_hide)$|^raw_")
COOKING = ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking")


def recipe_mentions(server_dir, ids):
    """For each id, the recipe files in the server's jars that name it (any recipe type: a mod's own crafting stations
    don't always say what they take), leaving out those the pack's data pack replaces."""
    replaced = set()
    data_root = os.path.join(server_dir, "config", "openloader", "data")
    for root, _, files in os.walk(data_root):
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), data_root).replace(os.sep, "/")
            if "/data/" in rel and "/recipes/" in rel:
                replaced.add(rel.split("/data/", 1)[1])  # <pack>/data/<namespace>/recipes/... -> <namespace>/recipes/...
    found = {i: [] for i in ids}
    mods = os.path.join(server_dir, "mods")
    for jar in sorted(os.listdir(mods)) if os.path.isdir(mods) else []:
        try:
            z = zipfile.ZipFile(os.path.join(mods, jar))
        except (zipfile.BadZipFile, OSError):
            continue
        for name in z.namelist():
            if not (name.startswith("data/") and "/recipes/" in name and name.endswith(".json")) or name[5:] in replaced:
                continue
            text = z.read(name).decode("utf-8", "replace")
            for i in ids:
                if f'"{i}"' in text:
                    found[i].append(name[5:])
    return found


def leftover_check(dump_file, spec_file, server_dir):
    """Materials (ingots, nuggets, gems, scales, plates and the like) that nothing takes any more: what a gear trim can
    leave behind (FreshOrangeSlices: copper had almost nothing left to make). A material here is one no recipe crafts
    (a drop or loot) or one only cooked; something crafted is an end product, and food, blocks and other usable items
    have their use. Taken means named by a loaded recipe or by any recipe file in the jars the pack doesn't replace (a mod's
    own station may not say what it takes). Items the pack takes out or hides don't count. Returns (note, lines)."""
    if not os.path.exists(dump_file):
        return "leftovers: no registry dump", []
    rows = [json.loads(line) for line in open(dump_file) if line.strip()]
    if not any(r.get("kind") == "recipe" and "ingredients" in r for r in rows):
        return "leftovers: the dump has no recipe ingredients", []
    spec = json.load(open(spec_file)).get("pack_data", {})
    gone = {i for field in ("removed_items", "hidden_items") for reason, ids in spec.get(field, {}).items()
            if not reason.startswith("_") for i in ids}
    used, made = set(), {}
    for r in rows:
        if r.get("kind") == "recipe":
            used.update(r.get("ingredients", []))
            if r.get("result"):
                made.setdefault(r["result"], []).append(r.get("type"))
    materials = [r for r in rows if r.get("kind") == "item" and r["id"] not in gone and not r.get("durability") and "armor" not in r
                 and r.get("use", "none") == "none" and "BlockItem" not in r.get("classes", []) and all(t in COOKING for t in made.get(r["id"], []))
                 and (any(t.startswith(MATERIAL_TAGS) for t in r.get("tags", [])) or MATERIAL_NAMES.search(r["id"].split(":", 1)[1]))]
    candidates = [r["id"] for r in materials if r["id"] not in used]
    mentions = recipe_mentions(server_dir, candidates)
    leftovers = sorted(i for i in candidates if not mentions[i])
    names = {r["id"]: r.get("name", "") for r in materials}
    lines = [f"{item} ({names[item]}): nothing takes it; " + ("cooked" if item in made else "a drop or loot") for item in leftovers]
    return f"leftovers: {len(leftovers)} of {len(materials)} materials have nothing that takes them", lines


def crash_summary(folder, lines):
    """What a crashed process said: the newest crash report's description and first lines, else its last errors."""
    reports = sorted((os.path.join(folder, "crash-reports", f) for f in os.listdir(os.path.join(folder, "crash-reports"))),
                     key=os.path.getmtime) if os.path.isdir(os.path.join(folder, "crash-reports")) else []
    if reports:
        text = open(reports[-1], errors="replace").read()
        start = text.find("Description:")
        return text[start if start >= 0 else 0:][:2500]
    errors = [l for l in lines if re.search(r"Exception|Error|Caused by|\tat ", l)]
    return "\n".join(errors[-30:])[:2500]


def mob_check(server, client, player, entities, with_egg, shots, server_dir="."):
    """Summons each modded entity a few at a time in front of the player (in creative, so they leave it alone), lets
    them tick, then clears them. Returns (failures, warnings, summoned)."""
    failures, warnings, summoned = [], [], 0
    batch_size = 6
    server.send(f"gamemode creative {player}")
    server.send("gamerule doMobLoot false")
    server.send("gamerule doTileDrops false")
    server.send("gamerule mobGriefing false")
    time.sleep(1)
    warnings += [f"skipped {entity} (known: {why})" for entity, why in KNOWN.items() if entity in entities]
    entities = [entity for entity in entities if entity not in KNOWN]
    batches = [entities[i:i + batch_size] for i in range(0, len(entities), batch_size)]
    for n, batch in enumerate(batches):
        start = len(server.lines)
        for i, entity in enumerate(batch):
            server.send(f"execute as {player} at @s run summon {entity} ~{i * 3 - 7.5} ~ ~14")
        time.sleep(3)
        if n == len(batches) // 2:
            capture(shots, "pack_mobs")
        alive = server.process.poll() is None and server.send(f"execute as {player} at @s run kill @e[type=!minecraft:player,distance=..90]")
        time.sleep(0.5)
        alive = alive and server.send(f"execute as {player} at @s run tp @e[type=!minecraft:player,distance=..90] ~4000 ~ ~")
        time.sleep(0.5)
        if not alive or server.process.poll() is not None:
            failures.append(f"the server stopped during the mob check, with {', '.join(batch)}:\n" + crash_summary(server_dir, server.lines[start:]))
            break
        summoned += len(batch)
        new = server.lines[start:]
        for entity in sorted(set(re.findall(r"Entity Type: ([a-z0-9_.-]+:[a-z0-9_/.-]+)", "\n".join(new)))):
            (failures if entity in with_egg else warnings).append(f"{entity} failed to tick on the server (removed)")
        errors = [line for line in new if re.search(r"Exception|/ERROR\]", line) and "Entity Type" not in line]
        if errors and len(warnings) < 40:
            warnings.append(f"server errors while summoning {', '.join(batch)}: " + " / ".join(dict.fromkeys(errors))[:400])
        if client.process.poll() is not None:
            failures.append(f"the client stopped during the mob check, with {', '.join(batch)}:\n" + crash_summary(client.folder, client.lines[-200:]))
            break
    server.send(f"gamemode survival {player}")
    return failures, warnings, summoned


def hold_mouse(button, seconds, keys=()):
    """Holds a mouse button down in the game window for {seconds} (xdotool on the virtual display), with {keys} held
    from a moment before to a moment after (Shift_L to sneak); False when that isn't possible."""
    if not shutil.which("xdotool"):
        return False
    held = []
    try:
        found = subprocess.run(["xdotool", "search", "--name", "Minecraft"], capture_output=True, text=True, timeout=10)
        window = (found.stdout.split() or [None])[0]
        if window:
            subprocess.run(["xdotool", "windowfocus", "--sync", window], check=False, timeout=10)
        for key in keys:
            if subprocess.run(["xdotool", "keydown", key], check=False, timeout=10).returncode == 0:
                held.append(key)
        if keys:
            time.sleep(0.5)
        if subprocess.run(["xdotool", "mousedown", str(button)], check=False, timeout=10).returncode != 0:
            return False
        time.sleep(seconds)
        subprocess.run(["xdotool", "mouseup", str(button)], check=False, timeout=10)
        return len(held) == len(keys)
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f"couldn't hold mouse button {button}: {error}")
        return False
    finally:
        if held:
            time.sleep(0.3)
        for key in held:
            subprocess.run(["xdotool", "keyup", key], check=False, timeout=10)


def press(key):
    """Types a key into the game window (xdotool on the virtual display); False when that isn't possible."""
    if not shutil.which("xdotool"):
        return False
    try:
        found = subprocess.run(["xdotool", "search", "--name", "Minecraft"], capture_output=True, text=True, timeout=10)
        window = (found.stdout.split() or [None])[0]
        if window:
            subprocess.run(["xdotool", "windowfocus", "--sync", window], check=False, timeout=10)
        return subprocess.run(["xdotool", "key", key], check=False, timeout=10).returncode == 0
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f"couldn't press {key}: {error}")
        return False


def capture(folder, name):
    """The whole virtual screen, as a PNG: ffmpeg when the machine has it, else ImageMagick's import."""
    path = os.path.join(folder, f"bloom_{name}.png")
    if shutil.which("ffmpeg"):
        command = ["ffmpeg", "-loglevel", "error", "-y", "-f", "x11grab", "-video_size", "1280x720", "-i", os.environ.get("DISPLAY", ":99"),
                   "-frames:v", "1", path]
    else:
        command = ["import", "-window", "root", path]
    try:
        subprocess.run(command, check=False, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f"couldn't capture the screen: {error}")
    return os.path.exists(path)


def setting_now(path, key, value):
    """Whether a config file (after the run: the mod has read it and may have written it back) still has the value."""
    if not os.path.exists(path):
        return False, "no file"
    text = open(path, encoding="utf-8", errors="replace").read()
    if path.endswith(".json"):
        try:
            node = json.loads(text)
            for part in key.split("/"):
                node = node[part]
        except (ValueError, KeyError, TypeError):
            return False, "no " + key
        return node == json.loads(value), json.dumps(node)
    if path.endswith(".toml"):
        section, name = "", key
        if key.startswith("[") and "]" in key:
            section, name = key[1:key.index("]")], key[key.index("]") + 1:]
        current = ""
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("[") and "]" in stripped:
                current = ".".join(part.strip().strip('"') for part in stripped[1:stripped.rindex("]")].split("."))
            elif current == section and "=" in stripped and not stripped.startswith("#"):
                if stripped.split("=", 1)[0].strip().strip('"') == name:
                    found = stripped.split("=", 1)[1].strip()
                    return found == value, found
        return False, "no " + key
    for line in text.splitlines():
        if "=" in line and not line.strip().startswith("#") and line.split("=", 1)[0].strip() == key:
            found = line.split("=", 1)[1].strip()
            return found == value, found
    return False, "no " + key


def client_settings_check(game_dir):
    """The pack's keys and mod settings (config/bloomlevelup/pack-defaults.properties), as the client has them after the
    run: each key in options.txt, nothing else on the dodge's key, each setting in its file, each copied file as shipped."""
    failures, notes = [], []
    defaults_file = os.path.join(game_dir, "config", "bloomlevelup", "pack-defaults.properties")
    if not os.path.exists(defaults_file):
        return failures, notes
    lines = open(defaults_file, encoding="utf-8").read().splitlines()
    options_file = os.path.join(game_dir, "options.txt")
    bound = {}
    if os.path.exists(options_file):
        for line in open(options_file, encoding="utf-8", errors="replace").read().splitlines():
            if line.startswith("key_") and ":" in line:
                name, key = line[4:].split(":", 1)
                bound[name] = key
    keys = next((l.split("=", 1)[1] for l in lines if l.startswith("keys=")), "")
    wanted = {}
    for entry in filter(None, keys.split("|")):
        name, _, key = entry.partition(":key.")
        wanted[name] = "key." + key
    mods = os.listdir(os.path.join(game_dir, "mods"))
    present = {name for name in wanted if name in bound}
    wrong = [f"{name} is {bound[name]}, not {key}" for name, key in wanted.items() if name in bound and bound[name] != key]
    if wrong:
        failures.append("client keys not as the pack sets them: " + "; ".join(wrong))
    for name, key in wanted.items():
        if key != "key.keyboard.unknown":
            # Keys the pack itself gives to several bindings (one per armor slot, Soulslike's weapon actions) are shared on purpose.
            others = sorted(other for other, k in bound.items() if k.split(":")[0] == key.split(":")[0] and other != name
                            and k.split(":")[1:] == key.split(":")[1:] and wanted.get(other) != key)
            if others and name in bound:
                failures.append(f"{key} is {name}'s, but {', '.join(others)} start(s) on it too")
    # Keys that must do one thing only, by the client's live key list (every mod's binding, from Bloom's tooltip dump;
    # options.txt only has the keys the pack set): the dodge roll, Bloom's weapon skill and voice chat's push-to-talk. A binding that only acts in
    # a screen (GUI, JEI's hover contexts) or another context (Book of Dragons' taming) doesn't count.
    tips = os.path.join(game_dir, "tooltips.jsonl")
    if os.path.exists(tips):
        live = [json.loads(l) for l in open(tips, encoding="utf-8", errors="replace") if '"kind":"key"' in l.replace(" ", "")]
        alone = ["keybinds.combatroll.roll", "key.bloomlevelup.weapon_skill", "key.push_to_talk"]
        # The backpack's open key (B): Tom's Simple Storage's Open Terminal took it too (2026-11-19).
        alone += [r["id"] for r in live if "sophisticatedbackpacks" in r.get("id", "") and r["id"].endswith("open_backpack")]
        for name in alone:
            mine = next((r for r in live if r.get("id") == name), None)
            if mine is None or mine.get("key") == "key.keyboard.unknown":
                continue
            if "clashes" in mine:  # Bloom's dump: same key, modifier and an overlapping context, so only one fires
                others = sorted(mine["clashes"])
            else:
                others = sorted(r["id"] for r in live if r.get("id") != name and r.get("key") == mine.get("key")
                                and r.get("modifier", "NONE") == mine.get("modifier", "NONE") and r.get("context") in ("IN_GAME", "UNIVERSAL"))
            if others:
                failures.append(f"{mine['key']} should be {name}'s alone, but {', '.join(others)} use(s) it too")
        # Every key two bindings fight over, for the client-warnings step (keys the pack shares on purpose left out).
        fights = collections.defaultdict(set)
        for r in live:
            for other in r.get("clashes", []):
                if wanted.get(r["id"]) is None or wanted.get(r["id"]) != wanted.get(other):
                    fights[(r["key"], r.get("modifier", "NONE"))].update((r["id"], other))
        with open("key-clashes.txt", "w") as out:
            out.write("\n".join(f"{key}{'' if mod == 'NONE' else ' + ' + mod}: {', '.join(sorted(ids))}"
                                 for (key, mod), ids in sorted(fights.items())) or "none")
    checked, kept = 0, 0
    for line in lines:
        word, _, rest = line.partition("=")
        if word not in ("setting", "patch") or rest.count("|") < 2:
            continue
        path, key, value = rest.split("|", 2)
        target = os.path.join(game_dir, "config", path)
        if word == "patch" and not os.path.exists(target):
            continue
        checked += 1
        ok, found = setting_now(target, key, value)
        if ok:
            kept += 1
        else:
            failures.append(f"client setting not kept: {path} {key} should be {value}, is {found}")
    copies = 0
    for line in lines:
        if line.startswith("copy=") and "|" in line:
            source, target = line[5:].split("|", 1)
            source, target = os.path.join(game_dir, "config", source), os.path.join(game_dir, "config", target)
            copies += 1
            same = os.path.exists(target) and open(target, "rb").read() == open(source, "rb").read()
            if not same and os.path.exists(target) and target.endswith(".json"):
                try:  # the mod may write the same thing back in its own layout
                    same = json.load(open(target, encoding="utf-8")) == json.load(open(source, encoding="utf-8"))
                except ValueError:
                    pass
            if not same:
                failures.append(f"client file not as the pack ships it after the run: {os.path.relpath(target, game_dir)}")
    notes.append(f"client presets: {len(present)}/{len(wanted)} keys in options.txt, {kept}/{checked} settings kept, {copies} files copied"
                 + ("" if any(m.startswith("combatroll") for m in mods) else " (no Combat Roll in this layer)"))
    return failures, notes


def radar_shot(server, player, shots):
    """The minimap with one of each radar color around the player, everything else nearby cleared first: a zombie 20
    blocks east (hostile, red), a cow west (passive, green), a wolf north and an iron golem south (neutral, yellow; 20
    blocks out, clear of the player's own arrow in the middle), and a dropped item and an arrow (both hidden). The mobs stand still and are removed after."""
    server.send(f"execute as {player} at @s run kill @e[type=!player,distance=..64]")
    time.sleep(1)
    for command in (f"summon minecraft:zombie ~20 ~ ~ {{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:cow ~-20 ~ ~ {{NoAI:1b,Silent:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:wolf ~ ~ ~-20 {{NoAI:1b,Silent:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:item ~6 ~ ~14 {{Item:{{id:\"minecraft:diamond\",Count:1b}},PickupDelay:32767,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:arrow ~-14 ~ ~14 {{NoGravity:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:iron_golem ~ ~ ~20 {{NoAI:1b,Silent:1b,Tags:[\"lwx_radar\"]}}"):
        server.send(f"execute as {player} at @s run {command}")
        time.sleep(0.2)
    time.sleep(4)
    shot = capture(shots, "pack_minimap")
    server.send("kill @e[tag=lwx_radar]")
    time.sleep(1)
    return shot


def alive(server, player):
    """Whether the player is alive (not on the death screen): @e only finds living entities. True if the server didn't say."""
    mark = len(server.lines)
    server.send(f"execute if entity @e[type=minecraft:player,name={player}]")
    line = lines_since(server, mark, r"Test (passed|failed)", 10)
    return line is None or "passed" in line


def respawn_if_dead(server, player):
    """The checks before can get the player killed (the bands and sentinels come for them once the mob check puts them
    back in survival); a player on the death screen has no Bloom profile until they respawn. Respawns them from the death
    screen (Tab to its Respawn button, Enter). Returns a note, or None if they were alive."""
    if alive(server, player):
        return None
    for attempt in range(3):
        press("Tab")
        time.sleep(0.5)
        press("Return")
        time.sleep(3)
        if alive(server, player):
            return f"the player had died before the pet screen: respawned ({attempt + 1} tries)"
    return "the player had died before the pet screen and didn't respawn"


def lines_since(process, mark, pattern, limit):
    """The first line from index {mark} on matching {pattern}, waiting up to {limit} seconds; None if none came."""
    end = time.time() + limit
    while time.time() < end:
        for line in process.lines[mark:]:
            if re.search(pattern, line):
                return line
        time.sleep(0.5)
    return None


def band_check(server, player, entities, shots):
    """The LegionWareX add-on's bands, called by its command in front of the player (14 and 9 blocks ahead): a war band of 5
    and a pirate band of 4. Each must come whole, the war band led by its banner carrier and the pirate band by a rook
    (no captain); each is captured, then cleared. A band whose mod isn't in this layer is only noted. Returns (failures,
    notes)."""
    failures, notes = [], []
    wanted = {"war": ("minecraft:pillager", 5), "pirate": ("block_factorys_bosses:pirate_rook", 4)}
    server.send(f"effect give {player} minecraft:resistance 60 4 true")  # they attack at once
    for kind, (leader, size) in wanted.items():
        if leader not in entities and not leader.startswith("minecraft:"):
            notes.append(f"add-on: no {kind} band in this layer (no {leader.split(':')[0]})")
            continue
        mark = len(server.lines)
        # War bands charge at once, so they start further off; the client draws a couple of seconds behind the server.
        server.send(f"execute as {player} at @s positioned ^ ^ ^{14 if kind == 'war' else 9} run legionwarex band {kind} {size}")
        line = lines_since(server, mark, r"LegionWareX band: (no |\w+ band of)", 20)
        if line is None:
            failures.append(f"add-on: /legionwarex band {kind} said nothing (is the command there?)")
            continue
        made = re.search(r"(\w+) band of (\d+) at [^:]+: (.*)$", line)
        if not made:
            failures.append(f"add-on: no {kind} band: {line.strip()[-300:]}")
            continue
        count, members = int(made.group(2)), re.sub(r"\s*\(by command\)$", "", made.group(3).strip())
        notes.append(f"add-on: {kind} band of {count}: {members}")
        if count < size - 1:
            failures.append(f"add-on: a {kind} band of {size} came with {count} on flat ground: {members}")
        if kind == "war" and "banner" not in members:
            failures.append(f"add-on: the war band's leader carries no banner: {members}")
        if kind == "pirate" and ("pirate_captain" in members or not members.startswith("block_factorys_bosses:pirate_rook (leader")):
            failures.append(f"add-on: the pirate band should be led by a rook, with no captain: {members}")
        time.sleep(3.5 if kind == "war" else 4)
        capture(shots, f"pack_band_{kind}")
        # Still there a moment later (Easy Mob Spawn Control or another mod could take them away as they join).
        mark = len(server.lines)
        server.send(f"execute as {player} at @s run kill @e[tag=legionwarex_band,distance=..64]")
        killed = lines_since(server, mark, r"Killed (\d+) entities|Killed [^\n]+|No entity was found", 10)
        gone = re.search(r"Killed (\d+) entities", killed or "")
        if killed and "No entity was found" in killed:
            failures.append(f"add-on: the {kind} band was gone before it could be cleared (something removed it as it joined)")
        elif gone and int(gone.group(1)) < count:
            notes.append(f"add-on: {gone.group(1)} of the {kind} band's {count} were still there a few seconds later")
        time.sleep(3)
        server.send(f"execute as {player} at @s run kill @e[type=item,distance=..64]")
    server.send(f"effect clear {player}")
    return failures, notes


def chart_check(server, player, entities, shots):
    """The add-on's pirate charts: every lair of a mod in this layer must be in the game (a wrong id in the add-on's lair
    list fails). This world is flat with structures off, so no chart is read here; the boot test reads one in the
    shipped world (server-chart). Returns (failures, notes)."""
    failures, notes = [], []
    mods_here = {e.split(":")[0] for e in entities}
    mark = len(server.lines)
    server.send("legionwarex chart lairs")
    line = lines_since(server, mark, r"LegionWareX chart: \d+ lairs", 60)
    if not line:
        return ["add-on: /legionwarex chart lairs said nothing (is the command there?)"], notes
    text = line.split("LegionWareX chart: ", 1)[1].strip()
    missing = re.search(r"; not in the game \((\d+)\):(.*)$", text)
    if not missing:
        return [f"add-on: couldn't read the lair list: {text[:700]}"], notes
    # "structure" when the structure isn't there; "structure (no [boss, ...])" when it is but its bosses aren't.
    absent = [m.strip() for m in missing.group(2).split(", ") if m.strip()]
    wrong = [m for m in absent if (any(b.split(":")[0] in mods_here for b in re.findall(r"[a-z0-9_.-]+:[a-z0-9_/.-]+", m.split(" (no ", 1)[1]))
                                   if " (no " in m else m.split(":")[0] in mods_here | {"minecraft"})]
    notes.append(f"add-on: chart lairs: {text.split(';', 1)[0]}, {missing.group(1)} not in this layer")
    if wrong:
        failures.append(f"add-on: lairs in the chart list that aren't in the game though their mod is: {', '.join(wrong)}")
    return failures, notes


def sentinel_check(server, player, shots):
    """The add-on's daytime sentinels: every installed kind of its table placed in rows in front of the player (in creative,
    so they hold their posts for the picture), captured, then checked 10 seconds later in daylight: each should still be
    there and not burning (undead ones wear a sun-proof helmet). Fails when the table has installed kinds and none could
    be placed; kinds that vanished or burn are listed (pack-sentinels). Returns (failures, notes, report lines)."""
    failures, notes, report = [], [], []
    server.send(f"gamemode creative {player}")
    server.send("time set 1000")
    server.send(f"execute as {player} at @s run kill @e[type=!player,distance=..64]")
    time.sleep(1)
    mark = len(server.lines)
    server.send(f"execute as {player} at @s run legionwarex sentinel all")
    line = lines_since(server, mark, r"LegionWareX sentinel: placed", 90)
    if not line:
        server.send(f"gamemode survival {player}")
        return ["add-on: /legionwarex sentinel all said nothing (is the command there?)"], notes, report
    placed = re.search(r"placed (\d+) of (\d+) installed kinds: (.*?)(?:; couldn't place: (.*))?$", line)
    if not placed:
        server.send(f"gamemode survival {player}")
        return [f"add-on: couldn't read the sentinel line: {line.strip()[-300:]}"], notes, report
    count, kinds = int(placed.group(1)), int(placed.group(2))
    names = [n.strip() for n in placed.group(3).split(", ") if n.strip()]
    if kinds and not count:
        failures.append("add-on: no sentinel could be placed: " + line.strip()[-300:])
    if placed.group(4):
        report.append("couldn't place: " + placed.group(4))
    time.sleep(5)
    capture(shots, "pack_sentinels")
    time.sleep(5)
    mark = len(server.lines)
    server.send(f"execute as {player} at @s run legionwarex sentinel check")
    line = lines_since(server, mark, r"LegionWareX sentinel: \d+ sentinels near", 20) or ""
    rows = line.split("sentinels near: ", 1)[1].split("; ") if "sentinels near: " in line else []
    alive = {r.split(" (", 1)[0] for r in rows}
    gone = [n for n in names if n not in alive]
    burning = [r for r in rows if "burning" in r]
    notes.append(f"add-on: sentinels: {count} of {kinds} installed kinds placed; 10 s later in daylight {len(rows)} there, "
                 f"{len(burning)} burning")
    report.append("placed: " + ", ".join(names))
    report.append("10 s later: " + "; ".join(rows))
    if gone:
        report.append("gone: " + ", ".join(gone))
    if burning:
        report.append("burning: " + "; ".join(burning))
    server.send(f"execute as {player} at @s run kill @e[tag=legionwarex_sentinel,distance=..96]")
    time.sleep(2)
    server.send(f"execute as {player} at @s run kill @e[type=item,distance=..96]")
    server.send(f"gamemode survival {player}")
    return failures, notes, report


def naughtiness_check(server, server_dir):
    """The add-on's switch on Born in Chaos's Krampus meter: with the pack's config, killing animals doesn't count (only
    villagers and traders), logged once the server has started. Fails when Born in Chaos is in the layer and the add-on
    said nothing, couldn't change Born in Chaos's handler or found it still listening. Returns (failures, notes)."""
    lines = [l for l in server.lines if "LegionWareX naughtiness:" in l]
    mods = os.listdir(os.path.join(server_dir, "mods")) if os.path.isdir(os.path.join(server_dir, "mods")) else []
    if not lines:
        if any("born_in_chaos" in m.lower() for m in mods):
            return ["add-on: Born in Chaos is here but the add-on said nothing about its naughtiness (LegionWareX naughtiness:)"], []
        return [], ["add-on: naughtiness: no Born in Chaos in this layer"]
    line = lines[-1].split("LegionWareX naughtiness:", 1)[1].strip()
    if "couldn't" in line or "still on the bus" in line or "not on the bus" in line or "killing animals counts" in line:
        return [f"add-on: Born in Chaos's naughtiness from killing animals isn't off: {line[:300]}"], []
    return [], [f"add-on: naughtiness: {line[:300]}"]


def milk_check(server, server_dir):
    """The add-on's milk cure for Bad Omen (something in the pack stopped milk clearing it): at server start a stand-in
    player drinks milk with Bad Omen on, logged as "LegionWareX milk:". Fails when the add-on is here but said nothing, or
    its cure didn't take Bad Omen off. When plain milk doesn't cure it in this layer, a pack-milk warning says so, so the
    first layer it shows up in points at the mod that stops it. Returns (failures, notes)."""
    lines = [l for l in server.lines if "LegionWareX milk:" in l and ("plain milk cures" in l or "stand-in" in l)]
    mods = os.listdir(os.path.join(server_dir, "mods")) if os.path.isdir(os.path.join(server_dir, "mods")) else []
    if not lines:
        if any(m.lower().startswith("legionwarex") for m in mods):
            return ["add-on: it said nothing about milk and Bad Omen at start (LegionWareX milk:)"], []
        return [], []
    line = lines[-1].split("LegionWareX milk:", 1)[1].strip()
    if "plain milk cures Bad Omen: no" in line:
        text = line[:600].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
        print(f"::warning title=pack-milk::Plain milk doesn't take Bad Omen off in this layer: {text}")
    if "failed" in line or "couldn't" in line or not line.endswith(": yes"):
        return [f"add-on: milk doesn't take Bad Omen off: {line[:300]}"], []
    return [], [f"add-on: milk: {line[:300]}"]


def milk_drink_check(server, player):
    """A real player drinking milk with Bad Omen on, the way a player does (right-click held on the client, looking at
    the sky so no block or mob takes the click), with every mod in the layer. In creative, so nothing left around by the
    checks before can kill them mid-drink (milk still cures there; the bucket just stays full, so the drink is counted
    with the game's own statistic for milk buckets used). Fails when they drank and Bad Omen was still on 3 seconds
    later. Warns (pack-milk) when the drink didn't happen or when plain milk didn't take it off and the add-on had to
    (its "drank milk and Bad Omen was still on" line). Returns (failures, notes)."""
    def warn(text):
        print("::warning title=pack-milk::" + text[:900].replace("%", "%25").replace("\r", "").replace("\n", "%0A"))

    server.send(f"gamemode creative {player}")
    server.send("scoreboard objectives add lwx_milk minecraft.used:minecraft.milk_bucket")
    server.send(f"effect clear {player}")
    server.send(f"effect give {player} minecraft:bad_omen 600 0")
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ 0 -90")
    server.send(f"item replace entity {player} weapon.mainhand with minecraft:milk_bucket")
    time.sleep(2)
    mark = len(server.lines)
    if not hold_mouse(3, 3):
        server.send("scoreboard objectives remove lwx_milk")
        return [], ["add-on: milk drink: couldn't hold the right mouse button here"]
    time.sleep(3)
    server.send(f"scoreboard players get {player} lwx_milk")
    used = lines_since(server, mark, r"lwx_milk", 10) or ""
    after = len(server.lines)
    server.send(f"data get entity {player} ActiveEffects")
    effects = lines_since(server, after, r"has the following entity data|Found no elements matching", 10) or ""
    cured = next((l for l in server.lines[mark:] if "drank milk and Bad Omen was still on" in l), None)
    server.send(f"effect clear {player}")
    server.send(f"clear {player} minecraft:milk_bucket")
    server.send("scoreboard objectives remove lwx_milk")
    drank = re.search(r"has [1-9]\d* \[lwx_milk\]", used) is not None
    still = "bad_omen" in effects or re.search(r"Id: 31\b", effects) is not None
    if not drank:
        warn(f"The player's milk drink didn't happen ({used.strip()[-120:] or 'no answer'}); Bad Omen after: {'on' if still else 'off'}")
        return [], [f"add-on: milk drink: didn't happen ({used.strip()[-80:]})"]
    if still:
        warn(f"The player drank milk and Bad Omen was still on 3 s later{' (the add-on had taken it off, so something gave it back)' if cured else ''}: {effects.strip()[-300:]}")
        return [f"add-on: a player drank milk and still had Bad Omen 3 s later{' (given back after the add-on took it off)' if cured else ''}"], []
    if cured:
        line = cured.split("LegionWareX milk:", 1)[1].strip()
        warn(f"Plain milk didn't take a real player's Bad Omen off in this layer; the add-on did: {line[:300]}")
        return [], [f"add-on: milk drink: plain milk didn't, the add-on did ({line[:160]})"]
    return [], ["add-on: milk drink: a player drank milk and plain milk took Bad Omen off"]


def tentacle_check(server, player, server_dir, shots):
    """Bosses'Rise's Undying Tentacle as a player uses it (FreshOrangeSlices, 2026-11-21: on the server, sneaking and
    holding it plays the charge sound and nothing appears): in creative, on open ground well away from the checks
    before, looking ahead, the client sneaks and holds right-click for 3.5 seconds (the charge is 2), then the server
    counts Ghost Tentacles, with the add-on's watch on them, so the log says whether any appeared and, if they were
    taken away, by what. Screenshot pack_tentacles. Fails when they appeared and something removed them at once; warns
    (pack-tentacle) when none appeared or the input couldn't be sent. Returns (failures, notes)."""
    mods_dir = os.path.join(server_dir, "mods")
    if not any(m.startswith("block_factorys_bosses") for m in os.listdir(mods_dir)):
        return [], []

    def warn(text):
        print("::warning title=pack-tentacle::" + text[:3000].replace("%", "%25").replace("\r", "").replace("\n", "%0A"))

    ghost = "block_factorys_bosses:ghost_tentacle"
    mark = len(server.lines)
    server.send(f"data get entity {player} Pos")
    pos_line = lines_since(server, mark, r"has the following entity data: \[", 10) or ""
    back = re.findall(r"(-?[0-9.]+)d", pos_line.split("entity data:", 1)[-1])[:3]
    server.send(f"gamemode creative {player}")
    server.send(f"legionwarex watch {ghost}")
    server.send(f"execute at {player} run spreadplayers ~120 ~ 0 1 false {player}")
    time.sleep(3)
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ 0 0")
    server.send(f"kill @e[type={ghost}]")
    server.send(f"item replace entity {player} weapon.mainhand with block_factorys_bosses:undying_tentacle")
    time.sleep(2)
    mark = len(server.lines)
    sent = hold_mouse(3, 3.5, keys=("Shift_L",))
    time.sleep(0.5)
    server.send(f"execute if entity @e[type={ghost}]")
    counted = lines_since(server, mark, r"Test (passed|failed)", 10) or ""
    found = re.search(r"count: (\d+)", counted)
    count = int(found.group(1)) if found else 0
    capture(shots, "pack_tentacles")
    time.sleep(1)
    watch = [line.split("LegionWareX watch:", 1)[1].strip() for line in server.lines[mark:] if "LegionWareX watch:" in line]
    server.send("legionwarex watch off")
    server.send(f"kill @e[type={ghost}]")
    server.send(f"clear {player} block_factorys_bosses:undying_tentacle")
    if len(back) == 3:
        server.send(f"tp {player} {back[0]} {back[1]} {back[2]}")
    time.sleep(1)
    seen = "; ".join(watch)[:1500] or "the watch saw none appear"
    if not sent:
        warn(f"couldn't send the sneak and right-click to the client; tentacles: {count}; watch: {seen}")
        return [], [f"tentacles: the input couldn't be sent ({count} there)"]
    if count >= 4:
        return [], [f"tentacles: a player's Undying Tentacle summoned {count}"]
    appeared = [w for w in watch if " appeared at " in w]
    removed = [w for w in watch if " was removed after " in w and not w.split(" after ", 1)[1].startswith(tuple(f"{n} ticks" for n in range(40, 400)))]
    if appeared and removed:
        warn(f"Ghost Tentacles appeared and were taken away at once: {seen}")
        return [f"tentacles: {len(appeared)} appeared and were removed straight away ({removed[0][:200]})"], []
    warn(f"A player's Undying Tentacle summoned {count} Ghost Tentacle(s) (4 expected); watch: {seen}")
    return [], [f"tentacles: {count} of 4 appeared; watch: {seen[:200]}"]


def enchantment_check(server):
    """The enchantments the pack switches off (the add-on's deadEnchantments: Spell Engine's Spell Infinity, Skarrier Mobs'
    Bluntness, Mass and Mobility; FreshOrangeSlices, 2026-11-22): /legionwarex enchantments rolls the enchanting table
    2000 times, the librarian's book trade 500 and takes them off a loot book. Fails when the table or a trade still
    gives one, or loot keeps one. Returns (failures, notes)."""
    mark = len(server.lines)
    server.send("legionwarex enchantments")
    line = lines_since(server, mark, r"LegionWareX enchantments: ", 30) or ""
    said = line.split("LegionWareX enchantments: ", 1)[-1].strip()
    if not said:
        return [], ["enchantments: the add-on said nothing"]
    failures = []
    table = re.search(r"the table: (\d+) of", said)
    trades = re.search(r"trades: (\d+) of", said)
    if table and int(table.group(1)):
        failures.append(f"enchantments: the table still gave a switched-off one ({said[:300]})")
    if trades and int(trades.group(1)):
        failures.append(f"enchantments: a trade still sold a switched-off one ({said[:300]})")
    if "NOT taken off" in said:
        failures.append(f"enchantments: loot kept a switched-off one ({said[:300]})")
    return failures, [f"enchantments: {said[:400]}"]


def wand_check(server, player, server_dir, shots):
    """Construction Wand as a player uses it (FreshOrangeSlices, 2026-11-22: build assist for survival): in survival with
    Resistance V, on open ground, a 3x3 patch of cobblestone is set into the ground two blocks ahead; the player, with a
    stone wand and 64 cobblestone, looks at the patch and right-clicks. The wand copies the face it's pointed at, from
    the player's own blocks, so the cobblestone they carry goes down. The wand's use statistic says whether the click
    landed. Screenshot pack_wand. Fails when the click landed and nothing was placed; warns (pack-wand) when it didn't
    land. Returns (failures, notes)."""
    mods_dir = os.path.join(server_dir, "mods")
    if not any(m.startswith("constructionwand") for m in os.listdir(mods_dir)):
        return [], []

    def carried():
        mark = len(server.lines)
        server.send(f"clear {player} minecraft:cobblestone 0")
        found = re.search(r"Found (\d+) matching", lines_since(server, mark, r"matching|No items were found", 10) or "")
        return int(found.group(1)) if found else 0

    mark = len(server.lines)
    server.send(f"data get entity {player} Pos")
    pos_line = lines_since(server, mark, r"has the following entity data: \[", 10) or ""
    back = re.findall(r"(-?[0-9.]+)d", pos_line.split("entity data:", 1)[-1])[:3]
    server.send(f"effect give {player} minecraft:resistance 60 4 true")
    server.send(f"gamemode survival {player}")
    server.send(f"execute at {player} run spreadplayers ~-120 ~120 0 1 false {player}")
    time.sleep(3)
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ 0 35")
    server.send(f"execute at {player} run fill ~-1 ~-1 ~1 ~1 ~-1 ~3 minecraft:cobblestone")
    server.send(f"execute at {player} run fill ~-1 ~ ~1 ~1 ~3 ~3 minecraft:air")
    server.send("scoreboard objectives add lwx_wand minecraft.used:constructionwand.stone_wand")
    server.send(f"scoreboard players set {player} lwx_wand 0")
    server.send(f"item replace entity {player} weapon.mainhand with constructionwand:stone_wand")
    server.send(f"give {player} minecraft:cobblestone 64")
    time.sleep(2)
    before = carried()
    sent = hold_mouse(3, 0.15)
    time.sleep(1)
    after = carried()
    capture(shots, "pack_wand")
    mark = len(server.lines)
    server.send(f"scoreboard players get {player} lwx_wand")
    used_line = lines_since(server, mark, r"lwx_wand", 10) or ""
    used = int(m.group(1)) if (m := re.search(r"has (\d+) \[lwx_wand\]", used_line)) else 0
    server.send("scoreboard objectives remove lwx_wand")
    server.send(f"clear {player} constructionwand:stone_wand")
    server.send(f"clear {player} minecraft:cobblestone")
    server.send(f"effect clear {player}")
    server.send(f"gamemode creative {player}")
    if len(back) == 3:
        server.send(f"tp {player} {back[0]} {back[1]} {back[2]}")
    time.sleep(1)
    placed = before - after
    if placed > 0:
        return [], [f"wand: a player's stone wand placed {placed} cobblestone from their inventory"]
    if not sent or not used:
        print("::warning title=pack-wand::the right-click didn't reach the game (the wand was never used)")
        return [], ["wand: the right-click didn't reach the game"]
    return [f"wand: the stone wand was used and placed nothing (cobblestone {before} then {after})"], []


def gauntlet_check(server, player, server_dir, shots):
    """Bosses'Rise's Skor Gauntlet (the Yeti's arm) as a player uses it (FreshOrangeSlices, 2026-11-22: a friend found it
    made no wall). By its code its wave of ice spikes comes as right-click is let go, only while sneaking, off its
    cooldown, with a block within reach of where the player looks. In creative, on open ground away from the checks
    before, looking at the ground two blocks ahead, the client sneaks and holds right-click for 2 seconds (the wave is
    full at 1.6) and lets go still sneaking; the server counts Bosses'Rise's ice spikes (their ids from the dump), with
    the add-on's watch on them. Screenshot pack_gauntlet. Warns (pack-gauntlet) when none came or the input couldn't
    be sent. Returns (failures, notes)."""
    mods_dir = os.path.join(server_dir, "mods")
    dump = os.path.join(server_dir, "bloom-dump.jsonl")
    if not any(m.startswith("block_factorys_bosses") for m in os.listdir(mods_dir)) or not os.path.exists(dump):
        return [], []
    spikes = sorted({r["id"] for r in (json.loads(line) for line in open(dump) if line.strip())
                     if r.get("kind") == "entity" and str(r.get("id", "")).startswith("block_factorys_bosses:ice_spike")})
    if not spikes:
        return [], ["gauntlet: no ice spike entity in the dump"]

    def warn(text):
        print("::warning title=pack-gauntlet::" + text[:3000].replace("%", "%25").replace("\r", "").replace("\n", "%0A"))

    def count():
        total = {}
        for kind in spikes:
            mark = len(server.lines)
            server.send(f"execute at {player} if entity @e[type={kind},distance=..16]")
            found = re.search(r"count: (\d+)", lines_since(server, mark, r"Test (passed|failed)", 10) or "")
            total[kind] = int(found.group(1)) if found else 0
        return total

    gauntlet = "block_factorys_bosses:ice_gauntlet"
    mark = len(server.lines)
    server.send(f"data get entity {player} Pos")
    pos_line = lines_since(server, mark, r"has the following entity data: \[", 10) or ""
    back = re.findall(r"(-?[0-9.]+)d", pos_line.split("entity data:", 1)[-1])[:3]
    server.send(f"gamemode creative {player}")
    for kind in spikes:
        server.send(f"legionwarex watch {kind}")
    server.send(f"execute at {player} run spreadplayers ~ ~120 0 1 false {player}")
    time.sleep(3)
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ 0 40")
    for kind in spikes:
        server.send(f"kill @e[type={kind}]")
    server.send(f"item replace entity {player} weapon.mainhand with {gauntlet}")
    time.sleep(2)
    mark = len(server.lines)
    sent = hold_mouse(3, 2.0, keys=("Shift_L",))
    time.sleep(0.3)
    first = count()
    capture(shots, "pack_gauntlet")
    later = count()
    most = {kind: max(first.get(kind, 0), later.get(kind, 0)) for kind in spikes}
    watch = [line.split("LegionWareX watch:", 1)[1].strip() for line in server.lines[mark:] if "LegionWareX watch:" in line]
    server.send("legionwarex watch off")
    for kind in spikes:
        server.send(f"kill @e[type={kind}]")
    server.send(f"clear {player} {gauntlet}")
    if len(back) == 3:
        server.send(f"tp {player} {back[0]} {back[1]} {back[2]}")
    time.sleep(1)
    shown = ", ".join(f"{kind.split(':')[1]} {n}" for kind, n in most.items())
    appeared = sum(1 for w in watch if " appeared at " in w)
    if not sent:
        warn(f"couldn't send the sneak and right-click to the client; spikes: {shown}")
        return [], [f"gauntlet: the input couldn't be sent ({shown})"]
    if sum(most.values()) or appeared:
        return [], [f"gauntlet: a player's Skor Gauntlet raised its wall ({shown}; {appeared} seen appearing)"]
    seen = "; ".join(watch)[:1500] or "the watch saw none appear"
    warn(f"A player's Skor Gauntlet, sneaking and looking at the ground, raised no ice spikes ({shown}); watch: {seen}")
    return [], [f"gauntlet: no wall ({shown})"]


def summon_check(server, player, server_dir, shots):
    """Bosses'Rise's Pirate Saber as a player uses it (FreshOrangeSlices, 2026-11-22: its two pirates stayed for good,
    couldn't be hurt and came at their owner with sword and bolt, alone and beside a friend). In survival with Resistance
    V (so the area's mobs can't kill the player, and mobs still pick them), on open ground looking ahead: the client
    holds right-click for 1.5 seconds (the charge is 0.7), a zombie beside the player gives the pair a fight, a second
    charge a few seconds on is still waiting (10 s since 0.4.0), and a third once 11 game seconds have passed sends the
    first pair away. The
    add-on's /legionwarex summons lists them each time. Screenshot pack_pirates. Fails when a charge the game saw (the
    saber's use statistic) summons no pair, a waiting one does, or more than two are left (a right-click that never
    reached the game, a screen left open, only warns); every target and hit the add-on refused is noted (pack-summons), with
    the calls that tried it, which name the mod pointing summons at their owner. Returns (failures, notes)."""
    mods_dir = os.path.join(server_dir, "mods")
    if not any(m.startswith("block_factorys_bosses") for m in os.listdir(mods_dir)):
        return [], []
    rook, shooter, saber = "block_factorys_bosses:pirate_rook", "block_factorys_bosses:crossbow_pirate", "block_factorys_bosses:pirate_saber"

    def listed():
        """The player's summons as the add-on lists them: [(entity, id, seconds left)]."""
        mark = len(server.lines)
        server.send("legionwarex summons")
        line = lines_since(server, mark, r"LegionWareX summons: \d+ Pirate Saber summon", 10) or ""
        return [(m.group(1), m.group(2), int(m.group(3)))
                for m in re.finditer(r"; (\S+) #(\w+) of " + re.escape(player) + r" at [-0-9 ]+, (\d+) s left", line)]

    def charge():
        sent = hold_mouse(3, 1.5)
        time.sleep(1)
        return sent, listed()

    failures, notes = [], []
    mark_all = len(server.lines)
    server.send(f"data get entity {player} Pos")
    pos_line = lines_since(server, mark_all, r"has the following entity data: \[", 10) or ""
    back = re.findall(r"(-?[0-9.]+)d", pos_line.split("entity data:", 1)[-1])[:3]
    server.send(f"effect give {player} minecraft:resistance 90 4 true")
    server.send(f"effect give {player} minecraft:regeneration 90 4 true")
    server.send(f"gamemode survival {player}")
    # The saber's own use statistic says whether a charge finished at all (a screen left open eats the right-click).
    server.send(f"scoreboard objectives add lwx_saber minecraft.used:{saber.replace(':', '.')}")
    server.send(f"scoreboard players set {player} lwx_saber 0")
    server.send(f"execute at {player} run spreadplayers ~-120 ~ 0 1 false {player}")
    time.sleep(3)
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ 0 0")
    for kind in (rook, shooter):
        server.send(f"execute at {player} run kill @e[type={kind},distance=..32]")
    server.send(f"item replace entity {player} weapon.mainhand with {saber}")
    time.sleep(2)
    sent, first = charge()
    mark = len(server.lines)
    server.send(f"scoreboard players get {player} lwx_saber")
    used_line = lines_since(server, mark, r"lwx_saber", 10) or ""
    used = int(m.group(1)) if (m := re.search(r"has (\d+) \[lwx_saber\]", used_line)) else 0
    server.send(f"execute at {player} run summon minecraft:zombie ^ ^ ^4")
    time.sleep(3)
    _, waiting = charge()
    # The wait is in game time, which a busy server runs slower than the clock: the first pair's time left says how much
    # has passed. The third charge goes once 11 game seconds have.
    end = time.time() + 45
    while time.time() < end and first:
        now = listed()
        if now and max(s for _, _, s in now) <= max(s for _, _, s in first) - 11:
            break
        time.sleep(2)
    _, third = charge()
    # They stand at the player's sides: turn to the Rook for the picture.
    server.send(f"execute as {player} at @s run tp @s ~ ~ ~ facing entity @e[type={rook},sort=nearest,limit=1] feet")
    time.sleep(2)
    capture(shots, "pack_pirates")
    time.sleep(2)
    said = [line.split("LegionWareX summons:", 1)[1].strip() for line in server.lines[mark_all:]
            if "LegionWareX summons:" in line and " Pirate Saber summon" not in line]
    for kind in (rook, shooter, "minecraft:zombie"):
        server.send(f"execute at {player} run kill @e[type={kind},distance=..32]")
    server.send(f"clear {player} {saber}")
    server.send("scoreboard objectives remove lwx_saber")
    server.send(f"effect clear {player}")
    server.send(f"gamemode creative {player}")
    if len(back) == 3:
        server.send(f"tp {player} {back[0]} {back[1]} {back[2]}")
    time.sleep(1)
    shown = lambda summons: ", ".join(f"{e.split(':')[1]} #{i} {s} s" for e, i, s in summons) or "none"
    ids = lambda summons: {i for _, i, _ in summons}
    if said:
        print("::notice title=pack-summons::" + "%0A".join(s[:600].replace("%", "%25") for s in said[:12]))
    if not sent or (not first and not used):
        why = "couldn't send the right-click to the client" if not sent else f"the right-click didn't reach the game: the saber was never used ({used_line.strip()[-160:]})"
        print("::warning title=pack-summons::" + why)
        return [], ["summons: " + why]
    if len(first) != 2:
        failures.append(f"summons: a charge of the Pirate Saber left {len(first)} summons, not 2 ({shown(first)})")
    elif ids(waiting) != ids(first):
        failures.append(f"summons: a charge a few seconds on wasn't still waiting ({shown(first)}, then {shown(waiting)})")
    elif len(third) != 2 or ids(third) & ids(first):
        failures.append(f"summons: a charge 11 s on didn't send the first pair away ({shown(waiting)}, then {shown(third)})")
    notes.append(f"summons: the Pirate Saber's pair ({shown(first)}; then {shown(waiting)}; then {shown(third)}); "
                 f"{len(said)} refusals logged" + (f", first: {said[0][:200]}" if said else ""))
    return failures, notes


def death_check(server, player):
    """A player dying and respawning: Bloom says nothing about a missing profile (the playtest log of 2026-11-21 had
    dozens of "Bloom profile asked for ... without one attached" a death, from the tick code asking the removed body).
    Keeps the inventory for it, then puts the game rule back. Fails on any such warning. Returns (failures, notes)."""
    mark = len(server.lines)
    server.send("gamerule keepInventory")
    rule = lines_since(server, mark, r"keepInventory is currently set to", 10) or ""
    keep = "true" if rule.strip().endswith("true") else "false"
    server.send("gamerule keepInventory true")
    mark = len(server.lines)
    server.send(f"kill {player}")
    time.sleep(3)
    respawned = respawn_if_dead(server, player)
    time.sleep(2)
    server.send(f"gamerule keepInventory {keep}")
    warnings = [line for line in server.lines[mark:] if "Bloom profile asked for" in line]
    if warnings:
        print("::warning title=pack-bloom-death::" + "%0A".join(w[-500:].replace("%", "%25") for w in warnings[:5]))
        return [f"bloom: {len(warnings)} missing-profile warnings around a death ({warnings[0][-240:]})"], []
    return [], [f"bloom: a death and respawn, no missing-profile warnings ({respawned or 'respawned'})"]


def krampus_check(server, player):
    """The add-on's Krampus count (Born in Chaos's meter, filled by villager kills only): the meter set to 0, then five
    villagers summoned beside the player and killed by them; the 5th should bring Krampus (logged "Krampus came"), none
    before it. Nothing to check when the add-on doesn't run the count (no Born in Chaos). Returns (failures, notes)."""
    started = [l for l in server.lines if "LegionWareX naughtiness:" in l and "Krampus at the" in l]
    if not started or "Born in Chaos's own count" in started[-1]:
        return [], []
    failures, results = [], []
    server.send(f"gamemode creative {player}")
    server.send(f"execute as {player} at @s run legionwarex naughtiness set 0")
    time.sleep(1)
    for _ in range(5):
        mark = len(server.lines)
        server.send(f"execute as {player} at @s run summon minecraft:villager ~4 ~ ~ {{NoAI:1b,Tags:[\"lwx_naughty\"]}}")
        time.sleep(1)
        server.send(f"execute as {player} at @s run damage @e[type=minecraft:villager,tag=lwx_naughty,limit=1,sort=nearest] 1000 minecraft:player_attack by {player}")
        line = lines_since(server, mark, r"LegionWareX naughtiness: \S+ killed", 10)
        results.append(line.split(" killed ", 1)[1].strip() if line else "no line")
    for n, result in enumerate(results, 1):
        if n < 5 and "Krampus" in result:
            failures.append(f"add-on: Krampus came before the 5th villager (at the {n}th: {result})")
    if "Krampus came" not in results[-1]:
        failures.append(f"add-on: the 5th villager didn't bring Krampus: {results[-1]}")
    for mob in ("born_in_chaos_v1:krampus", "born_in_chaos_v1:krampus_henchman"):
        server.send(f"execute as {player} at @s run kill @e[type={mob},distance=..32]")
    server.send(f"execute as {player} at @s run kill @e[type=item,distance=..32]")
    server.send(f"execute as {player} at @s run legionwarex naughtiness set 0")
    server.send(f"gamemode survival {player}")
    return failures, ["add-on: Krampus count: " + "; ".join(f"{n}: {r}" for n, r in enumerate(results, 1))]


def harvest_check(server, player):
    """The add-on's Harvest Court (Born in Chaos's pumpkin folk all year): /legionwarex harvest at midnight and at noon,
    where the player stands. At midnight each of them should be allowed some of the 40 tries by the add-on's all-year
    rule (and by its spawn rule as a whole: in Born in Chaos's own season, 25 October to 7 November, that is its rule
    alone); at noon by neither. Nothing to check without Born in Chaos. Returns (failures, notes)."""
    failures, notes, rows = [], [], {}
    for hour, label in ((18000, "midnight"), (6000, "noon")):
        server.send(f"time set {hour}")
        time.sleep(1)
        mark = len(server.lines)
        server.send(f"execute as {player} at @s run legionwarex harvest")
        line = lines_since(server, mark, r"LegionWareX harvest: ", 20)
        if not line:
            return ["add-on: /legionwarex harvest said nothing (is the command there?)"], notes
        if "no Harvest Court mobs" in line:
            return failures, ["add-on: Harvest Court: no pumpkin folk in this layer"]
        rows[label] = re.findall(r"(\S+) (\d+)/(\d+) \(all-year rule (\d+)/\d+\)", line)
        notes.append(f"add-on: Harvest Court at {label}: " + ", ".join(f"{m.split(':')[-1]} {yes}/{n} (own {own})" for m, yes, n, own in rows[label]))
    for mob, yes, n, own in rows.get("midnight", []):
        if int(own) == 0:
            failures.append(f"add-on: Harvest Court: the all-year rule never lets {mob} out at midnight (0/{n})")
        if int(yes) == 0:
            failures.append(f"add-on: Harvest Court: {mob}'s spawn rule never says yes at midnight (0/{n}): the all-year rule isn't taking")
    for mob, yes, n, own in rows.get("noon", []):
        if int(own):
            failures.append(f"add-on: Harvest Court: the all-year rule lets {mob} out at noon ({own}/{n})")
        elif int(yes):
            # Born in Chaos's own rule, in its season, doesn't ask for the dark: Easy Mob Spawn Control's night rule
            # (the pack's config) keeps them to the night then.
            notes.append(f"add-on: Harvest Court: {mob}'s own rule allows noon in its season ({yes}/{n}); the night rule keeps it to the night")
    server.send("time set 1000")
    return failures, notes


def quest_check(server_dir, dump_file, server_lines):
    """The quest book (FTB Quests): it loaded every chapter build_pack wrote into config/ftbquests/quests (its "Loaded N
    chapter groups, N chapters, N quests" line), with no errors of its own, and every item, entity, structure and biome
    its quests name is in the game (the server's registry dump). Nothing to check in a layer without it. Returns
    (failures, notes)."""
    failures, notes = [], []
    folder = os.path.join(server_dir, "config", "ftbquests", "quests", "chapters")
    if not os.path.isdir(folder):
        return failures, notes
    written = len([f for f in os.listdir(folder) if f.endswith(".snbt")])
    loaded = next((l for l in server_lines if "chapter groups" in l and "Loaded" in l), None)
    if loaded is None:
        failures.append(f"quest book: FTB Quests never said it loaded the book ({written} chapters written)")
    else:
        counts = re.search(r"(\d+) chapters, (\d+) quests", loaded)
        notes.append("quest book: " + loaded.split("Loaded", 1)[1].strip())
        if counts and int(counts.group(1)) != written:
            failures.append(f"quest book: {written} chapters written, FTB Quests loaded {counts.group(1)}")
    errors = [l.strip() for l in server_lines if "ftbquests" in l.lower().replace(" ", "") and ("ERROR" in l or "Exception" in l)]
    if errors:
        failures.append("quest book: FTB Quests errors: " + " | ".join(errors[:4])[:900])
    if not os.path.exists(dump_file):
        return failures, notes
    rows = [json.loads(line) for line in open(dump_file) if line.strip()]
    have = {kind: {r["id"] for r in rows if r.get("kind") == kind} for kind in ("item", "entity", "structure", "biome")}
    have["structure"] |= {r["id"] for r in rows if r.get("kind") == "structure_id"}
    spaces = {i.split(":", 1)[0] for ids in have.values() for i in ids}
    used = quest_book.ids_used(os.path.join(os.path.dirname(os.path.abspath(__file__)), "quests"), spaces | quest_book.ALWAYS)
    missing = [f"{kind} {i}" for kind in have for i in sorted(used[kind]) if i not in have[kind]]
    if missing:
        failures.append(f"quest book: {len(missing)} ids the game doesn't have: " + ", ".join(missing[:40]))
    else:
        notes.append(f"quest book: all {sum(len(used[k]) for k in have)} items, mobs, structures and biomes it names are in the game")
    return failures, notes


def terrain_check(server_dir, spec_file):
    """The add-on's terrain survey (-Dlegionwarex.terrain): the Overworld the server would make beside vanilla's for the
    same seed, and where a new world's spawn goes. Fails when the survey didn't run, a spawn search found no mainland,
    or (with the pack's erosion change) the pack keeps more than 75% of both vanilla's peaks and its land over y 130. Returns
    (failures, notes, report lines)."""
    failures, notes, report = [], [], []
    path = os.path.join(server_dir, "legionwarex-terrain.jsonl")
    if not os.path.exists(path):
        return ["add-on: the terrain survey wrote nothing (legionwarex-terrain.jsonl)"], notes, report
    rows = [json.loads(line) for line in open(path) if line.strip()]
    for row in rows:
        report.append(json.dumps(row))
        if row.get("kind") == "error":
            failures.append(f"add-on: the terrain survey failed: {row.get('error')}")
    terrain = {row["setup"]: row for row in rows if row.get("kind") == "terrain"}
    if "pack" in terrain and "vanilla" in terrain:
        pack, vanilla = terrain["pack"], terrain["vanilla"]
        pct = lambda v: f"{v * 100:.1f}%"
        notes.append("terrain (pack vs vanilla, same seed): mountain biomes " + pct(pack["mountainBiomes"]) + " vs " + pct(vanilla["mountainBiomes"])
                     + ", peaks " + pct(pack["peakBiomes"]) + " vs " + pct(vanilla["peakBiomes"])
                     + "; land over 130 " + pct(pack["over130"]) + " vs " + pct(vanilla["over130"])
                     + ", over 160 " + pct(pack["over160"]) + " vs " + pct(vanilla["over160"])
                     + ", highest " + str(pack["landMax"]) + " vs " + str(vanilla["landMax"])
                     + "; land " + pct(pack["land"]) + " vs " + pct(vanilla["land"]))
        spec = json.load(open(spec_file))
        # The erosion change pulls in the low end, where the peaks and most high land are: the peaks or the land over
        # y 130 must fall well below vanilla's (in a seed that has some; a few high spots come from other noise, and
        # windswept hills and meadows sit higher in erosion and keep their share by design, so mountain biomes are
        # only reported). Without the change both sides are the same world, so the bar only has to sit clear of 100%:
        # across runs 324-325's twelve seeds the pack kept 0-52% of the peaks and 26-77% of the land over 130 (a mountainous
        # seed in run 325 kept 75% and 60%, just over the old 60% bar; a seed with almost no peaks in November 2026's
        # Bloom Pets runs kept 80% of its land over 130, over the 75% bar after it: now 90%, still well clear of 100%).
        some = vanilla["peakBiomes"] >= 0.003 or vanilla["over130"] >= 0.005
        fewer = pack["peakBiomes"] <= vanilla["peakBiomes"] * 0.9 or pack["over130"] <= vanilla["over130"] * 0.9
        if "minecraft:density_function/overworld/erosion" in spec.get("pack_data", {}).get("worldgen", {}) and some and not fewer:
            failures.append("terrain: the pack's erosion change left nearly as many peaks and as much land over y 130 as vanilla's ("
                            + pct(pack["peakBiomes"]) + " vs " + pct(vanilla["peakBiomes"]) + "; "
                            + pct(pack["over130"]) + " vs " + pct(vanilla["over130"]) + ")")
    else:
        failures.append("add-on: the terrain survey has no pack/vanilla rows")
    for row in [r for r in rows if r.get("kind") == "spawn"]:
        old = row.get("vanillaSearch", {})
        if not row.get("found"):
            failures.append(f"terrain: no mainland spawn for seed {row.get('seed')}")
            continue
        notes.append(f"spawn, seed {row['seed']}: {row['x']}, {row['y']}, {row['z']} ({row['biome']}, continentalness {row['continentalness']}); "
                     f"vanilla's search: {old.get('x')}, {old.get('z')} ({old.get('biome')}, {old.get('continentalness')})")
    return failures, notes, report


def main():
    server_dir, game_dir, shots = sys.argv[1], os.path.abspath(sys.argv[2]), sys.argv[3]
    os.makedirs(shots, exist_ok=True)
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    failures, notes = [], []

    # ---- the server: a fresh flat world, offline so the test client can join
    subprocess.run(["rm", "-rf", os.path.join(server_dir, "world")])
    # A mob that fails to tick is removed and logged instead of stopping the server (the mob check reports it).
    os.makedirs(os.path.join(server_dir, "defaultconfigs"), exist_ok=True)
    open(os.path.join(server_dir, "defaultconfigs", "forge-server.toml"), "w").write(
        "[server]\n\tremoveErroringBlockEntities = true\n\tremoveErroringEntities = true\n")
    set_properties(os.path.join(server_dir, "server.properties"), {
        "online-mode": "false", "level-type": "minecraft\\:flat", "difficulty": "easy", "spawn-protection": "0",
        "generate-structures": "false", "view-distance": "6", "simulation-distance": "5",
    })
    # Bloom's registry dump (two seconds after the start), for the sorting check.
    jvm_args = os.path.join(server_dir, "user_jvm_args.txt")
    existing = open(jvm_args).read() if os.path.exists(jvm_args) else ""
    if "bloomlevelup.dump" not in existing:
        open(jvm_args, "a").write(("" if existing.endswith("\n") or not existing else "\n") + "-Dbloomlevelup.dump=bloom-dump.jsonl\n")
    # The add-on's terrain survey (the Overworld this server would make, beside vanilla's), for the terrain check.
    existing = open(jvm_args).read()
    if "legionwarex.terrain" not in existing:
        open(jvm_args, "a").write("-Dlegionwarex.terrain=legionwarex-terrain.jsonl\n")
    server = Process(["sh", "start.sh"], server_dir, stdin=True)
    if not server.wait_for(r"\]: Done \(|Done \([0-9.]+s\)!", 600):
        print(f"::error title=pack-launch::The server didn't come up. Last lines:%0A{esc(chr(10).join(server.lines[-40:])[:3800])}")
        sys.exit(1)
    progress("server up")

    # ---- the client: Forge installed by portablemc, the pack's client side already in the game folder
    options = os.path.join(game_dir, "options.txt")
    # A first start like a friend's: options.txt only has what keeps the test quiet (Bloom adds the pack's packs).
    open(options, "w").write("onboardAccessibility:false\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\n"
                             "pauseOnLostFocus:false\nsoundCategory_master:0.0\nguiScale:2\nrenderDistance:6\n")
    forge_file = os.path.join(game_dir, "forge-version.txt")
    forge = open(forge_file).read().strip() if os.path.exists(forge_file) else FORGE_FALLBACK
    launch = ["portablemc", "--main-dir", os.path.join(game_dir, ".pmc"), "start", f"forge::{forge}", "--mc-dir", game_dir, "-u", PLAYER,
              "--resolution", "1280x720", "--jvm-arg=-Xmx6G"]

    # First start: up to the title screen (it should be Minecraft's own), then closed.
    title = Process(launch, game_dir)
    if title.wait_for(r"Created: \d+x\d+x\d+ minecraft:textures/atlas/blocks\.png-atlas", TITLE_LIMIT):
        settle(title, quiet=20, limit=240)
        if not capture(shots, "pack_title"):
            failures.append("couldn't capture the title screen")
    else:
        failures.append("the client didn't reach the title screen")
        capture(shots, "pack_title")
    stop(title)
    progress("title screen done")

    # Second start: straight into the server.
    # Bloom's tooltip dump (ten seconds in): every weapon's tooltip as a player sees it, for sorting the pack's weapons.
    client = Process(launch + [f"--jvm-arg=-Dbloomlevelup.tooltips={os.path.join(game_dir, 'tooltips.jsonl')}", "--join-server", "127.0.0.1"], game_dir)
    joined = server.wait_for(rf"{PLAYER} joined the game", JOIN_LIMIT)
    started = time.time()

    progress("joined" if joined else "didn't join")
    if joined:
        for command in SCENE:
            server.send(command)
            time.sleep(0.3)
        time.sleep(25)  # chunks, entity models and the animations settle
        for n in range(3):
            if not capture(shots, f"pack_{n}"):
                failures.append("couldn't capture the screen")
            time.sleep(4)
        server.send(f"execute as {PLAYER} at @s run tp @s ~ ~ ~ 180 15")
        time.sleep(4)
        capture(shots, "pack_back")
        radar_shot(server, PLAYER, shots)
        # The inventory: where Bloom's buttons sit next to what other mods put around it, with a status effect showing
        # (the game draws effects outside the panel's right edge, beside JEI's item list).
        server.send(f"effect give {PLAYER} minecraft:resistance 60 0 true")
        time.sleep(1)
        if press("e"):
            time.sleep(3)
            capture(shots, "pack_inventory")
            press("e")
            time.sleep(1)
        server.send(f"effect clear {PLAYER}")
        server.send(f"execute as {PLAYER} at @s run tp @s ~ ~ ~ 0 8")
        entities, with_egg = modded_entities(os.path.join(server_dir, "mods"))
        progress(f"mob check: {len(entities)} modded entities")
        mob_failures, mob_warnings, summoned = mob_check(server, client, PLAYER, entities, with_egg, shots, server_dir)
        progress(f"mob check done ({summoned} summoned)")
        failures += mob_failures
        notes.append(f"mob check: {summoned} of {len(entities)} modded entities summoned ({len(with_egg)} with a spawn egg)")
        if mob_failures or mob_warnings:
            text = chr(10).join(mob_failures + mob_warnings)
            for n, i in enumerate(range(0, min(len(text), 3900 * 3), 3900)):
                print(f"::warning title=pack-mobs-{n}::{esc(text[i:i + 3900])}")
        if any(f.startswith("legionwarex-addon") for f in os.listdir(os.path.join(server_dir, "mods"))):
            server.send(f"execute as {PLAYER} at @s run tp @s ~ ~ ~ 0 8")
            # The mob check can leave things on the ground (fire, webs, a block a mob put down, a crater): the bands and
            # sentinels get the flat world's ground back first, so a band left short says something about the band.
            server.send(f"execute as {PLAYER} at @s run fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 minecraft:grass_block")
            server.send(f"execute as {PLAYER} at @s run fill ~-24 ~ ~-24 ~24 ~8 ~24 minecraft:air")
            server.send(f"execute as {PLAYER} at @s run kill @e[type=!minecraft:player,distance=..48]")
            time.sleep(2)
            band_failures, band_notes = band_check(server, PLAYER, entities, shots)
            failures += band_failures
            notes += band_notes
            progress("band check done")
            chart_failures, chart_notes = chart_check(server, PLAYER, entities, shots)
            failures += chart_failures
            notes += chart_notes
            progress("chart check done")
            sentinel_failures, sentinel_notes, sentinel_report = sentinel_check(server, PLAYER, shots)
            failures += sentinel_failures
            notes += sentinel_notes
            if sentinel_report:
                text = chr(10).join(sentinel_report)
                for n, i in enumerate(range(0, min(len(text), 3900 * 2), 3900)):
                    print(f"::notice title=pack-sentinels-{n}::{esc(text[i:i + 3900])}")
            progress("sentinel check done")
            harvest_failures, harvest_notes = harvest_check(server, PLAYER)
            failures += harvest_failures
            notes += harvest_notes
            progress("harvest check done")
            naughty_failures, naughty_notes = naughtiness_check(server, server_dir)
            failures += naughty_failures
            notes += naughty_notes
            milk_failures, milk_notes = milk_check(server, server_dir)
            failures += milk_failures
            notes += milk_notes
            drink_failures, drink_notes = milk_drink_check(server, PLAYER)
            failures += drink_failures
            notes += drink_notes
            progress("milk check done")
            tentacle_failures, tentacle_notes = tentacle_check(server, PLAYER, server_dir, shots)
            failures += tentacle_failures
            notes += tentacle_notes
            progress("tentacle check done")
            summon_failures, summon_notes = summon_check(server, PLAYER, server_dir, shots)
            failures += summon_failures
            notes += summon_notes
            progress("summon check done")
            gauntlet_failures, gauntlet_notes = gauntlet_check(server, PLAYER, server_dir, shots)
            failures += gauntlet_failures
            notes += gauntlet_notes
            progress("gauntlet check done")
            enchantment_failures, enchantment_notes = enchantment_check(server)
            failures += enchantment_failures
            notes += enchantment_notes
            progress("enchantment check done")
            wand_failures, wand_notes = wand_check(server, PLAYER, server_dir, shots)
            failures += wand_failures
            notes += wand_notes
            progress("wand check done")
            krampus_failures, krampus_notes = krampus_check(server, PLAYER)
            failures += krampus_failures
            notes += krampus_notes
            progress("krampus check done")
        # The quest book, opened the way its command does (the inventory's book button opens the same screen).
        if os.path.isdir(os.path.join(server_dir, "config", "ftbquests", "quests")):
            server.send(f"execute as {PLAYER} run ftbquests open_book")
            time.sleep(4)
            capture(shots, "pack_quests")
            press("Escape")
            time.sleep(1)
            progress("quest book shown")
        # Bloom Pets' screen, with a wolf (in the party, level 40 with some XP: skills open up to Moonblessed), a cow
        # and a fox given to the player; peaceful first, so what the checks above left around can't kill them.
        if any(f.startswith("bloompets") for f in os.listdir(os.path.join(server_dir, "mods"))):
            server.send("difficulty peaceful")
            time.sleep(2)
            revived = respawn_if_dead(server, PLAYER)
            if revived:
                progress(revived)
                notes.insert(0, "bloom pets: " + revived)
                if "didn't" in revived:
                    failures.append("bloom pets: " + revived)
            for species in ("wolf", "cow", "fox"):
                server.send(f"bloompets give {species} {PLAYER}")
            time.sleep(2)
            server.send(f"execute as {PLAYER} run bloompets party 1")
            time.sleep(1)
            server.send(f"execute as {PLAYER} run bloompets level 40 1")
            server.send(f"execute as {PLAYER} run bloompets xp 100 1")
            time.sleep(1)
            server.send(f"execute as {PLAYER} run bloompets screen")
            time.sleep(4)
            capture(shots, "pack_pets")
            press("Escape")
            time.sleep(1)
            progress("pet screen shown")
            # One and the Same (Bloom's Combat Major, 3 points): the wolf follows the Sword tree, shown in the screen's
            # Bloom view (Slash and Full Sweep open at its level 40, Execute and Aura Sword still dim).
            mark = len(server.lines)
            server.send(f"bloom admin majorlevels {PLAYER} 3")
            server.send(f"execute as {PLAYER} run bloom buy bloomlevelup:combat/one_and_the_same")
            time.sleep(2)
            server.send(f"execute as {PLAYER} run bloompets tree sword 1")
            time.sleep(2)
            server.send(f"execute as {PLAYER} run bloompets screen bloom")
            time.sleep(4)
            capture(shots, "pack_pets_bloom")
            press("Escape")
            time.sleep(1)
            # What the server said to all that (Bloom's answers to the Major's purchase, the tree's, and Bloom Pets' line
            # on what Bloom told it), in the milestones (the summary is cut at 3900 characters, too late for these).
            said = [line for line in server.lines[mark:] if line.strip() and "Can't keep up" not in line]
            for line in said[:10]:
                progress("pets: " + line[-220:])
            bridge = next((line for line in server.lines if "Bloom Pets: Bloom's API" in line), None)
            if bridge and "not found" in bridge:
                failures.append("bloom pets: " + bridge[-200:])
            view = next((line for line in said if "opens the pet screen's Bloom view" in line), "")
            if "One and the Same off" in view:
                failures.append("bloom pets: the Bloom view didn't open: " + view[-200:])
            progress("pet screen's Bloom view shown")
        # Last, as it moves the player to the world's spawn: a death and a respawn.
        death_failures, death_notes = death_check(server, PLAYER)
        failures += death_failures
        notes += death_notes
        progress("death check done")
    else:
        failures.append("the client didn't join the server")
        capture(shots, "pack_0")

    # ---- Bloom's view of the pack's gear and bosses
    sorting, unsorted = bloom_sorting(os.path.join(server_dir, "bloom-dump.jsonl"))
    notes.append(sorting)
    if unsorted:
        print(f"::warning title=pack-bloom-unsorted::{esc(chr(10).join(unsorted)[:3900])}")
    enchantment_list(os.path.join(server_dir, "bloom-dump.jsonl"))
    # ---- materials a trim left with nothing to make (reviewed by hand: some are drops with uses outside recipes)
    leftover_note, leftovers = leftover_check(os.path.join(server_dir, "bloom-dump.jsonl"),
                                              os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"), server_dir)
    notes.append(leftover_note)
    if leftovers:
        text = chr(10).join(leftovers)
        for n, i in enumerate(range(0, min(len(text), 3900 * 3), 3900)):
            print(f"::warning title=pack-leftovers-{n}::{esc(text[i:i + 3900])}")
    # ---- the quest book: loaded, and naming only what the game has
    quest_failures, quest_notes = quest_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"), server.lines)
    failures += quest_failures
    notes += quest_notes
    # ---- voice chat: Simple Voice Chat's server came up on its UDP port (the client side can't talk on CI: no microphone)
    if any(f.startswith("voicechat") for f in os.listdir(os.path.join(server_dir, "mods"))):
        started = next((l for l in server.lines if "Voice chat server started" in l), None)
        failed = next((l for l in server.lines if "Failed to start voice chat server" in l), None)
        if failed or not started:
            failures.append("voice chat: its server didn't start: " + (failed or "no 'Voice chat server started' line").strip()[-300:])
        else:
            notes.append("voice chat: " + started.split("]: ", 1)[-1].strip())
    # ---- the pack's own data pack: recipes out, items hidden from JEI
    data_failures, data_notes = pack_data_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"), server.lines)
    failures += data_failures
    notes += data_notes
    # ---- the pack's natural spawns: what each biome spawns, and how much of it is modded
    spawn_failures, spawn_notes, spawn_report = spawn_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"),
                                                            os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"))
    failures += spawn_failures
    notes += spawn_notes
    voice_failures, voice_notes = voice_check(server_dir, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"))
    failures += voice_failures
    notes += voice_notes
    carry_failures, carry_notes = carry_on_check(server_dir)
    failures += carry_failures
    notes += carry_notes
    # Bloom Pets (pets/, packed in like the add-on): loaded, with its roster read.
    if any(f.startswith("bloompets") for f in os.listdir(os.path.join(server_dir, "mods"))):
        said = next((l for l in server.lines if re.search(r"Bloom Pets: \d+ species", l)), None)
        if said is None:
            failures.append("bloom pets: the server never said how many species it read (not loaded?)")
        else:
            count = int(re.search(r"Bloom Pets: (\d+) species", said).group(1))
            (failures if count == 0 else notes).append(f"bloom pets: {count} species in the roster")
    # The LegionWareX add-on (addon/, packed in like Bloom): loaded, with its keepsake and its companion tag.
    if any(f.startswith("legionwarex-addon") for f in os.listdir(os.path.join(server_dir, "mods"))):
        dump_rows = [json.loads(line) for line in open(os.path.join(server_dir, "bloom-dump.jsonl")) if line.strip()] \
            if os.path.exists(os.path.join(server_dir, "bloom-dump.jsonl")) else []
        keepsake = any(r.get("kind") == "item" and r.get("id") == "legionwarex:companion_keepsake" for r in dump_rows)
        companions = sorted(r["id"] for r in dump_rows if r.get("kind") == "entity" and "legionwarex:companions" in r.get("tags", []))
        has_migamigos = any(r.get("kind") == "entity" and r.get("id", "").startswith("migamigos:") for r in dump_rows)
        if not keepsake:
            failures.append("add-on: the companion keepsake isn't registered (the LegionWareX add-on didn't load)")
        elif has_migamigos and len(companions) != 5:
            failures.append(f"add-on: its companion tag has {companions}, not Migamigos' five")
        else:
            notes.append(f"add-on: loaded; companions it keeps: {', '.join(companions) or 'none in this layer'}")
        terrain_failures, terrain_notes, terrain_report = terrain_check(server_dir, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"))
        failures += terrain_failures
        notes += terrain_notes
        if terrain_report:
            text = chr(10).join(terrain_report)
            for n, i in enumerate(range(0, min(len(text), 3900 * 2), 3900)):
                print(f"::notice title=pack-terrain-{n}::{esc(text[i:i + 3900])}")
    # Easy Mob Spawn Control's files from the pack (Shybroom's village spawns, boss despawn rules and caps) must load.
    emsc = [l for l in server.lines if ("EasyMSC" in l or "easymobspawncontrol" in l.lower()) and ("Failed to read" in l or "Exception" in l)]
    if emsc:
        failures.append("Easy Mob Spawn Control couldn't read the pack's files: " + " | ".join(emsc[:4])[:900])
    if spawn_report:
        text = chr(10).join(spawn_report)
        for n, i in enumerate(range(0, min(len(text), 3900 * 3), 3900)):
            print(f"::notice title=pack-spawns-{n}::{esc(text[i:i + 3900])}")

    # ---- what the client did (both starts)
    client_exited = client.process.poll() is not None  # by itself, before the test stops it
    client_log = title.lines + client.lines
    applied = next((l for l in client_log if "Bloom Pack defaults applied" in l), None)
    packs_line = next((l for l in open(options).read().splitlines() if l.startswith("resourcePacks:")), "") if os.path.exists(options) else ""
    if not applied:
        failures.append("Bloom didn't apply the pack's resource packs (no 'Bloom Pack defaults applied' line)")
    if "FreshAnimations" not in packs_line:
        failures.append(f"Fresh Animations isn't on in options.txt ({packs_line or 'no resourcePacks line'})")
    # The pack's defaults for mods that read them from defaultconfigs/ on a first start: Xaero's Minimap locked north.
    xaero_default = os.path.join(game_dir, "defaultconfigs", "xaero", "minimap", "profiles", "default.cfg")
    has_minimap = any(f.startswith("xaerominimap") for f in os.listdir(os.path.join(game_dir, "mods")))
    if os.path.exists(xaero_default) and has_minimap:
        profile = os.path.join(game_dir, "config", "xaero", "minimap", "profiles", "default.cfg")
        text = open(profile).read() if os.path.exists(profile) else ""
        if not re.search(r"(?m)^minimap_north_locked\s*=\s*true\s*$", text):
            found = sorted(os.listdir(os.path.dirname(profile))) if os.path.isdir(os.path.dirname(profile)) else "no profiles folder"
            failures.append(f"Xaero's Minimap didn't take the pack's default (north locked): {found}; "
                            + " / ".join(l for l in text.splitlines() if "north" in l or "profile" in l)[:300])
        else:
            notes.append("minimap: locked north (from the pack's defaultconfigs)")
    # The pack's keys and mod settings, as the client has them after the run (a mod may have rewritten its file).
    setting_failures, setting_notes = client_settings_check(game_dir)
    failures += setting_failures
    notes += setting_notes
    # The pack's shader, when it has one: switched on in Oculus's settings, and what Oculus said about it.
    defaults_file = os.path.join(game_dir, "config", "bloomlevelup", "pack-defaults.properties")
    wanted = next((l.split("=", 1)[1] for l in open(defaults_file).read().splitlines() if l.startswith("shaderPack=")), None) \
        if os.path.exists(defaults_file) else None
    if wanted:
        oculus = os.path.join(game_dir, "config", "oculus.properties")
        settings = open(oculus).read() if os.path.exists(oculus) else ""
        said = [l for l in client_log if re.search(r"(?i)\[oculus|\[iris|shaderpack|shader pack|shader program|pipeline", l)
                and "are modifying Embeddium" not in l]
        notes.append("shader: " + (" / ".join(dict.fromkeys(said[:6])) if said else f"{wanted} (Oculus said nothing about it)")[:900])
        if f"shaderPack={wanted}" not in settings or "enableShaders=true" not in settings:
            # Bloom writes the file before the game starts; Oculus may switch shaders off where they can't run (this
            # test machine draws with a software renderer), so that's a warning with what the file says, not a failure.
            print(f"::warning title=pack-shader::Oculus's settings don't have {wanted} on after the run:%0A"
                  + esc(" / ".join(l for l in settings.splitlines() if re.match(r"(enableShaders|shaderPack)=", l)) or "no oculus.properties"))
    # A crash is the game stopping by itself or writing a crash report file. (Forge's removeErroringEntities, which the
    # server sends to clients too, logs a whole crash report for an entity it removes and carries on: those are listed.)
    reports = os.listdir(os.path.join(game_dir, "crash-reports")) if os.path.isdir(os.path.join(game_dir, "crash-reports")) else []
    if reports or client_exited or re.search(r"#@!@# Game crashed", "\n".join(client_log)):
        failures.append("the client crashed:\n" + crash_summary(game_dir, client_log[-200:]))
    removed = sorted(set(re.findall(r"Entity Type: ([a-z0-9_.-]+:[a-z0-9_/.-]+)", "\n".join(client.lines))))
    if removed:
        notes.append("removed on the client after an error: " + ", ".join(removed))

    stop(client)
    server.send("stop")
    try:
        server.process.wait(timeout=120)
    except subprocess.TimeoutExpired:
        server.process.kill()

    # ---- report
    mods = next((l for l in client_log if re.search(r"Found \d+ mod|Loading \d+ mods|Mod List", l)), "")
    summary = [f"joined: {'yes' if joined else 'no'}", applied or "no pack defaults line", packs_line, mods.strip()] + notes
    print(f"::notice title=pack-launch::{esc(chr(10).join(summary)[:3900])}")
    # The checks' notes whole (the summary above stops at 3900 characters, which the presets line nearly fills), for
    # the workflow's next step to print as pack-notes-NN.
    with open("pack-notes.txt", "w") as out:
        out.write("\n".join(notes) + "\n")
    client_warnings(client_log)
    problems = [l for l in client_log if re.search(r"/ERROR\]|Exception|Caused by|Mixin apply failed|InvalidInjection|Missing or unsupported", l)]
    if problems:
        text = "\n".join(dict.fromkeys(problems))
        for n, i in enumerate(range(0, min(len(text), 3900 * 6), 3900)):
            print(f"::warning title=pack-client-log-{n:02d}::{esc(text[i:i + 3900])}")
    if failures or not joined:
        tail = "\n".join(client_log[-60:])
        print(f"::error title=pack-launch-failed::{esc(('; '.join(failures) + chr(10) + tail)[:3900])}")
        sys.exit(1)


def enchantment_list(dump_file):
    """Every enchantment by mod, from the server's registry dump, written to enchantments.txt for the workflow to print
    (pack-enchantments-NN): for finding mods' enchantments that do the same thing. Each line: name, id, max level, where
    it comes from (table, villagers, or loot only), what it goes on, and its description if the mod ships one."""
    if not os.path.exists(dump_file):
        return
    byns = collections.defaultdict(list)
    with open(dump_file) as f:
        for line in f:
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if r.get("kind") != "enchantment" or "id" not in r:
                continue
            ns, path = r["id"].split(":", 1)
            where = ("curse" if r.get("curse") else "loot only" if r.get("treasure") or not r.get("discoverable")
                     else "table") + (", villagers" if r.get("tradeable") and not r.get("curse") else "")
            desc = r.get("description", "")
            byns[ns].append(f"  {r.get('name', path)} [{path}] max {r.get('max_level')}, {where}, {str(r.get('category', '')).lower()}"
                            + (f": {desc[:110]}" if desc else ""))
    out = []
    for ns in sorted(byns, key=lambda n: (n != "minecraft", n)):
        out.append(f"{ns} ({len(byns[ns])})")
        out.extend(sorted(byns[ns]))
    with open("enchantments.txt", "w") as f:
        f.write("\n".join(out) or "none")


def client_warnings(client_log):
    """Every distinct client warning and error, by the mod (namespace) it names, most frequent first, written to
    client-warnings.txt for the workflow's next step to print (as the boot test does for the server): a mod's missing
    models, textures and broken blockstates only show on the client. "Error loading class" lines (optional mixins for
    mods that aren't there) are only counted."""
    byns = collections.defaultdict(collections.Counter)
    optional = 0
    for line in client_log:
        m = re.match(r"\[[^\]]+\] \[[^\]]+\] \[(WARN|ERROR)\] (.*)", line) or re.match(r"\[[^\]]+\] \[[^\]]+/(WARN|ERROR)\] (?:\[[^\]]+\]: |: )?(.*)", line)
        if not m or "Can't keep up" in m.group(2):
            continue
        if m.group(2).startswith("Error loading class:"):
            optional += 1
            continue
        names = re.findall(r"\b([a-z0-9_.-]+):[a-z0-9_/.-]+", m.group(2))
        ns = next((x for x in names if x not in ("minecraft", "http", "https", "file", "union", "jar")), names[0] if names else "other")
        byns[ns][f"[{m.group(1)}] " + re.sub(r"\d+", "#", m.group(2))[:200]] += 1
    detail = [f"(and {optional} optional mixin classes not found: mods' extras for mods that aren't in the pack)"] if optional else []
    for ns, counter in sorted(byns.items(), key=lambda kv: -sum(kv[1].values())):
        detail.append(f"{ns}: {sum(counter.values())} lines")
        for text, n in counter.most_common(4):
            detail.append(f"    {n}x {text}")
    with open("client-warnings.txt", "w") as out:
        out.write("\n".join(detail) or "none")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        text = traceback.format_exc()
        print("::error title=pack-launch-crash::" + text.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
        raise
