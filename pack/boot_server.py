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

LIMIT = 900  # seconds (the first start installs Forge too)


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
    process = subprocess.Popen(["sh", "start.sh"], cwd=root, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    log, done, installed, memory = [], None, None, -1

    for line in process.stdout:
        log.append(line.rstrip())
        if installed is None and re.search(r"The server installed successfully|Successfully installed", line):
            installed = time.time() - start
        if done is None and re.search(r"\]: Done \(|\) Done \(|Done \([0-9.]+s\)!", line):
            done = time.time() - start
            time.sleep(5)
            memory = rss_mb(java_pid(process.pid))
            process.stdin.write("stop\n")
            process.stdin.flush()
        if time.time() - start > LIMIT:
            process.kill()
            break

    process.wait(timeout=180)
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
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
        sys.exit(1)
    install = f"Forge installed in {installed:.0f} s, " if installed else ""
    print(f"::notice title=server-boot::{install}up in {done:.0f} s with -Xmx{heap}; {mods}; server process memory {memory} MB right after start")

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
