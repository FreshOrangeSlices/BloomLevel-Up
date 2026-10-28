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
    client = Process(launch + ["--join-server", "127.0.0.1"], game_dir)
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
        # The inventory: where Bloom's buttons sit next to what other mods put around it.
        if press("e"):
            time.sleep(3)
            capture(shots, "pack_inventory")
            press("e")
            time.sleep(1)
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
    else:
        failures.append("the client didn't join the server")
        capture(shots, "pack_0")

    # ---- Bloom's view of the pack's gear and bosses
    sorting, unsorted = bloom_sorting(os.path.join(server_dir, "bloom-dump.jsonl"))
    notes.append(sorting)
    if unsorted:
        print(f"::warning title=pack-bloom-unsorted::{esc(chr(10).join(unsorted)[:3900])}")
    # ---- the pack's own data pack: recipes out, items hidden from JEI
    data_failures, data_notes = pack_data_check(server_dir, os.path.join(server_dir, "bloom-dump.jsonl"), server.lines)
    failures += data_failures
    notes += data_notes

    # ---- what the client did (both starts)
    client_exited = client.process.poll() is not None  # by itself, before the test stops it
    client_log = title.lines + client.lines
    applied = next((l for l in client_log if "Bloom Pack defaults applied" in l), None)
    packs_line = next((l for l in open(options).read().splitlines() if l.startswith("resourcePacks:")), "") if os.path.exists(options) else ""
    if not applied:
        failures.append("Bloom didn't apply the pack's resource packs (no 'Bloom Pack defaults applied' line)")
    if "FreshAnimations" not in packs_line:
        failures.append(f"Fresh Animations isn't on in options.txt ({packs_line or 'no resourcePacks line'})")
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
