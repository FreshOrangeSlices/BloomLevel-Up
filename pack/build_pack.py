"""Builds the Bloom Pack for sharing with friends: a Modrinth pack file (.mrpack) and the server's mods as a zip.

Usage: python3 pack/build_pack.py <bloom jar> <version> <out dir>

Reads pack/pack.json. Modrinth mods are linked (newest 26.3 Fabric build, plus every required dependency); Bloom
and the external (CurseForge-only) files are packed inside, which is fine for a private share but not for
publishing. Every jar is downloaded and checked: each mod's required mod ids have to be in the pack (on the client
and on the server), or the build fails. Prints a summary as GitHub annotations.
"""
import hashlib
import io
import json
import os
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
            meta = json.loads(archive.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
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


def main():
    bloom_jar, version, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    spec = json.load(open(os.path.join(os.path.dirname(__file__), "pack.json")))
    game = spec["minecraft"]
    files, notes, problems = [], [], []
    by_slug = {}
    queue = [(slug, None) for slug in spec["modrinth"]]
    seen = set()

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
        version = api(f"/version/{wanted_version}") if wanted_version else None
        if version is None or game not in version.get("game_versions", []):
            version = newest_version(project, game, loader_for(project))
        if version is None:
            problems.append(f"{project['title']} ({project['slug']}) has no {game} build")
            continue
        file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        folder = FOLDERS.get(project.get("project_type"), "mods")
        env = {"client": side(project.get("client_side")), "server": side(project.get("server_side"))}
        if folder != "mods":
            env = {"client": "required", "server": "unsupported"}
        env.update(spec.get("env_overrides", {}).get(project["slug"], {}))
        entry = {
            "slug": project["slug"], "title": project["title"], "version": version["version_number"], "type": version["version_type"],
            "path": f"{folder}/{file['filename']}", "url": file["url"], "hashes": file["hashes"], "size": file["size"], "env": env,
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

    # ---- the .mrpack
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
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
    pack_path = os.path.join(out, f"Bloom Pack {version}.mrpack")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, indent=2))
        for path, data in overrides.items():
            folder = "overrides" if sides[path]["server"] != "unsupported" else "client-overrides"
            pack.writestr(f"{folder}/{path}", data)
        for path, data in client_overrides.items():
            pack.writestr(f"client-overrides/{path}", data)

    # ---- the server's mods (drop the mods folder into a Fabric 26.3 server)
    server_path = os.path.join(out, f"Bloom Pack {version} server mods.zip")
    server_mods = []
    with zipfile.ZipFile(server_path, "w", zipfile.ZIP_DEFLATED) as server:
        for entry in files:
            if entry["env"]["server"] != "unsupported" and entry["path"].startswith("mods/"):
                server.writestr(entry["path"], jars[entry["path"]])
                server_mods.append(entry["title"])
        for path, data in overrides.items():
            if sides[path]["server"] != "unsupported":
                server.writestr(path, data)
                server_mods.append(os.path.basename(path))

    # ---- summary
    esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    lines = [f"{e['title']} {e['version']} [{e['type']}] -> {e['path']} (client {e['env']['client']}, server {e['env']['server']})" for e in files]
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
    main()
