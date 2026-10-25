# What exists on Modrinth for Minecraft 26.3 (Fabric) per wish: magic, dodge roll, bosses, weapons, armor, biomes,
# medieval structures, dragons. Plus a check of well-known candidates (newest version they support).
import json, sys, time, urllib.parse, urllib.request
API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up scouting (github actions)"}
def get(path, **params):
    q = "&".join(f"{k}={urllib.parse.quote(v if isinstance(v, str) else json.dumps(v))}" for k, v in params.items())
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(f"{API}{path}?{q}", headers=UA), timeout=60) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(3)
        except Exception:
            time.sleep(3)
    return None
out = []
topics = {
    "bosses": ["boss", "bosses", "boss fight", "mini boss", "dungeon boss", "raid", "boss rush"],
    "monsters": ["monsters", "mobs", "creatures", "new mobs", "hostile mobs", "undead"],
    "elites": ["elite mobs", "champions", "difficulty", "stronger mobs", "mob levels", "affixes"],
    "dungeons": ["dungeon", "dungeons", "ruins", "catacombs", "tower"],
    "fantasy": ["fantasy", "rpg", "adventure", "mythology", "knight"],
}
seen = set()
for topic, queries in topics.items():
    out.append(f"== {topic}")
    for query in queries:
        for version in ("26.3", "26.2"):
            res = get("/search", query=query, limit="15", index="relevance",
                      facets=[[f"versions:{version}"], ["categories:fabric", "categories:datapack"]]) or {"hits": []}
            for h in res["hits"]:
                if h["slug"] in seen or h["downloads"] < 3000:
                    continue
                seen.add(h["slug"])
                tag = "" if version == "26.3" else "  [26.2 only]"
                out.append(f"{h['slug']} | {h['title']} | {h['downloads']:,} dl | {h.get('date_modified','')[:10]} | {h['description'][:120]}{tag}")
cands = ["the-twilight-forest", "twilightforest", "aether", "the-aether", "eternal-starlight", "adventurez", "ruins-and-towers",
         "remnants-eclipsed-realm", "gateway-of-doom", "brutal-nightmare", "deadly-deadly-dungeon", "useless-reptile",
         "stellarity", "incendium", "deeper-and-darker", "the-bumblezone-fabric", "bosses-of-mass-destruction", "mutant-monsters",
         "mobs-of-mythology", "illagers-plus", "illager-invasion", "graveyard-fabric", "the-graveyard", "dungeons-plus",
         "creeper-overhaul", "naturalist", "alexs-mobs", "mythic-mounts", "galosphere", "spawn"]
out.append("== candidates: newest Minecraft version with a Fabric build")
for slug in cands:
    p = get(f"/project/{slug}")
    if not p:
        out.append(f"{slug}: not found")
        continue
    vs = get(f"/project/{slug}/version", loaders=["fabric"]) or []
    newest = vs[0]["game_versions"][-3:] if vs else []
    has = any("26.3" in v["game_versions"] for v in vs)
    out.append(f"{slug} ({p['title']}): {'26.3 YES' if has else 'no 26.3'}; newest fabric {newest}; loaders {p.get('loaders')}; {p['downloads']:,} dl; {p['description'][:100]}")
text = "\n".join(out)
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
chunks = [text[i:i + 3800] for i in range(0, len(text), 3800)]
for n, c in enumerate(chunks[:20]):
    print(f"::{'notice' if n < 10 else 'warning'} title=scout-{n:02d}::{esc(c)}")
