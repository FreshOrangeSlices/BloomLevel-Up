import json, urllib.request, urllib.parse, time

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

SLUGS = """fabric-api cloth-config player-animation-library playeranimator fabric-language-kotlin
emf-compat-better-combat not-enough-animations-emf-compat complementary-reimagined fresh-animations fa-player-extension
cave-horror-datapack terralith better-combat""".split()
lines = []
for s in SLUGS:
    p = get(f"/project/{s}")
    if p in (None, "ERR"):
        lines.append(f"{s}: {p}")
        continue
    lines.append(f"{s}|{p.get('project_type')}|{p.get('title')}|loaders={p.get('loaders')}|deps-check")
# Better Combat 26.3 fabric version dependencies
vs = get("/project/better-combat/version", game_versions=["26.3"], loaders=["fabric"]) or []
for v in vs[:1]:
    for d in v.get("dependencies", []):
        pid = d.get("project_id")
        p = get(f"/project/{pid}") if pid else None
        lines.append(f"better-combat dep: {d.get('dependency_type')} {pid} -> {p.get('slug') if isinstance(p, dict) else p} ({p.get('title') if isinstance(p, dict) else ''})")
    lines.append("better-combat files: " + ", ".join(f["filename"] for f in v.get("files", [])))
for term in ["Player Animation Library", "playerAnimator"]:
    r = get("/search", query=term, limit=5)
    hits = [] if r in (None, "ERR") else r.get("hits", [])
    lines.append(f"search '{term}': " + " || ".join(f"{h['title']} slug={h['slug']} versions={h.get('versions', [])[-3:]}" for h in hits))
msg = "\n".join(lines).replace("%", "%25").replace("\n", "%0A")
print(f"::notice title=mods-links::{msg}")
