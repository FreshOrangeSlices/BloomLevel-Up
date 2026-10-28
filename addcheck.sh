#!/bin/bash
# Throwaway: facts for adding Every Compat, Keybind Atlas and Zoni's Companions to LegionWareX (key names, Zoni's tabs,
# JEI's hidden-ingredient file, global datapack loaders). Pushed to ci/data (adding/).
set -u
mkdir -p data-out jars
python3 - <<'PY' > data-out/modrinth.txt 2>&1
import json, urllib.request
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return json.load(r)
API = "https://api.modrinth.com/v2"
want = {"keybind-atlas": "fokFjwwh", "zonis": "sZtlm7ZN", "every-compat": "eiktJyw1", "jei": "u6dRKJwZ"}
for q in ["open loader", "paxi", "global packs", "global datapacks", "datapack loader"]:
    print("search", q, [(h["slug"], h["project_id"], h["downloads"]) for h in get(f"{API}/search?query={q.replace(' ', '%20')}&limit=5&facets=%5B%5B%22categories%3Aforge%22%5D%5D")["hits"]])
for slug in ["open-loader", "paxi", "globalpacks", "global-packs"]:
    try:
        p = get(f"{API}/project/{slug}")
    except Exception as e:
        print(slug, "?", e); continue
    vs = get(f"{API}/project/{p['id']}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")
    print("==", slug, p["id"], p["downloads"], p["client_side"], p["server_side"], "updated", p["updated"][:10])
    for v in vs[:2]:
        print("  ", v["version_number"], v["date_published"][:10], v["files"][0]["filename"], [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]])
    if vs:
        want[slug] = p["id"]
    print("  body:", (p.get("body") or "")[:2500].replace("\n", " | "))
for name, pid in want.items():
    v = get(f"{API}/project/{pid}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D")[0]
    print("jar", name, v["version_number"], v["files"][0]["filename"], [(d.get("project_id"), d["dependency_type"]) for d in v["dependencies"]])
    with urllib.request.urlopen(urllib.request.Request(v["files"][0]["url"], headers={"User-Agent": "bloom-ci"})) as r:
        open(f"jars/{name}.jar", "wb").write(r.read())
PY
cat data-out/modrinth.txt
for jar in jars/*.jar; do
  name=$(basename "$jar" .jar); dir="x-$name"; mkdir -p "$dir" && (cd "$dir" && unzip -q -o "../$jar")
  {
    echo "== mods.toml"; cat "$dir/META-INF/mods.toml" 2>/dev/null | head -60
    echo "== non-class files"; (cd "$dir" && find . -type f ! -name '*.class' ! -name '*.png' | grep -v -E '/(textures|models|sounds|geo|animations|blockstates)/' | sort | head -80)
    echo "== lang key/keybind entries"; for f in $(cd "$dir" && find ./assets -name 'en_us.json' 2>/dev/null); do grep -i -E '"key\.|category|keybind' "$dir/$f" | head -30; done
    echo "== KeyMapping registrations"
    for c in $(cd "$dir" && grep -l -r -a --include='*.class' -E 'KeyMapping|m_90837_|lookupActive' . | sort | head -20); do
      cls="$(echo "${c#./}" | sed 's/\.class$//; s#/#.#g')"; echo "--- $cls"
      javap -c -p -constants -cp "$dir" "$cls" 2>/dev/null | grep -E 'KeyMapping\."<init>"|ldc|bipush|sipush|InputConstants|KeyConflict|KeyModifier|glfwGetKey|GLFW' | head -40
    done
    echo "== strings about blacklist/hidden/config paths"
    for c in $(cd "$dir" && grep -l -r -a --include='*.class' -i -E 'blacklist|hiddenIngredients|openloader|load_dirs|datapacks' . | sort | head -25); do
      echo "--- $c: $(strings -n 5 "$dir/$c" | grep -i -E 'blacklist|hidden|\.cfg|\.json|openloader|packs|load_dirs|data|config/' | sort -u | head -25 | tr '\n' ' ')"
    done
  } > "data-out/$name.txt" 2>&1
done
# Zoni's creative tabs: which items go where.
javap -c -p -cp x-zonis net.zoni.zoniscompanions.init.ZonisCompanionsModTabs 2>/dev/null | grep -E 'ldc|getstatic.*ModItems|lambda' > data-out/zonis-tabs.txt
ls x-zonis/net/zoni/zoniscompanions/init/ >> data-out/zonis-tabs.txt
git clone -q --depth 1 -b ci/data "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" data
mkdir -p data/adding && cp data-out/*.txt data/adding/ && cd data && git add -A
git -c user.name=ci -c user.email=ci@example.invalid commit -qm "adding mods" && git push -q origin ci/data && echo pushed
