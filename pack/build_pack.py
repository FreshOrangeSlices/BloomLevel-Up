"""Builds the Bloom Pack for sharing with friends: a Modrinth pack file (.mrpack) and a ready-to-run server folder.

Usage: python3 pack/build_pack.py <bloom jar> <version> <out dir> [--client-run <dir>] [--without <slug,slug>]

Reads pack/pack.json. Modrinth mods are linked (newest 26.3 Fabric build, plus every required dependency); Bloom
and the external (CurseForge-only) files are packed inside, which is fine for a private share but not for
publishing. Every jar is downloaded and checked: each mod's required mod ids have to be in the pack (on the client
and on the server), or the build fails. Prints a summary as GitHub annotations.

The server zip is a whole server folder: the Fabric server launcher, mods, datapacks (in world/datapacks),
start.bat / start.sh, server.properties and an eula.txt to accept. On a host like Shockbyte only its mods and world
folders are uploaded.

--client-run fills a dev client's run folder with the pack's client side (mods except Fabric API and Bloom, which
the dev run has already, plus resource packs, shaders and the first-launch settings) for the pack launch test.
--without leaves those Modrinth projects out (to test the pack without a suspect).
"""
import hashlib
import io
import json
import os
import shutil
import sys
import time
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up pack builder (github actions)"}
FOLDERS = {"mod": "mods", "resourcepack": "resourcepacks", "shader": "shaderpacks", "datapack": "datapacks"}
# Mod ids every Fabric 26.3 setup has.
BUILT_IN = {"minecraft", "java", "fabricloader", "fabric-loader", "mixinextras"}
HEAP = "5G"  # what a 6 GB host leaves for Minecraft (the boot test uses the same)

START_BAT = f"""@echo off
rem Bloom Pack server. Needs Java 25 or newer (java -version). Close this window or type stop to shut it down.
cd /d "%~dp0"
java -Xms2G -Xmx{HEAP} -jar fabric-server.jar nogui
pause
"""

START_SH = f"""#!/bin/sh
# Bloom Pack server. Needs Java 25 or newer (java -version). Type stop to shut it down.
cd "$(dirname "$0")"
exec java -Xms2G -Xmx{HEAP} -jar fabric-server.jar nogui "$@"
"""

EULA = """# Change false to true to agree to Minecraft's EULA (https://aka.ms/MinecraftEULA). The server won't start until you do.
eula=false
"""

# Only what the pack needs; the server fills in every other setting on its first start.
SERVER_PROPERTIES = """motd=Bloom Pack
view-distance=10
simulation-distance=8
max-players=10
difficulty=normal
spawn-protection=0
allow-flight=true
"""

README = """Bloom Pack server

On your own PC
1. Install Java 25 or newer (for example Eclipse Temurin 25 from adoptium.net). "java -version" in a terminal shows which one you have.
2. Open eula.txt and change false to true.
3. Double-click start.bat (Windows) or run ./start.sh (Mac/Linux). The first start downloads Minecraft's server and builds the world.
4. Friends join with your public IP and port 25565 (forward TCP 25565 on your router to this PC).
   It starts with 5 GB of memory: change -Xmx5G in start.bat if your PC needs it lower.

On a host (Shockbyte and similar)
Set the server type to Fabric for Minecraft 26.3, then upload the mods folder and the world folder (it holds the datapacks).
allow-flight=true and the other lines in server.properties are worth copying into the host's settings.
"""


def http(url, binary=False):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as response:
                data = response.read()
                return data if binary else json.loads(data)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            time.sleep(2 + attempt * 3)
        except Exception:
            time.sleep(2 + attempt * 3)
    raise RuntimeError(f"Couldn't fetch {url}")


def api(path, **params):
    query = "&".join(f"{k}={urllib.parse.quote(json.dumps(v) if isinstance(v, list) else str(v))}" for k, v in params.items())
    return http(f"{API}{path}" + (f"?{query}" if query else ""))


