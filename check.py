import json, urllib.request, urllib.parse, sys, time

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up mod-check (github actions)"}

def get(path, **params):
    q = "&".join(f"{k}={urllib.parse.quote(json.dumps(v) if isinstance(v, list) else str(v))}" for k, v in params.items())
    url = f"{API}{path}" + (f"?{q}" if q else "")
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(2)
        except Exception:
            time.sleep(2)
    return "ERR"

versions = [v["version"] for v in get("/tag/game_version") if v["version"].startswith("26.3")]
GROUPS = {
 "content": "dungeons-and-taverns ati-structures-fabricforge boss-checklist aerial-hell mss-moogs-soaring-structures mes-moogs-end-structures repurposed-structures-fabric realms-reforged bosses-of-mass-destruction simply-swords origins-legacy origins-legacy-classes ruins-and-towers",
 "perf": "sodium lithium ferrite-core immediatelyfast entityculling krypton moreculling c2me-fabric",
 "visual": "iris complementary-reimagined lambdynamiclights continuity bettergrassify entity-model-features entitytexturefeatures distanthorizons sodium-extra reeses-sodium-options",
 "anim": "fresh-animations fa-player-extension not-enough-animations better-animations-collection better-combat",
 "life": "village-builder totem-villagers-workforce villager-guards villager-names-serilum homesteads variants-and-ventures undeadriders ambient-creatures friends-and-foes tiny-skeletons cave-horror-datapack illager-expansion-polymer wandering-trader-maps more-villagers-re-employed",
 "qol": "emi jei appleskin modmenu jade wthit xaeros-minimap xaeros-world-map journeymap mouse-tweaks controlling zoomify farmers-delight-refabricated explorers-compass natures-compass waystones lootr yungs-api yungs-better-dungeons yungs-better-mineshafts yungs-better-strongholds towns-and-towers terralith tectonic travelers-titles sound-physics-remastered fallingleaves particle-rain presence-footsteps simple-voice-chat ping-wheel supplementaries moonlight handcrafted comforts shulkerboxtooltip trinkets accessories clumps universal-graves gravestones open-parties-and-claims inventory-profiles-next cloth-config architectury-api owo-lib yacl patchouli geckolib neat damage-numbers carpet worldedit",
}
SEARCH = ["World Bosses", "Villagers at Work", "Villagers after Work", "More Villagers at Work", "Brutal Bosses", "EMF Compat Better Combat", "EMF Compat Not Enough Animations", "Village Builder pandorical"]

def check(slug):
    p = get(f"/project/{slug}")
    if p is None:
        return f"{slug}: NOT FOUND"
    if p == "ERR":
        return f"{slug}: ERROR"
    loaders = ["fabric"]
    if p.get("project_type") in ("resourcepack",):
        loaders = ["minecraft"]
    elif p.get("project_type") == "shader":
        loaders = ["iris"]
    elif "fabric" not in p.get("loaders", []) and "datapack" in p.get("loaders", []):
        loaders = ["datapack"]
    vs = get(f"/project/{slug}/version", game_versions=versions, loaders=loaders) or []
    if vs == "ERR":
        return f"{slug}: ERROR versions"
    title = p.get("title")
    if vs:
        v = vs[0]
        return f"{slug} ({title}): {len(vs)} builds for 26.3 [{','.join(loaders)}], latest {v['version_number']} [{v['version_type']}] {v['date_published'][:10]}"
    gv = p.get("game_versions", [])
    return f"{slug} ({title}): NO 26.3 {','.join(loaders)} build; newest listed MC versions {gv[-4:]} ; updated {p.get('updated','')[:10]}"

out = {}
for group, slugs in GROUPS.items():
    lines = [check(s) for s in slugs.split()]
    out[group] = lines
lines = []
for term in SEARCH:
    r = get("/search", query=term, limit=4)
    hits = [] if r in (None, "ERR") else r.get("hits", [])
    lines.append(f"search '{term}': " + " || ".join(f"{h['title']} slug={h['slug']} versions={h.get('versions', [])[-3:]}" for h in hits))
out["search"] = lines
out["versions"] = [", ".join(versions)]
for group, lines in out.items():
    msg = "\n".join(lines).replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::notice title=mods-{group}::{msg}")
