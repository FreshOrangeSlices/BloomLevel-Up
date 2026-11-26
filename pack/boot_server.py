"""Starts the pack's server zip the way a friend would (accept the EULA, run start.sh) and stops it once it's up.
Forge 1.20.1 pack: the first start installs Forge from the installer in the zip, then starts the server.

Usage: python3 pack/boot_server.py <server zip> [<folder>]

Unzips into <folder> (default "server") and leaves it there, Forge installed and the world made, so the pack launch
test can start it again. Fails if the server crashes or isn't up within the time limit. Reports how long it took, how
many mods loaded and how much memory the server process used, as GitHub annotations.
"""
import collections
import os
import re
import subprocess
import sys
import time
import zipfile
import shutil
import signal
import threading

LIMIT = 600  # seconds (the first start installs Forge too; a normal start takes one or two minutes)


def rss_mb(pid):
    try:
        for line in open(f"/proc/{pid}/status"):
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) // 1024
    except OSError:
        pass
    return -1


def java_pid(parent):
    """start.sh runs the installer and then run.sh, which execs java: the server's own process, for its memory."""
    try:
        out = subprocess.run(["pgrep", "-P", str(parent)], capture_output=True, text=True).stdout.split()
        return int(out[-1]) if out else parent
    except Exception:
        return parent


def chart_check(process, log, root):
    """The LegionWareX add-on's pirate charts in the shipped world (a normal Overworld with structures, as a friend's
    server makes it; the client test's world is flat with structures off): a chart read at the world's spawn, from the
    server console, three times (each leads to a lair no earlier chart showed). Prints what it found (server-chart), a
    warning when it found no lair within reach or led to the same lair twice."""
    if not any(f.startswith("legionwarex-addon") for f in os.listdir(os.path.join(root, "mods"))):
        return
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    lines = []
    finds = [("legionwarex chart find", r"LegionWareX chart: found")] * 3
    for command, pattern in [("legionwarex chart lairs", r"LegionWareX chart: \d+ lairs")] + finds:
        mark = len(log)
        process.stdin.write(command + "\n")
        process.stdin.flush()
        end = time.time() + 240
        found = None
        while time.time() < end and found is None:
            time.sleep(1)
            found = next((l for l in log[mark:] if re.search(pattern, l)), None)
        lines.append(found.split("LegionWareX chart: ", 1)[1].strip() if found else f"{command}: no answer within 4 minutes")
    spots = [m.group(1) for m in (re.search(r" at (-?\d+, -?\d+),", l) for l in lines[1:]) if m]
    if len(set(spots)) < len(spots):
        lines.append("the same lair twice: " + ", ".join(spots))
    # A layer without boss mods has no lair to find: that's only noted.
    lairs = re.search(r"in minecraft:overworld \((\d+)\)", lines[0])
    none_here = lairs is not None and lairs.group(1) == "0"
    level = "warning" if any(("found no lair" in l and not none_here) or "no answer" in l or "same lair" in l for l in lines) else "notice"
    print(f"::{level} title=server-chart::{esc(chr(10).join(lines)[:3900])}")


