import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
PAT = re.compile(r'swift.?strike|flurry|cleave|heavy.?slam', re.I)
try:
    for name, url in json.load(open('probe/jars2.json')).items():
        try:
            path = 'j_' + re.sub(r'\W', '_', name) + '.jar'; open(path, 'wb').write(get(url)); z = zipfile.ZipFile(path)
        except Exception as e:
            p(f"#### {name}: download failed {e}"); continue
        hits = []
        for n in z.namelist():
            if PAT.search(n) and not n.endswith(('.png', '.ogg')):
                hits.append(f"file {n}" + (": " + z.read(n).decode('utf8', 'replace')[:500].replace('\n', ' ') if n.endswith('.json') and ('spell_assignments' in n or 'spells/' in n or 'weapon' in n) else ''))
            if '/lang/en_us' in n:
                for k, v in json.loads(z.read(n).decode('utf8', 'replace')).items():
                    if PAT.search(k) or PAT.search(str(v)):
                        hits.append(f"lang {k}: {str(v)[:220]}")
            if 'spell_assignments' in n or 'spell_container' in n:
                hits.append(f"assign {n}: {z.read(n).decode('utf8','replace')[:300].replace(chr(10),' ')}")
        p(f"#### {name} ({len(hits)} hits)")
        for h in hits[:70]: p(h)
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
