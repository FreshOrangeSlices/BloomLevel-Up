"""Starts the pack's server zip the way a friend would (accept the EULA, run start.sh) and stops it once it's up.

Usage: python3 pack/boot_server.py <server zip>

Fails if the server crashes or isn't up within the time limit. Reports how long it took, how many mods loaded, the
datapacks the world has on and how much memory the server process used, as GitHub annotations.
"""
import os
import re
import subprocess
import sys
import time
import zipfile

LIMIT = 420  # seconds


def rss_mb(pid):
    try:
        for line in open(f"/proc/{pid}/status"):
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) // 1024
    except OSError:
        pass
    return -1


def main():
    server_zip = sys.argv[1]
    root = "server"
    os.makedirs(root, exist_ok=True)
    with zipfile.ZipFile(server_zip) as server:
        server.extractall(root)
    with open(os.path.join(root, "eula.txt")) as eula:
        text = eula.read()
    if "eula=false" not in text:
        print("::error title=server-boot::eula.txt should ship as eula=false (the player accepts it)")
        sys.exit(1)
    with open(os.path.join(root, "eula.txt"), "w") as eula:
        eula.write(text.replace("eula=false", "eula=true"))
    heap = re.search(r"-Xmx(\w+)", open(os.path.join(root, "start.sh")).read()).group(1)

    start = time.time()
    # start.sh execs java, so this process is the server itself.
    process = subprocess.Popen(["sh", "start.sh"], cwd=root, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    log, done, memory, datapacks = [], None, -1, []

    for line in process.stdout:
        log.append(line.rstrip())
        if done is not None and "data pack" in line:
            datapacks.append(line.split("]: ", 1)[-1].strip())
        if done is None and re.search(r"\]: Done \(|\) Done \(|Done \([0-9.]+s\)!", line):
            done = time.time() - start
            time.sleep(5)
            memory = rss_mb(process.pid)
            process.stdin.write("datapack list enabled\n")
            process.stdin.flush()
            time.sleep(3)
            process.stdin.write("stop\n")
            process.stdin.flush()
        if time.time() - start > LIMIT:
            process.kill()
            break

    process.wait(timeout=120)
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    loading = next((l for l in log if "Loading" in l and "mods" in l), "")
    problems = [l for l in log if re.search(r"ERROR|Exception|Caused by|Incompatible|requires", l)]
    if problems:
        text = "\n".join(problems[:60])
        print(f"::warning title=server-log::{esc(text[:3900])}")
    if done is None:
        print(f"::error title=server-boot::The server didn't come up (exit {process.returncode}). Last lines:%0A{esc(chr(10).join(log[-40:])[:3800])}")
        sys.exit(1)
    print(f"::notice title=server-boot::Up in {done:.0f} s with -Xmx{heap}; {loading.strip()}; server process memory {memory} MB right after start")
    print(f"::notice title=server-datapacks::{esc(chr(10).join(datapacks) or 'no datapack list printed')}")


if __name__ == "__main__":
    main()
