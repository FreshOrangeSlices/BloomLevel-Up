"""Builds the Forge 1.20.1 pack in pack/pack.json (LegionWareX) for sharing with friends: a Modrinth pack file (.mrpack)
and a ready-to-run server folder.

Usage: python3 pack/build_pack.py <bloom jar> <version> <out dir> [--layers N] [--client-run <dir>] [--without <slug,slug>] [--update]

The pack's mods are in cumulative layers; --layers N builds only the first N (CI tests each, to tell which layer
breaks what), and without it every layer goes in. Modrinth mods are linked (newest 1.20.1 Forge build, plus every
required dependency); Bloom and the CurseForge-only files ("external") are packed inside, which is fine for a private
share but not for publishing.

Every Modrinth project is pinned to the version in pack/pack.lock.json (the versions last tested), so a build only
changes when the lock does; a project the lock doesn't name gets its newest build. --update ignores the lock and takes
the newest of everything. Either way the build writes the versions it used to <out dir>/pack.lock.json and lists what
differs from the committed lock (pack-updates), so the lock can be moved forward on purpose. The lock also keeps each
file's address and hashes: Modrinth's CDN keeps serving a version its author has taken off the project page, so the
pack still builds from it (with a note), and only a file that's really gone stops the build. Every jar is downloaded and checked: each
mod's mandatory mod ids (from its META-INF/mods.toml) have to be in the pack, on the client and on the server, or the
build fails. Prints a summary as GitHub annotations.

The server zip is a whole server folder: the Forge installer (start.bat / start.sh install Forge on the first start,
then run it), mods, start scripts, server.properties and an eula.txt to accept. On a host like Shockbyte only its mods
folder is uploaded.

--client-run fills a game folder with the pack's client side (every client mod including Bloom, resource packs and
the first-launch settings) for the pack launch test. --without leaves those Modrinth projects out.
"""
import hashlib
import io
import json
import os
import re
import shutil
import sys
import time
import tomllib
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up pack builder (github actions)"}
FOLDERS = {"mod": "mods", "resourcepack": "resourcepacks", "shader": "shaderpacks", "datapack": "datapacks"}
# Mod ids every Forge 1.20.1 setup has.
BUILT_IN = {"minecraft", "forge", "javafml", "lowcodefml", "mcp"}
HEAP = "5G"  # what a 6 GB host leaves for Minecraft (the boot test uses the same)

START_BAT = f"""@echo off
rem {{name}} server (Forge 1.20.1). Needs Java 17 (java -version). Type stop to shut it down.
cd /d "%~dp0"
if not exist run.bat (
  echo First start: installing Forge, this needs internet and takes a minute...
  java -jar forge-installer.jar --installServer
  if errorlevel 1 goto failed
)
findstr /b /c:"-Xmx" user_jvm_args.txt >nul 2>&1 || echo -Xmx{HEAP}>> user_jvm_args.txt
call run.bat nogui
goto :eof
:failed
echo Installing Forge failed (is Java 17 installed, and is the internet on?)
pause
"""

START_SH = f"""#!/bin/sh
# {{name}} server (Forge 1.20.1). Needs Java 17 (java -version). Type stop to shut it down.
cd "$(dirname "$0")"
if [ ! -f run.sh ]; then
  echo "First start: installing Forge, this needs internet and takes a minute..."
  java -jar forge-installer.jar --installServer || exit 1
fi
grep -q '^-Xmx' user_jvm_args.txt 2>/dev/null || echo "-Xmx{HEAP}" >> user_jvm_args.txt
exec sh run.sh nogui "$@"
"""

EULA = """# Change false to true to agree to Minecraft's EULA (https://aka.ms/MinecraftEULA). The server won't start until you do.
eula=false
"""

# Only what the pack needs; the server fills in every other setting on its first start.
SERVER_PROPERTIES = """motd={name}
view-distance=10
simulation-distance=8
max-players=10
difficulty=normal
spawn-protection=0
allow-flight=true
"""

