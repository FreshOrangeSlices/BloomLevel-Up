import os, re, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(cls):
    r = subprocess.run(['java', '-jar', 'cfr.jar', 'j.jar', '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ')]
out = []; p = out.append
try:
    url = json.load(open('probe/jars4.json'))['backport-copper-age']
    open('j.jar', 'wb').write(get(url)); z = zipfile.ZipFile('j.jar'); names = z.namelist()
    langs = [n for n in names if n.endswith('lang/en_us.json')]
    p("lang files: " + ", ".join(langs))
    lang = json.loads(z.read(langs[0]))
    p("sample keys: " + ", ".join(k for k in list(lang)[:12]))
    p("golem lang: " + " | ".join(f"{k}={v}" for k, v in lang.items() if re.search(r'(?i)golem', k))[:1500])
    for n in names:
        if n.endswith('.class') and re.search(rb'CARVED_PUMPKIN|f_50133_|f_50143_', z.read(n)):
            src = dec(n[:-6].replace('/', '.'))
            p(f"==== {n}\n" + "\n".join(l for l in src if re.search(r'(?i)pumpkin|copper|golem|spawn|f_50133_|f_50143_|BlockPattern|aisle', l))[:2500])
    for n in names:
        if re.search(r'(Constants|CommonClass|ModItems|ModBlocks)\.class$', n):
            src = dec(n[:-6].replace('/', '.'))
            p(f"==== {n}\n" + "\n".join(l for l in src if re.search(r'MOD_ID|\"minecraft\"|ResourceLocation|NAMESPACE|withDefaultNamespace|copper_chest', l))[:1800])
except Exception: p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/copper.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
