import os, re, json, zipfile, subprocess, urllib.request, traceback, io
def get(url):
    url = url.replace(' ', '%20')
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=240).read()
def dec(cls, jar):
    r = subprocess.run(['java', '-jar', 'cfr.jar', jar, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip() and not l.startswith(' *') and not l.startswith('import ') and not l.startswith('/*')]
jars = {}
for f in ['probe/jars.json', 'probe/jars2.json', 'probe/jars3.json']:
    jars.update(json.load(open(f)))
out = []; p = out.append
def entity_health(z, jar, name_re):
    for n in z.namelist():
        if n.endswith('.class') and '$' not in n and re.search(name_re, n.split('/')[-1]):
            src = dec(n[:-6].replace('/', '.'), jar)
            attrs = [l.strip() for l in src if 'm_22268_' in l or 'f_22276_' in l or 'f_22284_' in l or 'f_22281_' in l]
            if attrs:
                p(f"---- {n}\n   " + "\n   ".join(a[:220] for a in attrs[:10]))
def loot(z, words):
    for n in z.namelist():
        if n.startswith('data/') and '/loot_tables/entities/' in n and any(w in n for w in words):
            t = z.read(n).decode('utf-8', 'replace')
            p(f"-- {n}\n{re.sub(r'\\s+', ' ', t)[:1500]}")
try:
    for key, jar, name_re, words in [
        ('Bosses Oddities', 'od.jar', r'(?i)queen.*orchid.*Entity\.class$', ['queen_of_orchid']),
        ('legendary-monsters', 'lm.jar', r'(?i)obliterator.*Entity\.class$', ['obliterator']),
        ('Skarrier Mobs', 'sk.jar', r'(?i)^(Voidrake|TunnelGore|Trawler)Entity\.class$', ['voidrake', 'tunnel_gore', 'trawler']),
        ('borninchaos', 'bic.jar', r'(?i)(lord_?pumpkin|LordPumpkin|SirPumpkinhead).*Entity\.class$', ['lord_pumpkinhead', 'sir_pumpkinhead'])]:
        open(jar, 'wb').write(get(jars[key])); z = zipfile.ZipFile(jar)
        p(f"==== {key}")
        entity_health(z, jar, name_re)
        loot(z, words)
        if key == 'borninchaos':
            ents = [n for n in z.namelist() if re.search(r'(?i)lord.?pumpkin', n)]
            p("lord pumpkin files: " + ", ".join(ents[:30]))
            for n in z.namelist():
                if n.endswith('.class') and '$' not in n and 'procedures' in n:
                    b = z.read(n)
                    if b'LORD_PUMPKINHEAD' in b:
                        src = dec(n[:-6].replace('/', '.'), jar)
                        p(f"---- spawns/uses LORD_PUMPKINHEAD: {n}\n" + "\n".join(l[:240] for l in src if re.search(r'LORD_PUMPKINHEAD|getItem|m_41720_|Items\.|SubscribeEvent|execute\(|random|Math\.random|getDayTime|m_46468_', l))[:1800])
            try:
                lang = json.loads(z.read('assets/born_in_chaos_v1/lang/en_us.json').decode('utf-8', 'replace'))
                for k, v in lang.items():
                    if re.search(r'(?i)lord.?pumpkin|soulbane|pumpkin_staff|summon', k):
                        p(f"lang {k} = {v[:200]}")
            except Exception as e:
                p(f"lang: {e}")
except Exception:
    p(traceback.format_exc())
os.makedirs('probe-out', exist_ok=True)
open('probe-out/bosses.txt', 'w').write('\n'.join(out)); open('report.txt', 'w').write('done')
