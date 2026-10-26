"""The pack launch test: a real Forge 1.20.1 client with the pack's client side joins the pack's server.

Usage (inside a virtual display): python3 pack/launch_test.py <server folder> <client game folder> <screenshots dir>

The server folder is the one boot_server.py left (Forge installed). It's switched to a flat world in offline mode
and started; the client (installed and started by portablemc, offline, as the build_pack.py --client-run folder has
it) joins it. The server then sets a scene (noon, some Fresh Animations mobs in front of the player) and the screen
is captured a few times. Checks: the client gets in, Bloom switched the pack's resource packs on (log line and
options.txt), and nothing crashed. Results and the client's warnings go out as GitHub annotations; the screenshots
are written as PNGs for the screenshot job.
"""
import os
import re
import subprocess
import sys
import threading
import time

PLAYER = "BloomTester"
JOIN_LIMIT = 900  # seconds for the client to install, start and join
FORGE = "forge::1.20.1-47.3.0"
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
        self.process = subprocess.Popen(args, cwd=cwd, env=env, stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
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
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()


def set_properties(path, values):
    lines = open(path).read().splitlines() if os.path.exists(path) else []
    keys = {line.split("=", 1)[0] for line in lines if "=" in line}
    lines = [f"{line.split('=', 1)[0]}={values[line.split('=', 1)[0]]}" if "=" in line and line.split("=", 1)[0] in values else line
             for line in lines]
    lines += [f"{key}={value}" for key, value in values.items() if key not in keys]
    open(path, "w").write("\n".join(lines) + "\n")


def capture(folder, name):
    path = os.path.join(folder, f"bloom_{name}.png")
    subprocess.run(["import", "-window", "root", path], check=False)
    return os.path.exists(path)


def main():
    server_dir, game_dir, shots = sys.argv[1], os.path.abspath(sys.argv[2]), sys.argv[3]
    os.makedirs(shots, exist_ok=True)
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    failures, notes = [], []

    # ---- the server: a fresh flat world, offline so the test client can join
    subprocess.run(["rm", "-rf", os.path.join(server_dir, "world")])
    set_properties(os.path.join(server_dir, "server.properties"), {
        "online-mode": "false", "level-type": "minecraft\\:flat", "difficulty": "easy", "spawn-protection": "0",
        "generate-structures": "false", "view-distance": "6", "simulation-distance": "5",
    })
    server = Process(["sh", "start.sh"], server_dir, stdin=True)
    if not server.wait_for(r"\]: Done \(|Done \([0-9.]+s\)!", 600):
        print(f"::error title=pack-launch::The server didn't come up. Last lines:%0A{esc(chr(10).join(server.lines[-40:])[:3800])}")
        sys.exit(1)

    # ---- the client: Forge installed by portablemc, the pack's client side already in the game folder
    options = os.path.join(game_dir, "options.txt")
    # A first start like a friend's: options.txt only has what keeps the test quiet (Bloom adds the pack's packs).
    open(options, "w").write("onboardAccessibility:false\ntutorialStep:none\njoinedFirstServer:true\nskipMultiplayerWarning:true\n"
                             "pauseOnLostFocus:false\nsoundCategory_master:0.0\nguiScale:2\nrenderDistance:6\n")
    client = Process(["portablemc", "--main-dir", os.path.join(game_dir, ".pmc"), "start", FORGE, "--mc-dir", game_dir,
                      "-u", PLAYER, "--join-server", "127.0.0.1", "--resolution", "1280x720", "--jvm-arg=-Xmx3G"], game_dir)
    joined = server.wait_for(rf"{PLAYER} joined the game", JOIN_LIMIT)
    started = time.time()

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
    else:
        failures.append("the client didn't join the server")
        capture(shots, "pack_0")

    # ---- what the client did
    client_log = client.lines
    applied = next((l for l in client_log if "Bloom Pack defaults applied" in l), None)
    packs_line = next((l for l in open(options).read().splitlines() if l.startswith("resourcePacks:")), "") if os.path.exists(options) else ""
    if not applied:
        failures.append("Bloom didn't apply the pack's resource packs (no 'Bloom Pack defaults applied' line)")
    if "FreshAnimations" not in packs_line:
        failures.append(f"Fresh Animations isn't on in options.txt ({packs_line or 'no resourcePacks line'})")
    crash = [l for l in client_log if re.search(r"---- Minecraft Crash Report|Exception in thread \"Render thread\"|Game crashed", l)]
    if crash:
        failures.append("the client crashed")

    for process in (client.process,):
        process.terminate()
    try:
        client.process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        client.process.kill()
    server.send("stop")
    try:
        server.process.wait(timeout=120)
    except subprocess.TimeoutExpired:
        server.process.kill()

    # ---- report
    mods = next((l for l in client_log if re.search(r"Found \d+ mod|Loading \d+ mods|Mod List", l)), "")
    summary = [f"joined: {'yes' if joined else 'no'}", applied or "no pack defaults line", packs_line, mods.strip()]
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
    main()
