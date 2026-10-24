"""Boots a Fabric dedicated server with the pack's server mods and stops it once it's up.

Usage: python3 pack/boot_server.py <server mods zip> <minecraft version> <loader version>

Fails if the server crashes or isn't up within the time limit. Reports how long it took, how many mods loaded and
how much memory the server process used, as GitHub annotations.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import zipfile

LIMIT = 420  # seconds
HEAP = "5G"  # what a 6 GB host leaves for Minecraft


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "BloomLevel-Up CI"}), timeout=60) as response:
        return response.read()


def rss_mb(pid):
    try:
        for line in open(f"/proc/{pid}/status"):
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) // 1024
    except OSError:
        pass
    return -1


def main():
    mods_zip, game, loader = sys.argv[1], sys.argv[2], sys.argv[3]
    root = "server"
    os.makedirs(root, exist_ok=True)
    installers = json.loads(get("https://meta.fabricmc.net/v2/versions/installer"))
    installer = next(v["version"] for v in installers if v.get("stable"))
    with open(os.path.join(root, "server.jar"), "wb") as out:
        out.write(get(f"https://meta.fabricmc.net/v2/versions/loader/{game}/{loader}/{installer}/server/jar"))
    with zipfile.ZipFile(mods_zip) as mods:
        mods.extractall(root)
    with open(os.path.join(root, "eula.txt"), "w") as eula:
        eula.write("eula=true\n")

    start = time.time()
    process = subprocess.Popen(["java", f"-Xmx{HEAP}", "-jar", "server.jar", "nogui"], cwd=root, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    log, done, memory = [], None, -1

    for line in process.stdout:
        log.append(line.rstrip())
        if done is None and re.search(r"\]: Done \(|\) Done \(|Done \([0-9.]+s\)!", line):
            done = time.time() - start
            time.sleep(5)
            memory = rss_mb(process.pid)
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
    print(f"::notice title=server-boot::Up in {done:.0f} s with -Xmx{HEAP}; {loading.strip()}; server process memory {memory} MB right after start")


if __name__ == "__main__":
    main()
