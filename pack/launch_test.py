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
    """A milestone, with the machine's free memory, printed as it happens (so a run that dies shows how far it got)."""
    free = next((int(l.split()[1]) // 1024 for l in open("/proc/meminfo") if l.startswith("MemAvailable:")), -1)
    print(f"::notice title=pack-progress::{step} ({free} MB free, {time.strftime('%H:%M:%S')})", flush=True)


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


BLOOM_NONE = {"staff", "battlestaff", "wand", "spellbreaker", "horn", "shield", "fist"}  # spell weapons, shields, gauntlets


def pack_data_check(server_dir, dump_file, server_lines):
    """The pack's own data pack (pack_data, loaded by Open Loader from config/openloader/data): every recipe it takes
    out is gone from the server, nothing makes an item it hides, and each hidden item carries JEI's hidden tag. From
    the files the server zip shipped and Bloom's registry dump. Ids from mods this layer leaves out are skipped.
    Returns (failures, notes)."""
    packs = os.path.join(server_dir, "config", "openloader", "data")
    if not os.path.isdir(packs):
        return [], []
    removed, hidden = [], []
    for pack in sorted(os.listdir(packs)):
        data = os.path.join(packs, pack, "data")
        for folder, _, names in os.walk(data):
            for file_name in names:
                parts = os.path.relpath(os.path.join(folder, file_name), data).replace(os.sep, "/").split("/", 2)
                if len(parts) == 3 and parts[1] == "recipes" and file_name.endswith(".json"):
                    # Only recipes switched off (a forge:false condition); recipe_edits are changed, not removed.
                    try:
                        recipe = json.load(open(os.path.join(folder, file_name)))
                    except ValueError:
                        recipe = {}
                    if {"type": "forge:false"} in recipe.get("conditions", []):
                        removed.append(f"{parts[0]}:{parts[2][:-5]}")
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
    injected = next((l.split("]: ", 1)[-1] for l in server_lines if "Successfully injected" in l), "Open Loader said nothing")
    notes = [f"pack data: {len([r for r in removed if r.split(':')[0] in mods])} recipes out, {len(checked)} items hidden "
             f"(of {len(removed)} and {len(hidden)} listed; the rest are from mods this layer leaves out); {injected}"]
    return failures, notes


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
    families = ("sword", "axe", "bow", "crossbow", "trident")
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
            others = sorted(other for other, k in bound.items() if k.split(":")[0] == key.split(":")[0] and other != name
                            and k.split(":")[1:] == key.split(":")[1:])
            if others and name in bound:
                failures.append(f"{key} is {name}'s, but {', '.join(others)} start(s) on it too")
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
    """The minimap with one of each radar color around the player: a zombie (hostile, red), a cow (passive, green), a
    wolf (neutral, yellow), and a dropped item and an arrow (both hidden). The mobs stand still and are removed after."""
    for command in (f"summon minecraft:zombie ~5 ~ ~ {{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:cow ~-5 ~ ~ {{NoAI:1b,Silent:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:wolf ~ ~ ~5 {{NoAI:1b,Silent:1b,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:item ~ ~ ~-5 {{Item:{{id:\"minecraft:diamond\",Count:1b}},PickupDelay:32767,Tags:[\"lwx_radar\"]}}",
                    f"summon minecraft:arrow ~4 ~ ~4 {{NoGravity:1b,Tags:[\"lwx_radar\"]}}"):
        server.send(f"execute as {player} at @s run {command}")
        time.sleep(0.2)
    time.sleep(4)
    shot = capture(shots, "pack_minimap")
    server.send("kill @e[tag=lwx_radar]")
    time.sleep(1)
    return shot


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
    and a pirate band of 4. Each must come whole, the war band led by its banner carrier; each is captured, then
    cleared. A band whose mod isn't in this layer is only noted. Returns (failures, notes)."""
    failures, notes = [], []
    wanted = {"war": ("minecraft:pillager", 5), "pirate": ("block_factorys_bosses:pirate_captain", 4)}
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


def terrain_check(server_dir, spec_file):
    """The add-on's terrain survey (-Dlegionwarex.terrain): the Overworld the server would make beside vanilla's for the
    same seed, and where a new world's spawn goes. Fails when the survey didn't run, a spawn search found no mainland,
    or (with the pack's erosion change) the pack keeps more than 60% of both vanilla's peaks and its land over y 130. Returns
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
        # only reported).
        some = vanilla["peakBiomes"] >= 0.003 or vanilla["over130"] >= 0.005
        fewer = pack["peakBiomes"] <= vanilla["peakBiomes"] * 0.6 or pack["over130"] <= vanilla["over130"] * 0.6
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
            band_failures, band_notes = band_check(server, PLAYER, entities, shots)
            failures += band_failures
            notes += band_notes
            progress("band check done")
    else:
        failures.append("the client didn't join the server")
        capture(shots, "pack_0")

    # ---- Bloom's view of the pack's gear and bosses
    sorting, unsorted = bloom_sorting(os.path.join(server_dir, "bloom-dump.jsonl"))
    notes.append(sorting)
    if unsorted:
        print(f"::warning title=pack-bloom-unsorted::{esc(chr(10).join(unsorted)[:3900])}")
    # ---- materials a trim left with nothing to make (reviewed by hand: some are drops with uses outside recipes)
    leftover_note, leftovers = leftover_check(os.path.join(server_dir, "bloom-dump.jsonl"),
                                              os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"), server_dir)
    notes.append(leftover_note)
    if leftovers:
        text = chr(10).join(leftovers)
        for n, i in enumerate(range(0, min(len(text), 3900 * 3), 3900)):
            print(f"::warning title=pack-leftovers-{n}::{esc(text[i:i + 3900])}")
    # ---- the pack's own data pack: recipes out, items hidden from JEI
    data_failures, data_notes = pack_data_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"), server.lines)
    failures += data_failures
    notes += data_notes
    # ---- the pack's natural spawns: what each biome spawns, and how much of it is modded
    spawn_failures, spawn_notes, spawn_report = spawn_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"),
                                                            os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack.json"))
    failures += spawn_failures
    notes += spawn_notes
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
    problems = [l for l in client_log if re.search(r"/ERROR\]|Exception|Caused by|Mixin apply failed|InvalidInjection|Missing or unsupported", l)]
    if problems:
        text = "\n".join(dict.fromkeys(problems))
        for n, i in enumerate(range(0, min(len(text), 3900 * 6), 3900)):
            print(f"::warning title=pack-client-log-{n:02d}::{esc(text[i:i + 3900])}")
    if failures or not joined:
        tail = "\n".join(client_log[-60:])
        print(f"::error title=pack-launch-failed::{esc(('; '.join(failures) + chr(10) + tail)[:3900])}")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        text = traceback.format_exc()
        print("::error title=pack-launch-crash::" + text.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
        raise
