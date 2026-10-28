#!/bin/bash
# Throwaway: does Myths & Legends' Cogwork Construct (crashes the first client that sees it) spawn by itself, and where
# do Legionary's Unbreakable (its attacks crash a dedicated server) and the rest come from?
python3 - <<'PY' > out.txt 2>&1
import json, urllib.request, zipfile, io, re
def get(url, raw=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bloom-ci"})) as r:
        return r.read() if raw else json.load(r)
for name, vid, words in [("mythsandlegends", "J9qH2IC8", [b"cogwork"]), ("legionary", "zCtV7TZo", [b"unbreakable"])]:
    v = get(f"https://api.modrinth.com/v2/version/{vid}")
    z = zipfile.ZipFile(io.BytesIO(get(v["files"][0]["url"], raw=True)))
    print("==", name, v["version_number"])
    for n in sorted(z.namelist()):
        if "biome_modifier" in n or "/structure" in n or "spawn" in n.lower():
            b = z.read(n)
            if n.endswith(".json"):
                hit = [w.decode() for w in words if w in b]
                print(" ", n, ("MENTIONS " + ",".join(hit)) if hit else "", b[:300].decode(errors="replace").replace("\n", " ") if hit else "")
            elif any(w in n.encode() for w in words) or any(w in b for w in words):
                print(" ", n, "(binary mentions)")
    for n in z.namelist():
        if n.endswith(".class"):
            b = z.read(n)
            for w in words:
                if w in b and (b"SpawnPlacement" in b or b"m_21662_" in b or b"BiomeModifier" in b or b"addSpawn" in b or b"EntityJoinLevel" in b or b"m_7967_" in b):
                    print("  class", n, "mentions", w.decode(), "and spawning code")
PY
cat out.txt
echo "::notice title=spawns::$(head -c 3800 out.txt | tr '\n' '|' | sed 's/%/%25/g')"
