"""Throwaway (never merged): checks the Medieval High Fantasy master list for Forge 1.20.1 builds.

For each entry: the Modrinth project (by slug, else the best search hit) and its newest Forge 1.20.1 build (the newest
1.20.1 resource pack for packs), with its required dependencies; then CurseForge (through cfwidget) for anything
Modrinth doesn't have for Forge 1.20.1. Prints one line per entry as GitHub annotations.
"""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modpack check (github actions)"}
API = "https://api.modrinth.com/v2"

# (name on the list, Modrinth slug guess, CurseForge slug, kind)
ENTRIES = [
    ("Aquamirae", "aquamirae", "aquamirae", "mod"),
    ("Ice and Fire: Dragons", "ice-and-fire-dragons", "ice-and-fire-dragons", "mod"),
    ("Book of Dragons", "book-of-dragons", "book-of-dragons", "mod"),
    ("Mowzie's Mobs", "mowzies-mobs", "mowzies-mobs", "mod"),
    ("Monster Expansion", "monster-expansion", "monster-expansion", "mod"),
    ("Skarrier Mobs", "skarrier-mobs", "skarrier-mobs", "mod"),
    ("Mutant Monsters", "mutant-monsters", "mutant-monsters", "mod"),
    ("Myths & Legends", "myths-n-legends", "myths-n-legends", "mod"),
    ("Legionary", "legionary", "legionary", "mod"),
    ("Migamigos", "migamigos", "migamigos", "mod"),
    ("Iron's Spells 'n Spellbooks", "irons-spells-n-spellbooks", "irons-spells-n-spellbooks", "mod"),
    ("Spell Engine", "spell-engine", "spell-engine", "mod"),
    ("Wizards (RPG Series)", "wizards", "wizards", "mod"),
    ("Paladins & Priests (RPG Series)", "paladins-and-priests", "paladins-and-priests", "mod"),
    ("Rogues & Warriors (RPG Series)", "rogues-and-warriors", "rogues-and-warriors", "mod"),
    ("Archers (RPG Series)", "archers", "archers", "mod"),
    ("Elemental Wizards", "elemental-wizards-rpg", "elemental-wizards-rpg", "mod"),
    ("Epic Knights", "epic-knights-armor-and-weapons", "epic-knights-armor-and-weapons", "mod"),
    ("Simply Swords", "simply-swords", "simply-swords", "mod"),
    ("Marium's Soulslike Weaponry", "mariums-soulslike-weaponry", "mariums-soulslike-weaponry", "mod"),
    ("Fresh Animations", "fresh-animations", None, "pack"),
    ("Entity Model Features", "entity-model-features", None, "mod"),
    ("Entity Texture Features", "entitytexturefeatures", None, "mod"),
    ("Fresh Animations: Player Extension", "fa-player-extension", None, "pack"),
    ("Touch'n Motion", "touch-and-motion", "touch-and-motion", "mod"),
    ("Punchy!", "punchy-fpa", "punchy", "mod"),
    ("Fancy World Animations", "fwa", "fwa", "mod"),
    ("Particle Rain", "particle-rain", "particle-rain", "mod"),
    ("Emotecraft", "emotecraft", "emotecraft", "mod"),
    ("Novum's Altitude", "novums-altitude", "novums-altitude", "mod"),
    ("EMF Compat: Carry On", "emf-compat-carry-on", "emf-compat-carry-on", "mod"),
    ("Not Enough Animations", "not-enough-animations", None, "mod"),
    ("Better Combat", "better-combat", None, "mod"),
    ("Better Mob Combat", "better-mob-combat", "better-mob-combat", "mod"),
    ("EMF Compat: Better Combat", "emf-compat-better-combat", None, "mod"),
    ("Structurify", "structurify", "structurify", "mod"),
    ("Easy Mob Spawn Control", "pTXV6gwq", "easy-mob-spawn-control", "mod"),
    ("Polymorph", "polymorph", "polymorph", "mod"),
    ("Almost Unified", "sdaSaQEz", "almost-unified", "mod"),
    ("In Control!", "in-control", "in-control", "mod"),
    ("JEI", "jei", "jei", "mod"),
    ("Jade", "jade", "jade", "mod"),
    ("AppleSkin", "appleskin", "appleskin", "mod"),
    ("Mouse Tweaks", "mouse-tweaks", "mouse-tweaks", "mod"),
    ("Inventory Profiles Next", "inventory-profiles-next", "inventory-profiles-next", "mod"),
    ("Crafting Tweaks", "crafting-tweaks", "crafting-tweaks", "mod"),
    ("Carry On", "carry-on", "carry-on", "mod"),
    ("Lootr", "lootr", "lootr", "mod"),
    ("Corpse", "corpse", "corpse", "mod"),
    ("Xaero's Minimap", "xaeros-minimap", "xaeros-minimap", "mod"),
    ("Xaero's World Map", "xaeros-world-map", "xaeros-world-map", "mod"),
    ("Waystones", "waystones", "waystones", "mod"),
    ("KeyVision", "keyvision", "keyvision", "mod"),
    ("Modifier Keybinds", "modifier-keybinds", "modifier-keybinds", "mod"),
    ("Embeddium", "embeddium", "embeddium", "mod"),
    ("ModernFix", "modernfix", "modernfix", "mod"),
    ("FerriteCore", "ferrite-core", "ferritecore", "mod"),
    ("Clumps", "clumps", "clumps", "mod"),
    ("GeckoLib", "geckolib", "geckolib", "mod"),
    ("Citadel", "citadel", "citadel", "mod"),
]