def main():
    server_zip = sys.argv[1]
    root = sys.argv[2] if len(sys.argv) > 2 else "server"
    os.makedirs(root, exist_ok=True)
    with zipfile.ZipFile(server_zip) as server:
        server.extractall(root)
    os.chmod(os.path.join(root, "start.sh"), 0o755)
    with open(os.path.join(root, "eula.txt")) as eula:
        text = eula.read()
    if "eula=false" not in text:
        print("::error title=server-boot::eula.txt should ship as eula=false (the player accepts it)")
        sys.exit(1)
    with open(os.path.join(root, "eula.txt"), "w") as eula:
        eula.write(text.replace("eula=false", "eula=true"))
    heap = re.search(r"-Xmx(\w+)", open(os.path.join(root, "start.sh")).read()).group(1)

    start = time.time()
    # Its own process group, so a hung server (and the Java it started) can be stopped as a whole.
    process = subprocess.Popen(["sh", "start.sh"], cwd=root, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
    log, done, installed, memory, hung = [], None, None, -1, None
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    # Read in the background: a server that hangs prints nothing, and the limit still has to apply.
    threading.Thread(target=lambda: [log.append(line.rstrip()) for line in process.stdout], daemon=True).start()

    seen, stop_sent = 0, None
    while process.poll() is None:
        time.sleep(1)
        for line in log[seen:]:
            if installed is None and re.search(r"The server installed successfully|Successfully installed", line):
                installed = time.time() - start
            if done is None and re.search(r"\]: Done \(|\) Done \(|Done \([0-9.]+s\)!", line):
                done = time.time() - start
        seen = len(log)
        if done is not None and stop_sent is None:
            time.sleep(5)
            memory = rss_mb(java_pid(process.pid))
            chart_check(process, log, root)
            process.stdin.write("stop\n")
            process.stdin.flush()
            stop_sent = time.time()
        stuck = time.time() - start > LIMIT if stop_sent is None else time.time() - stop_sent > 180
        if stuck:
            hung = "stopping" if stop_sent else "starting"
            pid = java_pid(process.pid)
            if pid and shutil.which("jstack"):
                dump = subprocess.run(["jstack", str(pid)], capture_output=True, text=True, timeout=60).stdout
                threads = [t for t in dump.split("\n\n") if re.search(r'"(Server thread|main|Worker-Main[^"]*|ForkJoinPool[^"]*)"', t)]
                text = "\n\n".join(t.strip()[:1500] for t in threads[:8])
                print(f"::error title=server-hung::The server hung while {hung}; its main threads:%0A{esc(text[:3900])}")
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            break
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        pass
    mods_dir = os.path.join(root, "mods")
    mods = f"{len([f for f in os.listdir(mods_dir) if f.endswith('.jar')])} mods" if os.path.isdir(mods_dir) else "no mods folder"
    # The installer's own output (it lists every class it patches, exceptions included) isn't the server's.
    after = next((i for i, l in enumerate(log) if re.search(r"The server installed successfully|ModLauncher running", l)), 0)
    problems = [l for l in log[after:] if re.search(r"ERROR|Exception|Caused by|Incompatible|requires|Missing or unsupported mandatory", l)]
    if problems:
        text = "\n".join(problems[:60])
        print(f"::warning title=server-log::{esc(text[:3900])}")
    if done is None:
        print(f"::error title=server-boot::The server didn't come up (exit {process.returncode}). Last lines:%0A{esc(chr(10).join(log[-40:])[:3800])}")
        # The cause chain from the newest crash report (a mod's "has failed to load correctly" names only the outer error):
        # each "Caused by" with the first lines under it, which name the mod and class at fault.
        reports = sorted((os.path.join(root, "crash-reports", f) for f in os.listdir(os.path.join(root, "crash-reports"))),
                         key=os.path.getmtime) if os.path.isdir(os.path.join(root, "crash-reports")) else []
        text = open(reports[-1], errors="replace").read() if reports else "\n".join(log)
        lines = text.splitlines()
        causes = []
        for i, line in enumerate(lines):
            if line.lstrip().startswith("Caused by") or "has failed to load correctly" in line or line.startswith("Description:"):
                causes.append("\n".join(l.rstrip() for l in lines[i:i + 7]))
            elif line.lstrip().startswith("Exception message:"):
                # The innermost exception and where it was thrown (a class's static setup, say).
                causes.append("\n".join(l.rstrip() for l in lines[i:i + 14]))
        if causes:
            print(f"::error title=server-crash::{esc(chr(10).join(causes)[:3900])}")
        sys.exit(1)
    install = f"Forge installed in {installed:.0f} s, " if installed else ""
    print(f"::notice title=server-boot::{install}up in {done:.0f} s with -Xmx{heap}; {mods}; server process memory {memory} MB right after start")

    # Worn gear the add-on keeps rare (RareMobGear: Epic Knights' rust series, 1 in 200): how many items its tag found.
    rare = next((l for l in log if "LegionWareX rare gear:" in l), None)
    if rare:
        found = re.search(r"rare gear: (\d+) items", rare)
        knights = os.path.isdir(mods_dir) and any(f.lower().startswith("epic") and "knight" in f.lower() for f in os.listdir(mods_dir))
        level = "warning" if found and found.group(1) == "0" and knights else "notice"
        print(f"::{level} title=server-rare-gear::{esc(rare.split('LegionWareX ', 1)[-1][:3000])}")

    # Roaming bosses (the add-on's roam/): the bosses it holds to a wait, and any id the game doesn't have (a typo, a mod gone).
    roam = next((l for l in log if "LegionWareX roamers:" in l and "roaming boss(es)" in l), None)
    if roam:
        level = "warning" if "not in the game" in roam else "notice"
        print(f"::{level} title=server-roamers::{esc(roam.split('LegionWareX ', 1)[-1][:3000])}")

    # Both magic mods' gear counting for both (the add-on's magic/MagicBridge): the attribute pairs it found.
    magic = next((l for l in log if "LegionWareX magic:" in l and "count for both" in l), None)
    if magic:
        pairs = re.search(r"magic: (\d+) pair", magic)
        both = os.path.isdir(mods_dir) and any("irons" in f.lower() for f in os.listdir(mods_dir)) \
            and any(f.lower().startswith("spell-power") or f.lower().startswith("spell_power") for f in os.listdir(mods_dir))
        level = "warning" if both and pairs and int(pairs.group(1)) < 8 else "notice"
        print(f"::{level} title=server-magic::{esc(magic.split('LegionWareX ', 1)[-1][:3000])}")

    # Other mods' compat code the add-on held until common setup (LateCompat: Copper Age Backport's Sophisticated
    # Storage compat, which otherwise stops the server starting): each held one should have run.
    held = [l for l in log if "LegionWareX compat:" in l]
    if held:
        failed = [l for l in held if "failed at common setup" in l]
        waited = sum("waits for common setup" in l for l in held)
        ran = sum("ran at common setup" in l for l in held)
        level = "warning" if failed or ran < waited else "notice"
        print(f"::{level} title=server-compat::{waited} compat module(s) held until common setup, {ran} ran then"
              f"{'; ' + esc(chr(10).join(failed)[:3000]) if failed else ''}")

    # Bug hunting: every distinct warning and error, by the mod (namespace) it names, most frequent first.
    byns = collections.defaultdict(collections.Counter)
    for line in log:
        m = re.match(r"\[[^\]]+\] \[[^\]]+/(WARN|ERROR)\] (?:\[[^\]]+\]: |: )?(.*)", line)
        if not m or "Can't keep up" in m.group(2):
            continue
        names = re.findall(r"\b([a-z0-9_.-]+):[a-z0-9_/.-]+", m.group(2))
        ns = next((x for x in names if x not in ("minecraft", "http", "https", "file")), names[0] if names else "other")
        byns[ns][f"[{m.group(1)}] " + re.sub(r"\d+", "#", m.group(2))[:200]] += 1
    detail = []
    for ns, counter in sorted(byns.items(), key=lambda kv: -sum(kv[1].values())):
        detail.append(f"{ns}: {sum(counter.values())} lines")
        for text, n in counter.most_common(5):
            detail.append(f"    {n}x {text}")
    text = "\n".join(detail) or "none"
    for n, i in enumerate(range(0, min(len(text), 3900 * 4), 3900)):
        print(f"::notice title=server-warnings-{n:02d}::{esc(text[i:i + 3900])}")


if __name__ == "__main__":
    main()