def side(value):
    # Everything in the pack is meant to be installed: "optional" on a side becomes "required" (launchers may
    # otherwise ask, or skip it). Only "unsupported" keeps a mod off that side.
    return "unsupported" if value == "unsupported" else "required"


def newest_version(project, game, loader_for):
    """The newest version for this game version (releases first, then betas, then alphas)."""
    versions = api(f"/project/{project['id']}/version", game_versions=[game], loaders=[loader_for]) or []
    for kind in ("release", "beta", "alpha"):
        for version in versions:
            if version["version_type"] == kind:
                return version
    return None


def loader_for(project):
    kind = project.get("project_type")
    if kind == "resourcepack":
        return "minecraft"
    if kind == "shader":
        return "iris"
    if kind == "datapack" or ("fabric" not in project.get("loaders", []) and "datapack" in project.get("loaders", [])):
        return "datapack"
    return "fabric"


def mod_ids(data):
    """Mod ids a jar provides (its own, 'provides', and every jar nested inside it), and its required mod ids."""
    provided, depends = set(), {}
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return provided, depends
    with archive:
        if "fabric.mod.json" in archive.namelist():
            try:
                meta = json.loads(archive.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
            except ValueError:
                return provided, depends
            provided.add(meta.get("id"))
            provided.update(meta.get("provides", []))
            for key, value in (meta.get("depends") or {}).items():
                depends[key] = value
            for nested in meta.get("jars", []):
                path = nested.get("file") if isinstance(nested, dict) else None
                if path and path in archive.namelist():
                    inner, _ = mod_ids(archive.read(path))
                    provided.update(inner)
    return provided, depends


def server_launcher(game, loader):
    installers = http("https://meta.fabricmc.net/v2/versions/installer")
    installer = next(v["version"] for v in installers if v.get("stable"))
    return http(f"https://meta.fabricmc.net/v2/versions/loader/{game}/{loader}/{installer}/server/jar", binary=True)


def main():
    args = sys.argv[1:]
    client_run = None
    if "--client-run" in args:
        at = args.index("--client-run")
        client_run = args[at + 1]
        del args[at:at + 2]
    without = set()
    if "--without" in args:
        at = args.index("--without")
        without = {slug.strip() for slug in args[at + 1].split(",") if slug.strip()}
        del args[at:at + 2]
    bloom_jar, pack_version, out = args[0], args[1], args[2]
    os.makedirs(out, exist_ok=True)
    spec = json.load(open(os.path.join(os.path.dirname(__file__), "pack.json")))
    game = spec["minecraft"]
    files, notes, problems = [], [], []
    by_slug = {}
    # Server-only content mods (structures) go on the client too, so singleplayer worlds have them; their
    # dependencies with them.
    singleplayer_too = set(spec.get("singleplayer_too", []))
    spec["modrinth"] = [slug for slug in spec["modrinth"] if slug not in without]
    presets = spec.setdefault("client_presets", {})
    presets["resource_packs"] = [slug for slug in presets.get("resource_packs", []) if slug not in without]
    if without:
        notes.append("left out: " + ", ".join(sorted(without)))
    queue = [(slug, None, slug in singleplayer_too) for slug in spec["modrinth"]]
    seen = set()

    # ---- Modrinth: each listed project and its required dependencies
    while queue:
        ref, wanted_version, client_too = queue.pop(0)
        project = api(f"/project/{ref}")
        if project is None:
            problems.append(f"Modrinth project {ref} not found")
            continue
        if project["id"] in seen:
            continue
        seen.add(project["id"])
        version = api(f"/version/{wanted_version}") if wanted_version else None
        if version is None or game not in version.get("game_versions", []):
            version = newest_version(project, game, loader_for(project))
        if version is None:
            problems.append(f"{project['title']} ({project['slug']}) has no {game} build")
            continue
        file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        # A datapack-only project (no Fabric jar) is a zip for a world's datapacks folder, whatever its type says.
        datapack = loader_for(project) == "datapack" or ("datapack" in version.get("loaders", []) and "fabric" not in version.get("loaders", []))
        folder = "datapacks" if datapack else FOLDERS.get(project.get("project_type"), "mods")
        env = {"client": side(project.get("client_side")), "server": side(project.get("server_side"))}
        if folder != "mods":
            env = {"client": "required", "server": "unsupported"}
        if client_too and folder == "mods":
            env["client"] = "required"
        env.update(spec.get("env_overrides", {}).get(project["slug"], {}))
        entry = {
            "slug": project["slug"], "title": project["title"], "version": version["version_number"], "type": version["version_type"],
            "path": f"{folder}/{file['filename']}", "url": file["url"], "hashes": file["hashes"], "size": file["size"], "env": env,
            "datapack": datapack,
        }
        files.append(entry)
        by_slug[project["slug"]] = entry
        for dependency in version.get("dependencies", []):
            if dependency.get("dependency_type") == "required" and dependency.get("project_id"):
                queue.append((dependency["project_id"], dependency.get("version_id"), client_too))

    # ---- download every jar: checks the hashes, and the server zip needs them anyway
    jars = {}
    for entry in files:
        data = http(entry["url"], binary=True)
        if hashlib.sha1(data).hexdigest() != entry["hashes"]["sha1"]:
            problems.append(f"{entry['title']}: sha1 mismatch")
        jars[entry["path"]] = data

    # ---- Bloom and the external files: packed inside (overrides)
    overrides = {}
    bloom_name = os.path.basename(bloom_jar)
    overrides[f"mods/{bloom_name}"] = open(bloom_jar, "rb").read()
    sides = {f"mods/{bloom_name}": {"client": "required", "server": "required"}}
    for external in spec.get("external", []):
        data = None
        for url in external["urls"]:
            try:
                data = http(url, binary=True)
            except RuntimeError:
                data = None
            if data and data[:2] == b"PK":
                break
            data = None
        if data is None:
            notes.append(f"{external['name']} couldn't be downloaded here: add {external['file']} to the mods folder by hand ({external['page']})")
            continue
        path = f"mods/{external['file']}"
        overrides[path] = data
        sides[path] = {"client": external["client"], "server": external["server"]}

    # ---- every mod's required mods have to be in the pack, on each side
    for where in ("client", "server"):
        present = {path: data for path, data in jars.items()
                   if next(e for e in files if e["path"] == path)["env"][where] != "unsupported" and path.startswith("mods/")}
        present.update({path: data for path, data in overrides.items() if sides[path][where] != "unsupported"})
        provided, needs = set(BUILT_IN), {}
        for path, data in present.items():
            ids, depends = mod_ids(data)
            provided |= ids
            for mod_id in depends:
                needs.setdefault(mod_id, []).append(os.path.basename(path))
        for mod_id, wanted_by in sorted(needs.items()):
            if mod_id not in provided:
                problems.append(f"{where}: {', '.join(sorted(set(wanted_by)))} need(s) mod id '{mod_id}', which isn't in the pack")

    # ---- client presets: Fresh Animations on, Complementary selected (client only, first install)
    client_overrides = {}
    presets = spec.get("client_presets", {})
    packs = [f"file/{os.path.basename(by_slug[s]['path'])}" for s in presets.get("resource_packs", []) if s in by_slug]
    if packs:
        client_overrides["options.txt"] = ("resourcePacks:" + json.dumps(["vanilla", "fabric"] + packs) + "\n").encode()
    shader = presets.get("shader")
    if shader in by_slug:
        client_overrides["config/iris.properties"] = (f"enableShaders=true\nshaderPack={os.path.basename(by_slug[shader]['path'])}\n").encode()
    # Other client config files, as given (a JSON object is written as JSON). A mod fills in the settings left out.
    for path, content in presets.get("files", {}).items():
        client_overrides[path] = (json.dumps(content, indent=2) if isinstance(content, (dict, list)) else str(content)).encode()

    # ---- the .mrpack
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": pack_version,
        "name": spec["name"],
        "summary": spec["summary"],
        "files": [{
            "path": e["path"],
            "hashes": {"sha1": e["hashes"]["sha1"], "sha512": e["hashes"]["sha512"]},
            "env": e["env"],
            "downloads": [e["url"]],
            "fileSize": e["size"],
        } for e in files],
        "dependencies": {"minecraft": game, "fabric-loader": spec["fabric_loader"]},
    }
    pack_path = os.path.join(out, f"Bloom Pack {pack_version}.mrpack")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, indent=2))
        for path, data in overrides.items():
            folder = "overrides" if sides[path]["server"] != "unsupported" else "client-overrides"
            pack.writestr(f"{folder}/{path}", data)
        for path, data in client_overrides.items():
            pack.writestr(f"client-overrides/{path}", data)

    # ---- the server: a whole folder, ready to start (or its mods and world folders, uploaded to a host)
    server_path = os.path.join(out, f"Bloom Pack {pack_version} server.zip")
    server_mods = []
    with zipfile.ZipFile(server_path, "w", zipfile.ZIP_DEFLATED) as server:
        for entry in files:
            if entry["env"]["server"] != "unsupported" and entry["path"].startswith("mods/"):
                server.writestr(entry["path"], jars[entry["path"]])
                server_mods.append(entry["title"])
            elif entry["datapack"]:
                server.writestr(f"world/datapacks/{os.path.basename(entry['path'])}", jars[entry["path"]])
                server_mods.append(f"{entry['title']} (datapack)")
        for path, data in overrides.items():
            if sides[path]["server"] != "unsupported":
                server.writestr(path, data)
                server_mods.append(os.path.basename(path))
        server.writestr("fabric-server.jar", server_launcher(game, spec["fabric_loader"]))
        for name, text in (("start.bat", START_BAT.replace("\n", "\r\n")), ("eula.txt", EULA), ("server.properties", SERVER_PROPERTIES),
                           ("README.txt", README)):
            server.writestr(name, text)
        script = zipfile.ZipInfo("start.sh")
        script.external_attr = 0o755 << 16  # executable when unzipped
        script.compress_type = zipfile.ZIP_DEFLATED
        server.writestr(script, START_SH)

    # ---- the pack launch test: the client side into a dev run folder
    if client_run:
        for sub in ("mods", "resourcepacks", "shaderpacks", "config"):
            shutil.rmtree(os.path.join(client_run, sub), ignore_errors=True)
            os.makedirs(os.path.join(client_run, sub), exist_ok=True)
        for entry in files:
            if entry["env"]["client"] != "unsupported" and entry["slug"] != "fabric-api" and not entry["datapack"]:
                with open(os.path.join(client_run, entry["path"]), "wb") as target:
                    target.write(jars[entry["path"]])
        for path, data in list(overrides.items()) + list(client_overrides.items()):
            if path == f"mods/{bloom_name}" or sides.get(path, {}).get("client") == "unsupported":
                continue
            os.makedirs(os.path.dirname(os.path.join(client_run, path)) or client_run, exist_ok=True)
            with open(os.path.join(client_run, path), "wb") as target:
                target.write(data)

    # ---- summary
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    lines = [f"{e['title']} ({e['slug']}) {e['version']} [{e['type']}] -> {e['path']} (client {e['env']['client']}, server {e['env']['server']})"
             for e in files]
    lines += [f"packed inside: {path}" for path in overrides]
    lines += [f"client preset: {path}" for path in client_overrides]
    text = "\n".join(lines)
    for n in range(0, len(text), 3800):
        print(f"::notice title=pack-contents-{n // 3800:02d}::{esc(text[n:n + 3800])}")
    size = os.path.getsize(pack_path) / 1e6
    print(f"::notice title=pack-summary::{esc(f'{len(files)} linked + {len(overrides)} packed inside; pack file {size:.1f} MB; server mods: {len(server_mods)}' + chr(10) + chr(10).join(notes))}")
    with open(os.path.join(out, "server-mods.txt"), "w") as listing:
        listing.write("\n".join(server_mods) + "\n")
    if problems:
        print(f"::error title=pack-problems::{esc(chr(10).join(problems))}")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        text = traceback.format_exc()
        print("::error title=pack-crash::" + text.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
        raise
