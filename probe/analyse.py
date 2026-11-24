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
    src = dec('net.mindoth.fabledweaponry.event.CommonEvents', 'fw.jar')
    p("==== CommonEvents\n" + "\n".join(l[:330] for l in src))
    for n in names:
        if n.endswith('.class') and ('MaceItem' in n or 'Config' in n) and '$' not in n:
            p(f"---- {n}\n" + "\n".join(l[:300] for l in dec(n[:-6].replace('/', '.'), 'fw.jar'))[:3000])
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/fabled2.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
