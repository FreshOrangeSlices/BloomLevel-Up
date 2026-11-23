import os, re, sys, json, zipfile, urllib.request, urllib.parse, traceback
out = []; p = out.append
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=120).read()
PAT = re.compile(r'blood.?moon|bloodmoon|swift.?strike|flurry|cleave|heavy.?slam|rusted|bone_dagger|vial_of_blood|orchid', re.I)
try:
    jars = json.load(open('probe/jars.json'))
    for name, url in jars.items():
        try:
            path = 'j_' + re.sub(r'\W', '_', name) + '.jar'
            open(path, 'wb').write(get(url.replace(' ', '%20').replace('+', '%2B') if 'forgecdn' in url else url))
            z = zipfile.ZipFile(path)
        except Exception as e:
            p(f"#### {name}: download failed {e}"); continue
        hits = []
        for n in z.namelist():
            low = n.lower()
            if low.endswith('.class'):
                data = z.read(n)
                found = sorted(set(m.group(0).decode('latin1') for m in re.finditer(rb'(?i)blood.?moon|swift.?strike|flurry|cleave|heavy.?slam|bone_dagger|vial_of_blood', data)))
                if found: hits.append(f"class {n}: {found}")
            elif low.endswith(('.json', '.toml', '.lang', '.txt', '.mcmeta', '.cfg')):
                if PAT.search(n):
                    hits.append(f"file {n}")
                if '/lang/en_us' in low or low.endswith('.toml') or ('recipes/' in low or 'loot_tables/' in low or 'loot_table' in low):
                    try: text = z.read(n).decode('utf8', 'replace')
                    except Exception: continue
                    for line in text.splitlines():
                        if PAT.search(line):
                            hits.append(f"{n}: {line.strip()[:300]}")
        p(f"#### {name} ({len(hits)} hits)")
        for h in hits[:45]: p(h)
except Exception:
    p(traceback.format_exc())
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)))
