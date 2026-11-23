import os, re, sys, json, zipfile, urllib.request, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
try:
    jars = json.load(open('probe/jars.json'))
    open('me.jar', 'wb').write(get(jars['monster-expansion'])); z = zipfile.ZipFile('me.jar')
    for n in z.namelist():
        if 'kobeko' in n.lower() and n.startswith('data/') and n.endswith('.json'):
            p(f"==== {n}\n{z.read(n).decode()[:900]}")
    lang = json.loads(z.read([n for n in z.namelist() if n.endswith('lang/en_us.json')][0]))
    p("lang: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if 'kobeko' in k.lower())[:2500])
    p("kobeko files: " + ", ".join(n for n in z.namelist() if 'kobeko' in n.lower() and not n.endswith('.png'))[:2000])
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