def get(url, tries=4):
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as response:
                if response.status == 202:  # cfwidget: queued, ask again
                    time.sleep(8)
                    continue
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            time.sleep(3 + attempt * 3)
        except Exception:
            time.sleep(3 + attempt * 3)
    return None


def words(text):
    return {w for w in "".join(c.lower() if c.isalnum() else " " for c in text).split() if len(w) > 2}


def modrinth_project(name, slug, kind):
    project = get(f"{API}/project/{urllib.parse.quote(slug)}")
    if project and (words(name) & words(project["title"]) or slug == project["slug"]):
        return project, "slug"
    query = urllib.parse.urlencode({"query": name, "limit": 5})
    hits = (get(f"{API}/search?{query}") or {}).get("hits", [])
    for hit in hits:
        if len(words(name) & words(hit["title"])) >= max(1, len(words(name)) // 2):
            return get(f"{API}/project/{hit['project_id']}"), "search"
    return None, "; ".join(h["title"] for h in hits[:3])


def versions(project, loader):
    query = urllib.parse.urlencode({"loaders": json.dumps([loader]), "game_versions": json.dumps(["1.20.1"])})
    return get(f"{API}/project/{project['id']}/version?{query}") or []


def any_1201(project):
    query = urllib.parse.urlencode({"game_versions": json.dumps(["1.20.1"])})
    found = get(f"{API}/project/{project['id']}/version?{query}") or []
    return sorted({loader for v in found for loader in v.get("loaders", [])})


titles = {}


def title(project_id):
    if project_id not in titles:
        project = get(f"{API}/project/{project_id}")
        titles[project_id] = project["title"] if project else project_id
    return titles[project_id]


def curseforge(cf_slug):
    if not cf_slug:
        return None
    data = get(f"https://api.cfwidget.com/minecraft/mc-mods/{cf_slug}")
    if not data or "files" not in data:
        return "CurseForge: not found"
    files = [f for f in data["files"] if "1.20.1" in f.get("versions", []) and any(v.lower() == "forge" for v in f.get("versions", []))]
    if not files:
        loaders = sorted({v for f in data["files"] if "1.20.1" in f.get("versions", []) for v in f.get("versions", []) if not v[0].isdigit()})
        return f"CurseForge '{data.get('title')}': no Forge 1.20.1 file (1.20.1 files for: {', '.join(loaders) or 'none'})"
    newest = max(files, key=lambda f: f.get("uploaded_at", ""))
    return f"CurseForge '{data.get('title')}': Forge 1.20.1 {newest.get('display') or newest.get('name')} ({newest.get('uploaded_at', '')[:10]})"


lines = []
for name, slug, cf_slug, kind in ENTRIES:
    loader = "minecraft" if kind == "pack" else "forge"
    project, how = modrinth_project(name, slug, kind)
    if project is None:
        lines.append(f"[CF?] {name}: not on Modrinth (search: {how or 'nothing'}) | {curseforge(cf_slug) or 'no CurseForge slug'}")
        continue
    found = versions(project, loader)
    found = [v for v in found if kind == "pack" or "forge" in v.get("loaders", [])]
    head = f"{name} -> Modrinth '{project['title']}' ({project['slug']}, by {how}, {project.get('project_type')}, client {project.get('client_side')}, server {project.get('server_side')})"
    if found:
        newest = found[0]
        deps = [title(d["project_id"]) for d in newest.get("dependencies", []) if d.get("dependency_type") == "required" and d.get("project_id")]
        lines.append(f"[OK] {head}: Forge 1.20.1 {newest['version_number']} ({newest['date_published'][:10]}, {newest['version_type']})"
                     + (f"; needs {', '.join(deps)}" if deps else ""))
    else:
        other = any_1201(project)
        lines.append(f"[NO-MR] {head}: no {loader} 1.20.1 build (1.20.1 on: {', '.join(other) or 'none'}) | {curseforge(cf_slug) or 'no CurseForge slug'}")

text = "\n".join(lines)
print(text)
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
chunks, current = [], ""
for line in lines:
    if len(current) + len(line) + 1 > 3800:
        chunks.append(current)
        current = ""
    current += line + "\n"
chunks.append(current)
kinds = ["notice", "warning", "error"]
for n, chunk in enumerate(chunks[:30]):
    print(f"::{kinds[n // 10]} title=modcheck-{n:02d}::{esc(chunk)}")
