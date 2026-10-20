import json, urllib.parse, urllib.request
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up compat check"})
    return json.load(urllib.request.urlopen(req, timeout=30))
slugs = ["dungeons-and-taverns","ati-structures-fabricforge","boss-checklist","aerial-hell","mss-moogs-soaring-structures","mes-moogs-end-structures",
 "repurposed-structures-fabric","origins-legacy","sodium","lithium","ferrite-core","immediatelyfast","entityculling","krypton","moreculling","c2me-fabric",
 "iris","continuity","bettergrassify","entity-model-features","entitytexturefeatures","distanthorizons","sodium-extra","reeses-sodium-options",
 "not-enough-animations","better-animations-collection","better-combat","villager-guards","villager-names-serilum","homesteads","variants-and-ventures",
 "undeadriders","ambient-creatures","friends-and-foes","tiny-skeletons","illager-expansion-polymer","wandering-trader-maps","more-villagers-re-employed",
 "fabric-api","towns-and-towers","yacl","damage-numbers"]
for slug in slugs:
    q = urllib.parse.urlencode({"loaders": json.dumps(["fabric", "datapack", "quilt"]), "game_versions": json.dumps(["26.3"])})
    try:
        v = get(f"https://api.modrinth.com/v2/project/{slug}/version?" + q)
        print(f"{slug}: " + (f"{len(v)} builds, latest {v[0]['version_number']} [{v[0]['version_type']}] {v[0]['date_published'][:10]} loaders={','.join(v[0]['loaders'])}" if v else "NO fabric/datapack 26.3 build"))
    except Exception as e:
        print(f"{slug}: ERROR {e}")
for name in ["World Bosses", "Village Builder", "Villagers at Work", "Villagers after Work", "More Villagers at Work", "Realms Reforged"]:
    q = urllib.parse.urlencode({"query": name, "limit": 3})
    hits = get("https://api.modrinth.com/v2/search?" + q)["hits"]
    print(f"search '{name}': " + " || ".join(f"{h['title']} slug={h['slug']} versions={h.get('versions', [])[-4:]}" for h in hits))
