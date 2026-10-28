"""Throwaway: for each weapon in the dump, the text keys and constants its own classes use (javap), to tell what it
does when its tooltip can't be read on a server. Written to data-out/weapons/code.json."""
import io, json, os, re, subprocess, sys, tempfile, zipfile, collections
weapons_path, server_zip, out = sys.argv[1], sys.argv[2], sys.argv[3]
weapons = json.load(open(weapons_path))
by_ns = collections.defaultdict(list)
for w in weapons:
    by_ns[w["id"].split(":")[0]].append(w)
result = {}
with zipfile.ZipFile(server_zip) as server:
    for jar in [n for n in server.namelist() if n.startswith("mods/") and n.endswith(".jar")]:
        data = server.read(jar)
        z = zipfile.ZipFile(io.BytesIO(data))
        names = z.namelist()
        spaces = {m.group(1) for n in names for m in [re.match(r"assets/([^/]+)/", n)] if m} & set(by_ns)
        if not spaces:
            continue
        classes = collections.defaultdict(list)
        for n in names:
            if n.endswith(".class"):
                classes[n.rsplit("/", 1)[-1][:-6]].append(n[:-6].replace("/", "."))
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "mod.jar")
        open(path, "wb").write(data)
        cache = {}
        def javap(fqn):
            if fqn not in cache:
                text = subprocess.run(["javap", "-c", "-p", "-constants", "-cp", path, fqn], capture_output=True, text=True).stdout
                strings = re.findall(r"// String (.+)$", text, re.M)
                statics = re.findall(r"getstatic\s+#\d+\s+// Field ([\w/$]+)\.(\w+):L([\w/$]+);", text)
                cache[fqn] = (strings, [f"{o.rsplit('/', 1)[-1]}.{f}" for o, f, t in statics if o == t])  # enum-like: a field of its own type
            return cache[fqn]
        for ns in spaces:
            for w in by_ns[ns]:
                strings, statics = [], []
                for simple in w.get("classes", []):
                    for fqn in classes.get(simple, [])[:2]:
                        s, f = javap(fqn)
                        strings += s
                        statics += f
                result[w["id"]] = {"strings": sorted(set(strings)), "statics": sorted(set(statics))}
        print(jar, sorted(spaces), len(cache), "classes read")
json.dump(result, open(os.path.join(out, "code.json"), "w"))
print("weapons with code read:", len(result))
