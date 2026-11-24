import os, re, json, zipfile, subprocess, urllib.request, traceback, io
def get(url):
    req = urllib.request.Request(url.replace(' ', '%20'), headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
def dec(cls, jar):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ') and not l.startswith('/*')]
jars = {}
for f in ['probe/jars.json', 'probe/jars2.json', 'probe/jars3.json']:
    jars.update(json.load(open(f)))
out = []; p = out.append
try:
    open('bic.jar', 'wb').write(get(jars['borninchaos']))
    src = dec('net.mcreator.borninchaosv.procedures.TransmutingElixirPriShchielchkiePKMPoBlokuProcedure', 'bic.jar')
    p("\n".join(l[:300] for l in src)[:7000])
    z = zipfile.ZipFile('bic.jar')
    lang = json.loads(z.read('assets/born_in_chaos_v1/lang/en_us.json').decode('utf-8', 'replace'))
    for k, v in lang.items():
        if re.search(r'(?i)chaos_component|seedof_chaos|intoxicating_decoction', k):
            p(f"lang {k} = {v}")
    for n in z.namelist():
        if n.startswith('data/') and n.endswith('.json') and ('chaos_component' in z.read(n).decode('utf-8', 'replace')):
            p(f"-- {n}: {re.sub(chr(10) + '| {2,}', '', z.read(n).decode('utf-8', 'replace'))[:500]}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/summon.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
