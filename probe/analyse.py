import os, re, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
def dec(cls, jar):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ') and not l.startswith('/*')]
out = []; p = out.append
try:
    data = None
    for url in ["https://mediafilez.forgecdn.net/files/5381/204/fabledweaponry-1.20.1-1.0.7.5.jar", "https://edge.forgecdn.net/files/5381/204/fabledweaponry-1.20.1-1.0.7.5.jar"]:
        try:
            data = get(url); break
        except Exception as e:
            p(f"!! {url}: {e}")
    open('fw.jar', 'wb').write(data); z = zipfile.ZipFile('fw.jar'); names = z.namelist()
    for n in names:
        if re.match(r'assets/[^/]+/lang/en_us\.json$', n):
            lang = json.loads(z.read(n).decode('utf-8', 'replace'))
            p("==== lang (tome, book, desc, tooltip, ability, passive)")
            for k, v in lang.items():
                if re.search(r'(?i)tome|book|tooltip|desc|passive|ability|skill|reforg|level|rune|enchant', k + v):
                    p(f"{k} = {v}")
    p("==== data naming tome")
    for n in names:
        if n.startswith('data/') and n.endswith('.json') and ('tome' in n or 'tome' in z.read(n).decode('utf-8', 'replace')):
            p(f"-- {n}\n{z.read(n).decode('utf-8', 'replace')[:900]}")
    p("==== classes")
    classes = [n for n in names if n.endswith('.class') and '$' not in n]
    p(f"{len(classes)} classes; tome ones:")
    tome = [n for n in classes if re.search(r'(?i)tome|book', n)]
    p("\n".join(tome))
    for n in tome[:6]:
        p(f"---- {n}\n" + "\n".join(dec(n[:-6].replace('/', '.'), 'fw.jar'))[:5000])
    # who else names the tome item
    for n in classes:
        b = z.read(n)
        if (b'TOME' in b or b'Tome' in b) and n not in tome:
            src = "\n".join(dec(n[:-6].replace('/', '.'), 'fw.jar'))
            lines = [l for l in src.split("\n") if re.search(r'(?i)tome', l)]
            p(f"---- uses tome: {n}\n" + "\n".join(l[:300] for l in lines[:20]))
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/fabled.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
