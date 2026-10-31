"""Throwaway: Voiceless Survival's entities_voices.json from the pack test's server, summarised.
Usage: voiceless2.py <server.zip> <shots dir> <out dir>"""
import json, os, sys, collections
_, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
report = []
path = os.path.join(shots, "server", "config", "ezvcsurvival", "entities_voices.json")
d = json.load(open(path))
report.append("top keys: " + ", ".join(f"{k} ({type(v).__name__}{', ' + str(len(v)) if isinstance(v, (dict, list)) else ''})" for k, v in d.items()))
for section, val in d.items():
    if not isinstance(val, dict):
        report.append(f"{section} = {val}")
        continue
    on = {k: v for k, v in val.items() if isinstance(v, dict) and v.get("enabled")}
    off = [k for k, v in val.items() if isinstance(v, dict) and not v.get("enabled")]
    report.append(f"## {section}: {len(on)} on, {len(off)} off")
    for k, v in sorted(on.items()):
        report.append(f"  ON {k} {json.dumps(v)}")
    report.append("  off namespaces: " + json.dumps(collections.Counter(k.split(':')[0] for k in off)))
    report.append("  off vanilla: " + ", ".join(k for k in off if k.startswith("minecraft:")))
gs = os.path.join(shots, "server", "config", "ezvcsurvival", "generalsounds.json")
g = json.load(open(gs))
report.append("generalsounds top keys: " + ", ".join(g.keys()))
report.append(json.dumps(g)[:1500])
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
