import os, re, json, zipfile, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
out = []; p = out.append
jars = json.load(open('probe/jars4.json'))
try:
    open('j.jar', 'wb').write(get(jars['mariums-soulslike-weaponry'])); z = zipfile.ZipFile('j.jar'); names = z.namelist()
    for n in names:
        if re.search(r'recipes?/.*(silver_bullet|hunter_pistol|blunderbuss|gatling|hunter_cannon|gun)', n) and n.endswith('.json'):
            p(f"recipe {n.split('/')[-1]}: {re.sub(r'\\s+', ' ', z.read(n).decode())[:500]}")
    lang = json.loads(z.read([n for n in names if n.endswith('lang/en_us.json')][0]))
    p("lang: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if re.search(r'(?i)silver_bullet|hunter_pistol|blunderbuss|gatling|hunter_cannon|\bgun', k))[:4000])
except Exception: p(traceback.format_exc())
try:
    open('j.jar', 'wb').write(get(jars['paladins-and-priests'])); z = zipfile.ZipFile('j.jar')
    lang = json.loads(z.read([n for n in z.namelist() if n.endswith('lang/en_us.json')][0]))
    p("paladins lang hints: " + "\n".join(f"{k}: {v}" for k, v in lang.items() if re.search(r'(?i)skill|hotbar|cast|bypass', k))[:1500])
except Exception: p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/guns.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
