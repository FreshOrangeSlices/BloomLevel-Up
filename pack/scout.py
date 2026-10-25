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
    "magic": ["spells", "magic", "wizard"], "dodge": ["dodge roll", "combat roll"], "bosses": ["bosses", "boss"],
    "weapons": ["weapons", "swords"], "armor": ["armor sets", "knight armor"], "biomes": ["biomes", "terrain generation"],
    "medieval": ["medieval", "castle", "dungeons structures"], "dragons": ["dragons"], "classes": ["rpg classes"],
}
seen = set()
for topic, queries in topics.items():
    out.append(f"== {topic}")
    for query in queries:
        res = get("/search", query=query, limit="12", index="relevance",
                  facets=[["versions:26.3"], ["categories:fabric"], ["project_type:mod"]]) or {"hits": []}
        for h in res["hits"]:
            if h["slug"] in seen:
                continue
            seen.add(h["slug"])
            out.append(f"{h['slug']} | {h['title']} | {h['downloads']:,} dl | {h['description'][:110]}")
cands = ["spell-engine", "wizards", "paladins-and-priests", "archers", "rogues-and-warriors", "combat-roll", "spell-power",
         "jewelry", "simply-swords", "epic-knights-shields-armor-and-weapons", "bosses-of-mass-destruction",
         "mariums-soulslike-weaponry", "terralith", "regions-unexplored", "biomes-o-plenty", "wwoo", "tectonic",
         "towns-and-towers", "yungs-better-dungeons", "when-dungeons-arise", "l_enders-cataclysm", "mowzies-mobs",
         "illager-invasion", "born-in-chaos", "arcanus", "magic-spellblades", "relics-mod", "artifacts", "medieval-buildings",
         "the-graveyard", "friends-and-foes", "dragons-survival", "end-remastered", "nullscape", "incendium", "spell-engine-extension"]
out.append("== candidates: newest Minecraft version with a Fabric build")
for slug in cands:
    p = get(f"/project/{slug}")
    if not p:
        out.append(f"{slug}: not found")
        continue
    vs = get(f"/project/{slug}/version", loaders=["fabric"]) or []
    newest = vs[0]["game_versions"][-3:] if vs else []
    has = any("26.3" in v["game_versions"] for v in vs)
    out.append(f"{slug} ({p['title']}): {'26.3 YES' if has else 'no 26.3'}; newest {newest}; {p['downloads']:,} dl")
text = "\n".join(out)
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
chunks = [text[i:i + 3800] for i in range(0, len(text), 3800)]
for n, c in enumerate(chunks[:20]):
    print(f"::{'notice' if n < 10 else 'warning'} title=scout-{n:02d}::{esc(c)}")
