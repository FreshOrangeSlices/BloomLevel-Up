import json, sys, urllib.parse, urllib.request
names = [
 "Dungeons and Taverns", "World Bosses", "ATi Structures", "Boss Checklist", "Aerial Hell", "Realms Reforged",
 "Moog's Soaring Structures", "Moog's End Structures", "Repurposed Structures", "Origins: Legacy", "Origins Legacy: Classes",
 "Sodium", "Lithium", "FerriteCore", "ImmediatelyFast", "Entity Culling", "Krypton", "More Culling", "C2ME", "Iris Shaders",
 "Complementary Reimagined", "Continuity", "BetterGrassify", "Entity Model Features", "Entity Texture Features",
 "Distant Horizons", "Sodium Extra", "Reese's Sodium Options", "Fresh Animations", "Not Enough Animations",
 "Better Animations Collection", "Fresh Animations: Player Extension", "Better Combat", "Village Builder", "Villagers at Work",
 "Villager Guards", "Villager Names", "Homesteads", "Variants&Ventures", "Undead Riders", "Ambient Creatures", "Friends&Foes",
 "Tiny Skeletons", "Cave Horror", "Illager Expansion Recrafted", "Wandering Trader Maps", "More Villagers",
 "Fabric API", "Mod Menu", "YACL", "Damage Numbers", "Carpet", "WorldEdit", "GeckoLib"]
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up compat check"})
    return json.load(urllib.request.urlopen(req, timeout=30))
for name in names:
    q = urllib.parse.urlencode({"query": name, "limit": 3, "facets": json.dumps([["versions:26.3"]])})
    try:
        hits = get("https://api.modrinth.com/v2/search?" + q)["hits"]
    except Exception as e:
        print(f"{name}: ERROR {e}"); continue
    if hits:
        h = hits[0]
        loaders = [c for c in h.get("categories", []) if c in ("fabric", "quilt", "neoforge", "forge", "datapack", "iris", "minecraft", "optifine", "canvas")]
        print(f"{name}: 26.3 -> {h['title']} | slug={h['slug']} | type={h['project_type']} | loaders={','.join(loaders)} | others={[x['title'] for x in hits[1:]]}")
    else:
        q2 = urllib.parse.urlencode({"query": name, "limit": 1})
        top = get("https://api.modrinth.com/v2/search?" + q2)["hits"]
        t = top[0] if top else None
        print(f"{name}: NO 26.3 match" + (f" (closest overall: {t['title']} slug={t['slug']} latest versions={t.get('versions', [])[-3:]})" if t else ""))
