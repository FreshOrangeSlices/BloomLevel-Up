import os, re, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(cls):
    r = subprocess.run(['java', '-jar', 'cfr.jar', 'j.jar', '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ')]
out = []; p = out.append
try:
    url = json.load(open('probe/jars3.json'))['myths-and-legends']
    open('j.jar', 'wb').write(get(url)); z = zipfile.ZipFile('j.jar'); names = z.namelist()
    p("==== BloodMoonSavedData\n" + "\n".join(dec('com.ovinter.mythsandlegends.api.data.BloodMoonSavedData'))[:6000])
    for n in names:
        if n.endswith('.class') and re.search(r'BloodMoon', n) and 'SavedData' not in n and '$' not in n:
            src = dec(n[:-6].replace('/', '.'))
            p(f"==== {n}\n" + "\n".join(l for l in src if re.search(r'public|static|BloodMoonSavedData|isBloodMoon|sleep|Sleep|getDayTime|% 24000|12000|13000|23000', l))[:2500])
except Exception: p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/bloodmoon.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
