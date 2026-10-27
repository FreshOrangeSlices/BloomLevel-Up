import json, urllib.request
UA = {"User-Agent": "FreshOrangeSlices/BloomLevel-Up modcheck"}
vs = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/azurelib/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D", headers=UA)))
p = json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2/project/azurelib", headers=UA)))
v = next(v for v in vs if v["version_number"] == "3.1.23")
f = next((f for f in v["files"] if f["primary"]), v["files"][0])
rec = {"project": p["id"], "id": v["id"], "file": f["filename"], "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"], "size": f["size"],
       "required": [[d.get("project_id"), d.get("version_id")] for d in v["dependencies"] if d["dependency_type"] == "required" and d.get("project_id")]}
print("::notice title=az::" + json.dumps(rec, separators=(",", ":")))