README = """{name} server (Forge 1.20.1)

On your own PC
1. Install Java 17 (for example Eclipse Temurin 17 from adoptium.net). "java -version" in a terminal shows which one you have.
2. Open eula.txt and change false to true.
3. Double-click start.bat (Windows) or run ./start.sh (Mac/Linux). The first start installs Forge (it needs internet for that), then builds the world.
4. Friends join with your public IP and port 25565 (forward TCP 25565 on your router to this PC).
   It starts with 5 GB of memory: change -Xmx5G in user_jvm_args.txt if your PC needs it lower.

On a host (Shockbyte and similar)
Set the server type to Forge 1.20.1 (version 47.3.0 or newer), then upload the mods folder.
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
    """The newest release or beta for this game version (as launchers take it; alphas only when there's nothing else),
    skipping a version with no file a game can load (see pick_file)."""
    versions = [v for v in api(f"/project/{project['id']}/version", game_versions=[game], loaders=[loader_for]) or [] if pick_file(v)]
    for version in versions:
        if version["version_type"] in ("release", "beta"):
            return version
    return versions[0] if versions else None


def pick_file(version):
    """A version's jar for Forge: when it carries several, not a NeoForge one. Never a sources, javadoc or developer
    jar (Moonlight Lib's 1.20-2.16.38 shipped only a -dev.jar, which a game can't load); None when that's all there is."""
    files = [f for f in version["files"] if not f["filename"].endswith(("-sources.jar", "-javadoc.jar", "-dev.jar"))]
    forge = [f for f in files if "neoforge" not in f["filename"].lower() and "-neo-" not in f["filename"].lower()]
    pool = forge or files
    return next((f for f in pool if f.get("primary")), pool[0]) if pool else None


def loader_for(project):
    kind = project.get("project_type")
    if kind == "resourcepack":
        return "minecraft"
    if kind == "shader":
        return "iris"  # Modrinth files shader packs under Iris (and OptiFine); Oculus, Iris's Forge port, reads the same packs
    if kind == "datapack" or ("forge" not in project.get("loaders", []) and "datapack" in project.get("loaders", [])):
        return "datapack"
    return "forge"


def read_toml(text):
    """A mods.toml as a dict. A few mods ship TOML that isn't quite valid; then just the ids and dependencies."""
    try:
        return tomllib.loads(text)
    except tomllib.TOMLDecodeError:
        mods = [{"modId": m} for m in re.findall(r'^\s*modId\s*=\s*"([^"]+)"', text, re.M)]
        return {"mods": mods[:1], "dependencies": {}}


def mod_ids(data):
    """
    Mod ids a jar provides with their versions (its own and every jar nested inside it, Forge's jar-in-jar; None when
    the version isn't written out), and the mod ids it needs: {mod id: (side "BOTH" | "CLIENT" | "SERVER", version range)}.
    """
    provided, depends = {}, {}
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return provided, depends
    with archive:
        names = set(archive.namelist())
        if "META-INF/mods.toml" in names:
            meta = read_toml(archive.read("META-INF/mods.toml").decode("utf-8", "replace"))
            jar_version = None
            if "META-INF/MANIFEST.MF" in names:
                found = re.search(r"^Implementation-Version:\s*(\S+)", archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace"), re.M)
                jar_version = found.group(1) if found else None
            own = []
            for mod in meta.get("mods", []):
                if mod.get("modId"):
                    version = str(mod.get("version", ""))
                    provided[mod["modId"]] = jar_version if "${file.jarVersion}" in version else (version if "${" not in version else None)
                    own.append(mod["modId"])
            for owner, entries in (meta.get("dependencies") or {}).items():
                if owner not in own or not isinstance(entries, list):
                    continue
                for entry in entries:
                    mandatory = entry.get("mandatory", entry.get("type", "required") == "required")
                    if mandatory is True or mandatory == "true":
                        depends[entry.get("modId")] = (str(entry.get("side", "BOTH")).upper(), str(entry.get("versionRange", "") or ""))
        if "META-INF/jarjar/metadata.json" in names:
            try:
                nested = json.loads(archive.read("META-INF/jarjar/metadata.json")).get("jars", [])
            except ValueError:
                nested = []
            for jar in nested:
                path = jar.get("path")
                if path in names:
                    inner, _ = mod_ids(archive.read(path))
                    for mod_id, version in inner.items():
                        provided.setdefault(mod_id, version)
    return provided, depends


def version_key(text):
    """A version as numbers for comparing ("15.57.0.207" -> (15, 57, 0, 207)); None when it doesn't start with one."""
    # Every number before any "+build" part ("1.20.1-2.3.0" -> 1.20.1.2.3.0, so Maven ranges like
    # [1.20.1-2,1.20.1-3) compare right); a version that doesn't start with a number isn't compared.
    numbers = re.findall(r"\d+", text.split("+")[0] if re.match(r"\d", text) else "")
    return tuple(int(n) for n in numbers) if numbers else None


def in_range(version, spec):
    """Whether a version fits a Maven range ("[47.4,)", "[1.20,1.21)", "*" or empty means any). True when unsure."""
    spec = spec.strip()
    have = version_key(version or "")
    if not spec or spec == "*" or have is None:
        return True
    for part in re.findall(r"[\[(][^\])]*[\])]", spec) or [f"[{spec},)"]:
        low, _, high = part[1:-1].partition(",") if "," in part else (part[1:-1], None, part[1:-1])
        ok = True
        if low.strip():
            low_key = version_key(low.strip())
            if low_key is not None:
                ok &= have > low_key or (have == low_key and part[0] == "[") or have[:len(low_key)] > low_key
                ok &= not (have[:len(low_key)] < low_key)
        if high and high.strip():
            high_key = version_key(high.strip())
            if high_key is not None:
                ok &= have[:len(high_key)] < high_key or (have[:len(high_key)] == high_key and part[-1] == "]")
        if ok:
            return True
    return False


def forge_installer(game, forge):
    version = f"{game}-{forge}"
    return http(f"https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar", binary=True)


def locked_version(pin, record, game, loader):
    """A Modrinth version rebuilt from the lock, for one its author has taken off the project (the CDN still has the file)."""
    url = f"https://cdn.modrinth.com/data/{record['project']}/versions/{record['id']}/{urllib.parse.quote(record['file'])}"
    return {
        "id": record["id"], "version_number": pin, "version_type": "release", "game_versions": [game], "loaders": [loader],
        "files": [{"url": url, "filename": record["file"], "hashes": {"sha1": record["sha1"], "sha512": record["sha512"]},
                   "size": record["size"], "primary": True}],
        "dependencies": [{"project_id": project, "version_id": version, "dependency_type": "required"}
                         for project, version in record.get("required", [])],
    }


def pack_data_files(data, have_loader, problems):
    """The pack's own data pack, as {path: JSON}: under config/openloader/data/<name>, a pack.mcmeta, a recipe that
    never loads (a forge:false condition) in place of each removed one, and the c:hidden_from_recipe_viewers item tag
    (JEI hides what it holds) with the hidden items as optional entries. removed_recipes and hidden_items group their
    ids under a reason each ({reason: [ids]}); keys starting with _ are comments."""
    if not data:
        return {}
    if not have_loader:
        problems.append("pack_data needs Open Loader (open-loader) in the pack, in the first layer")
        return {}
    grouped = lambda field: [i for reason, ids in data.get(field, {}).items() if not reason.startswith("_") for i in ids]
    root = f"config/openloader/data/{data['name']}"
    out = {f"{root}/pack.mcmeta": {"pack": {"pack_format": 15, "description": data.get("description", data["name"])}}}
    for recipe in grouped("removed_recipes"):
        namespace, path = recipe.split(":", 1)
        out[f"{root}/data/{namespace}/recipes/{path}.json"] = {"conditions": [{"type": "forge:false"}]}
    hidden = grouped("hidden_items")
    if hidden:
        out[f"{root}/data/c/tags/items/hidden_from_recipe_viewers.json"] = {
            "replace": False, "values": [{"id": item, "required": False} for item in hidden]}
    return out


def lock_text(game, forge, versions, files, comment=None):
    """The lock file: the readable versions first, then one line per file (address, hashes, required projects)."""
    head = {"_comment": comment, "minecraft": game, "forge": forge} if comment else {"minecraft": game, "forge": forge}
    text = json.dumps(head, indent=2)[:-2] + ",\n"
    text += '  "versions": ' + json.dumps(dict(sorted(versions.items())), indent=2).replace("\n", "\n  ") + ",\n"
    rows = [f'    {json.dumps(slug)}: {json.dumps(files[slug], separators=(", ", ": "))}' for slug in sorted(files)]
    return text + '  "files": {\n' + ",\n".join(rows) + "\n  }\n}\n"


def main():
    args = sys.argv[1:]
    client_run = None
    if "--client-run" in args:
        at = args.index("--client-run")
        client_run = args[at + 1]
        del args[at:at + 2]
    update = "--update" in args
    if update:
        args.remove("--update")
    layers = None
    if "--layers" in args:
        at = args.index("--layers")
        layers = int(args[at + 1])
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
    name = spec["name"]
    chosen = spec.get("layers", [])[:layers] if layers else spec.get("layers", [])
    spec["modrinth"] = spec.get("modrinth", []) + [slug for layer in chosen for slug in layer.get("modrinth", [])]
    spec["external"] = spec.get("external", []) + [entry for layer in chosen for entry in layer.get("external", [])]
    files, notes, problems = [], [], []
    by_slug = {}
    spec["modrinth"] = [slug for slug in spec["modrinth"] if slug not in without]
    presets = spec.setdefault("client_presets", {})
    presets["resource_packs"] = [slug for slug in presets.get("resource_packs", []) if slug not in without]
    if without:
        notes.append("left out: " + ", ".join(sorted(without)))
    if chosen:
        notes.append("layers: " + ", ".join(layer["name"] for layer in chosen))
    queue = [(slug, None) for slug in spec["modrinth"]]
    seen = set()
    lock_path = os.path.join(os.path.dirname(__file__), "pack.lock.json")
    lock = {} if not os.path.exists(lock_path) else json.load(open(lock_path))
    locked, locked_files = lock.get("versions", {}), lock.get("files", {})
    used, used_files = {}, {}

    # ---- Modrinth: each listed project and its required dependencies
    while queue:
        ref, wanted_version = queue.pop(0)
        project = api(f"/project/{ref}")
        if project is None:
            problems.append(f"Modrinth project {ref} not found")
            continue
        if project["id"] in seen:
            continue
        seen.add(project["id"])
        version = None
        pin = None if update else locked.get(project["slug"])
        if pin:
            candidates = api(f"/project/{project['id']}/version", game_versions=[game], loaders=[loader_for(project)]) or []
            version = next((v for v in candidates if v["version_number"] == pin), None)
            if version is None and project["slug"] in locked_files:
                version = locked_version(pin, locked_files[project["slug"]], game, loader_for(project))
                notes.append(f"{project['title']} {pin} isn't listed on Modrinth any more (its author took it down): built from the locked file")
            if version is None:
                problems.append(f"{project['title']} ({project['slug']}): the locked version {pin} isn't on Modrinth any more")
                continue
        if version is None:
            version = api(f"/version/{wanted_version}") if wanted_version else None
            if version is None or game not in version.get("game_versions", []) or not pick_file(version) or (
                    loader_for(project) == "forge" and "forge" not in version.get("loaders", [])):
                version = newest_version(project, game, loader_for(project))
        if version is None:
            problems.append(f"{project['title']} ({project['slug']}) has no {game} {loader_for(project)} build")
            continue
        file = pick_file(version)
        if file is None:
            problems.append(f"{project['title']} {version['version_number']} has no jar a game can load (only sources or developer ones)")
            continue
        used[project["slug"]] = version["version_number"]
        used_files[project["slug"]] = {
            "project": project["id"], "id": version["id"], "file": file["filename"], "sha1": file["hashes"]["sha1"],
            "sha512": file["hashes"]["sha512"], "size": file["size"],
            "required": [[d.get("project_id"), d.get("version_id")] for d in version.get("dependencies", [])
                         if d.get("dependency_type") == "required" and d.get("project_id")],
        }
        datapack = loader_for(project) == "datapack"
        folder = "datapacks" if datapack else FOLDERS.get(project.get("project_type"), "mods")
        env = {"client": side(project.get("client_side")), "server": side(project.get("server_side"))}
        if folder != "mods":
            env = {"client": "required", "server": "unsupported"}
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
                queue.append((dependency["project_id"], dependency.get("version_id")))

    # ---- download every jar: checks the hashes, and the server zip needs them anyway
    jars = {}
    for entry in files:
        data = http(entry["url"], binary=True)
        if hashlib.sha1(data).hexdigest() != entry["hashes"]["sha1"]:
            problems.append(f"{entry['title']}: sha1 mismatch")
        jars[entry["path"]] = data

    # ---- Bloom: packed inside (overrides)
    overrides = {}
    bloom_name = os.path.basename(bloom_jar)
    overrides[f"mods/{bloom_name}"] = open(bloom_jar, "rb").read()
    sides = {f"mods/{bloom_name}": {"client": "required", "server": "required"}}
    for external in spec.get("external", []):
        if external["name"].lower().replace(" ", "-") in without:
            continue
        data = None
        for url in external.get("urls", []):
            try:
                data = http(url, binary=True)
            except RuntimeError:
                data = None
            if data and data[:2] == b"PK":
                break
            data = None
        if data is None:
            problems.append(f"{external['name']} couldn't be downloaded ({external['page']})")
            continue
        path = f"mods/{external['file']}"
        overrides[path] = data
        sides[path] = {"client": external["client"], "server": external["server"]}

    # ---- every mod's mandatory mods have to be in the pack, on each side
    for where in ("client", "server"):
        present = {path: data for path, data in jars.items()
                   if next(e for e in files if e["path"] == path)["env"][where] != "unsupported" and path.startswith("mods/")}
        present.update({path: data for path, data in overrides.items() if sides[path][where] != "unsupported"})
        provided = {mod_id: None for mod_id in BUILT_IN}
        provided.update({"minecraft": game, "forge": spec["forge"]})
        needs = {}
        for path, data in present.items():
            ids, depends = mod_ids(data)
            for mod_id, version in ids.items():
                provided.setdefault(mod_id, version)
            for mod_id, (needed_on, wanted) in depends.items():
                if needed_on in ("BOTH", where.upper()):
                    needs.setdefault(mod_id, []).append((os.path.basename(path), wanted))
        for mod_id, wanted_by in sorted(needs.items()):
            if mod_id not in provided:
                problems.append(f"{where}: {', '.join(sorted(set(jar for jar, _ in wanted_by)))} need(s) mod id '{mod_id}', which isn't in the pack")
                continue
            for jar, wanted in sorted(set(wanted_by)):
                if not in_range(provided[mod_id], wanted):
                    problems.append(f"{where}: {jar} needs {mod_id} {wanted}, the pack has {provided[mod_id]}")

    # ---- first-launch look: the resource packs on (bottom to top) and the shader picked. Not an options.txt (launchers don't reliably copy
    # one, and it would wipe a player's settings on every update): a defaults file Bloom applies just before the game
    # reads its options, once per change (config/PackDefaults.java, client/mixin/OptionsMixin). In overrides, so every
    # install gets it.
    pack_defaults = {}
    packs = [s[3:] if s.startswith("id:") else f"file/{os.path.basename(by_slug[s]['path'])}"
             for s in presets.get("resource_packs", []) if s.startswith("id:") or s in by_slug]
    lines = [f"# {name}: switched on by Bloom Level-Up before the game reads its options, once per change of this file."]
    if packs:
        lines.append("resourcePacks=" + "|".join(packs))
    shader = presets.get("shader")
    if shader in by_slug:  # (not when the layers built leave it out)
        lines.append("shaderPack=" + os.path.basename(by_slug[shader]["path"]))
    if presets.get("keys"):
        lines.append("keys=" + "|".join(f"{binding}:{key}" for binding, key in presets["keys"].items()))
    if len(lines) > 1:
        pack_defaults["config/bloomlevelup/pack-defaults.properties"] = ("\n".join(lines) + "\n").encode()
    # Other client config files, as given (a JSON object is written as JSON). A mod fills in the settings left out.
    for path, content in presets.get("files", {}).items():
        if not path.startswith("_"):
            pack_defaults[path] = (json.dumps(content, indent=2) if isinstance(content, (dict, list)) else str(content)).encode()
    # Config files for both sides (server rules a singleplayer world reads too), as given; in the server zip as well.
    for path, content in spec.get("config_files", {}).items():
        if path.startswith("_"):
            continue
        overrides[path] = (json.dumps(content, indent=2) if isinstance(content, (dict, list)) else str(content)).encode()
        sides[path] = {"client": "required", "server": "required"}
    # The pack's own data pack (pack_data): the recipes it takes out and the items it hides from JEI, loaded into every
    # world (new and old, and the server's) by Open Loader from config/openloader/data. Ids from mods a layer leaves out
    # do no harm: a recipe that can never load is skipped, and the tag's entries are optional.
    for path, content in pack_data_files(spec.get("pack_data"), "open-loader" in by_slug, problems).items():
        overrides[path] = (json.dumps(content, indent=2) + "\n").encode()
        sides[path] = {"client": "required", "server": "required"}

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
        "dependencies": {"minecraft": game, "forge": spec["forge"]},
    }
    pack_path = os.path.join(out, f"{name} {pack_version}.mrpack")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, indent=2))
        for path, data in overrides.items():
            folder = "overrides" if sides[path]["server"] != "unsupported" else "client-overrides"
            pack.writestr(f"{folder}/{path}", data)
        for path, data in pack_defaults.items():
            pack.writestr(f"client-overrides/{path}", data)

    # ---- the server: a whole folder, ready to start (or its mods folder, uploaded to a host)
    server_path = os.path.join(out, f"{name} {pack_version} server.zip")
    server_mods = []
    installer = forge_installer(game, spec["forge"])
    if not installer or installer[:2] != b"PK":
        problems.append(f"Couldn't download the Forge {game}-{spec['forge']} installer")
        installer = b""
    with zipfile.ZipFile(server_path, "w", zipfile.ZIP_DEFLATED) as server:
        for entry in files:
            if entry["env"]["server"] != "unsupported" and entry["path"].startswith("mods/"):
                server.writestr(entry["path"], jars[entry["path"]])
                server_mods.append(entry["title"])
        for path, data in overrides.items():
            if sides[path]["server"] != "unsupported":
                server.writestr(path, data)
                if path.startswith("mods/"):
                    server_mods.append(os.path.basename(path))
        server.writestr("forge-installer.jar", installer)
        for file_name, text in (("start.bat", START_BAT.replace("\n", "\r\n")), ("eula.txt", EULA), ("server.properties", SERVER_PROPERTIES),
                                ("README.txt", README)):
            server.writestr(file_name, text.replace("{name}", name))
        script = zipfile.ZipInfo("start.sh")
        script.external_attr = 0o755 << 16  # executable when unzipped
        script.compress_type = zipfile.ZIP_DEFLATED
        server.writestr(script, START_SH.replace("{name}", name))

    # ---- the pack launch test: the client side into a game folder (and the Forge version to start it with)
    if client_run:
        os.makedirs(client_run, exist_ok=True)
        with open(os.path.join(client_run, "forge-version.txt"), "w") as forge_file:
            forge_file.write(f"{game}-{spec['forge']}\n")
        for sub in ("mods", "resourcepacks", "shaderpacks", "config"):
            shutil.rmtree(os.path.join(client_run, sub), ignore_errors=True)
            os.makedirs(os.path.join(client_run, sub), exist_ok=True)
        for entry in files:
            if entry["env"]["client"] != "unsupported" and not entry["datapack"]:
                with open(os.path.join(client_run, entry["path"]), "wb") as target:
                    target.write(jars[entry["path"]])
        for path, data in list(overrides.items()) + list(pack_defaults.items()):
            if sides.get(path, {}).get("client") == "unsupported":
                continue
            os.makedirs(os.path.dirname(os.path.join(client_run, path)) or client_run, exist_ok=True)
            with open(os.path.join(client_run, path), "wb") as target:
                target.write(data)

    # ---- the versions used (the lock), and how they differ from the committed one
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    with open(os.path.join(out, "pack.lock.json"), "w") as lock_file:
        lock_file.write(lock_text(game, spec["forge"], used, used_files, lock.get("_comment")))
    stale = {slug for slug in used if locked.get(slug) != used[slug] or locked_files.get(slug, {}).get("id") != used_files[slug]["id"]}
    changes = [f"{slug}: {locked.get(slug, 'not locked')} -> {used[slug]}" + ("" if locked.get(slug) != used[slug] else " (the lock's file record was out of date)")
               for slug in sorted(stale)]
    if changes:
        print(f"::notice title=pack-updates::{esc(('Ignoring the lock (--update): ' if update else 'Not in the lock (newest taken): ') + chr(10) + chr(10).join(changes))}")
        # The lock entries for just those, to merge into pack/pack.lock.json once this build has passed its test.
        moved = json.dumps({"versions": {slug: used[slug] for slug in sorted(stale)}, "files": {slug: used_files[slug] for slug in sorted(stale)}},
                           separators=(",", ":"))
        for n, i in enumerate(range(0, len(moved), 3800)):
            print(f"::notice title=pack-lock-{n:02d}::{esc(moved[i:i + 3800])}")

    # ---- summary
    lines = [f"{e['title']} ({e['slug']}) {e['version']} [{e['type']}] -> {e['path']} (client {e['env']['client']}, server {e['env']['server']})"
             for e in files]
    lines += [f"packed inside: {path}" for path in overrides]
    lines += [f"client preset: {path}" for path in pack_defaults]
    chunks, current = [], ""
    for line in lines:
        if current and len(current) + len(line) + 1 > 3800:
            chunks.append(current)
            current = ""
        current += line + "\n"
    chunks.append(current)
    for n, chunk in enumerate(chunks):
        print(f"::notice title=pack-contents-{n:02d}::{esc(chunk)}")
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
