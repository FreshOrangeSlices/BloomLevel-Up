"""Throwaway: boss and creature mods for Forge 1.20.1 on Modrinth (search + a list of known names)."""
import json, sys, time, urllib.parse, urllib.request, urllib.error
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
def get(path, **params):
    q = "&".join(f"{k}={urllib.parse.quote(v if isinstance(v, str) else json.dumps(v))}" for k, v in params.items())
    for attempt in range(4):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request(f"https://api.modrinth.com/v2{path}" + (f"?{q}" if q else ""), headers=UA), timeout=60))
        except urllib.error.HTTPError as e:
            if e.code == 404: return None
            time.sleep(3)
    return None
seen, rows = set(), []
def row(p, src):
    slug = p["slug"]
    if slug in seen: return
    seen.add(slug)
    vs = get(f"/project/{slug}/version", game_versions=["1.20.1"], loaders=["forge"]) or []
    v = vs[0] if vs else None
    deps = []
    if v:
        for d in v["dependencies"]:
            if d["dependency_type"] == "required" and d.get("project_id"):
                dp = get(f"/project/{d['project_id']}")
                deps.append(dp["slug"] if dp else d["project_id"])
    rows.append((p.get("downloads", 0), f"{slug} | {p['title']} | {p.get('downloads',0):,} dl | upd {p.get('updated', p.get('date_modified',''))[:10]} | "
                 + (f"forge 1.20.1 {v['version_number']} [{v['version_type']}] {v['date_published'][:10]} deps={deps}" if v else "NO forge 1.20.1")
                 + f" | cats={p.get('categories', [])[:6]} | {src} | {p.get('description','')[:150]}"))
for query in ["boss", "bosses", "boss fight", "dungeon boss", "monsters", "creatures", "dungeons", "fantasy mobs", "medieval", "undead", "dragon", "giant", "illager"]:
    r = get("/search", query=query, facets=[["project_type:mod"], ["versions:1.20.1"], ["categories:forge"]], index="relevance", limit="20") or {"hits": []}
    for h in r["hits"]:
        if any(c in h.get("categories", []) + h.get("display_categories", []) for c in ["mobs", "adventure", "magic", "equipment", "worldgen"]):
            row(h, f"search:{query}")
known = ["l_enders-cataclysm", "cataclysm", "lenders-cataclysm", "bosses-rise", "bossesrise", "born-in-chaos", "the-twilight-forest", "twilightforest",
         "the-aether", "aether", "blue-skies", "illager-invasion", "illage-and-spillage", "goety", "the-graveyard", "the-graveyard-forge",
         "alexs-mobs", "alexs-caves", "undead-unleashed", "friends-and-foes-forge", "friends-and-foes", "deeper-and-darker", "integrated-dungeons-and-structures",
         "when-dungeons-arise", "dungeons-and-taverns", "eldritch-end", "bosses-of-mass-destruction-forge", "bosses-of-mass-destruction", "mobz",
         "the-conjurer", "illagers-plus", "knight-quest", "dungeons-mobs", "mowzies-cataclysm", "irons-spells-n-spellbooks", "ender-dragon-fight",
         "better-mob-combat", "valarian-conquest", "undergarden", "the-undergarden", "deep-aether", "nyfs-spiders", "whisperwoods", "grimoire-of-gaia",
         "grimoireofgaia", "dungeon-crawl", "dungeon-now-loading", "yungs-better-dungeons", "lionfish-api", "ancient-aether", "unusual-prehistory",
         "the-bumblezone", "spore", "creeper-overhaul", "doom-and-gloom", "enemy-expansion", "kobolds", "guard-villagers", "regions-unexplored",
         "illager-additions", "villager-and-illager-additions", "invade", "the-dungeon-raiders", "obscure-api", "medieval-buildings", "medieval-origins",
         "mythic-mounts", "dragon-mounts-legacy", "wyrmroost", "naturalist", "rotten-creatures", "the-lost-castle", "the-abyss", "cataclysm-addon",
         "majruszs-difficulty", "majruszs-progressive-difficulty", "apotheosis", "champions", "epic-boss", "boss-tools", "tidal-towns", "minecolonies"]
for slug in known:
    p = get(f"/project/{slug}")
    if p: row(p, "known")
    else: rows.append((-1, f"{slug} | not on Modrinth"))
rows.sort(key=lambda r: -r[0])
text = "\n".join(r for _, r in rows)
print(text)
open("bosses.txt", "w").write(text)
